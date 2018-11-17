package com.medleystudios.pn.server;

import com.medleystudios.pn.PNUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class PNClientAcceptHandler implements Runnable, PNServerListener {

   private ServerSocket serverSocket;

   public PNClientAcceptHandler(ServerSocket serverSocket) {
      super();
      this.serverSocket = serverSocket;
   }

   public boolean mayAccept() {
      return !this.serverSocket.isClosed() && this.serverSocket.isBound();
   }

   @Override
   public void run() {

      while (mayAccept()) {
         try {
            PNUtil.log(this, "Accepting next client...");
            Socket socket = serverSocket.accept();
            PNUtil.log(this, "Got a connection! " + socket);
         }
         catch (IOException e) {
            PNUtil.error(e, this, "Failed to accept client!");
         }
      }

      PNUtil.log(this, "Exiting now");

   }

   @Override
   public void onRegisterListener(PNServer server) {
      PNUtil.log(this, "Registered to server on port " + server.getPort());
   }
   @Override
   public void onUnregisterListener(PNServer server) {
      PNUtil.log(this, "Unregistered from server on port " + server.getPort());
   }
   @Override
   public void onClose(PNServer server) {
      PNUtil.log(this, "Closed by server on port " + server.getPort());
   }
}
