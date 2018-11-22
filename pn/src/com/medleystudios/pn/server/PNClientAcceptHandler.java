package com.medleystudios.pn.server;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.conn.PNConnection;
import com.medleystudios.pn.util.PNUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class PNClientAcceptHandler implements Runnable {

   private PNServer server;
   private ServerSocket serverSocket;
   private AcceptHandler onAccept;
   private Runnable onFailedAccept;

   public PNClientAcceptHandler(PNServer server, AcceptHandler onAccept, Runnable onFailedAccept) {
      super();
      this.server = server;
      this.serverSocket = this.server.getServerSocket();
      this.onAccept = onAccept;
      this.onFailedAccept = onFailedAccept;
   }

   public synchronized boolean mayAccept() {
      return this.server != null && this.server.isBound() && !server.isClosed();
   }

   @Override
   public void run() {
      while (mayAccept()) {
         final Socket socket;

         // Accept
         try {
            PN.log(this, "Accepting next client...");
            socket = this.serverSocket.accept();
         }
         catch (SocketException e) {
            PN.error(e, this, "Failed to accept client socket!");
            this.failedAccept();
            break;
         }
         catch (IOException e) {
            PN.error(e, this, "Failed to accept client socket!");
            this.failedAccept();
            break;
         }

         // Configure
         try {
            socket.setTcpNoDelay(true);
            socket.setReuseAddress(true);
         }
         catch (SocketException e) {
            try {
               socket.close();
            }
            catch (IOException closeE) {
               PN.error(closeE, this, "Close failed on newly connected socket which failed configuration");
            }
            PN.fatalError(e, this, "Failed to configure client socket!");
            break; // Unnecessary (fatal error)
         }

         // Get PNConnection
         PNConnection.get(socket)
            .thenAccept((connection) -> {
               this.accept(connection);
            })
            .exceptionally((t) -> {
               // This may only happen with one client, no need to call "failedAccept"
               PN.error(t, this, "Failed to create a PNConnection from newly accepted client socket!");
               try {
                  socket.close();
               }
               catch (IOException e) {
                  PN.error(e, this, "Close failed on newly connected socket which failed getting PNConnection");
               }
               return null;
            });
      }

      cleanUp();
   }

   private synchronized void cleanUp() {
      PN.log(this, "No longer accepting connections");

      this.server = null;
      this.serverSocket = null;
      this.onAccept = null;
      this.onFailedAccept = null;
   }

   private synchronized void accept(PNConnection connection) {
      if (this.onAccept != null) {
         this.onAccept.run(connection);
      }
   }

   private synchronized void failedAccept() {
      if (this.onFailedAccept != null) {
         this.onFailedAccept.run();
      }
   }

   public interface AcceptHandler {
      void run(PNConnection connection);
   }
}
