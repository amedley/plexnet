package com.medleystudios.pn.server;

import com.medleystudios.pn.PNUtil;

import java.io.IOException;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class PNServer {

   private final PNServerRunArguments runArguments;

   private ServerSocket serverSocket;

   private PNClientAcceptHandler clientAcceptHandler;

   private List<PNServerListener> serverListeners;

   private int port;

   private PNServer(PNServerRunArguments runArguments) {
      this.runArguments = runArguments;

      PNUtil.log(this, "" + runArguments);

      // Process run arguments
      this.port = this.runArguments.getPort();



      PNUtil.log(this, "Initializing server on port " + this.port);

      this.serverListeners = new ArrayList<>();

      try {
         this.serverSocket = new ServerSocket(this.port);
      }
      catch (IOException e) {
         PNUtil.fatalError(e, this, "Failed to initialize server socket!");
      }

      this.clientAcceptHandler = new PNClientAcceptHandler(this.serverSocket);
      this.registerListener(this.clientAcceptHandler);
      new Thread(this.clientAcceptHandler).start();
   }

   public PNServer(String[] args) {
      this((PNServerRunArguments)(new PNServerRunArguments().read(args)));
   }

   public void registerListener(PNServerListener listener) {
      // No duplicates
      for (int i = 0; i < this.serverListeners.size(); i++) {
         if (this.serverListeners.get(i) == listener) {
            throw new IllegalArgumentException("Failed to register server listener: " + listener.getClass().getName() + " " + listener);
         }
      }

      this.serverListeners.add(listener);
      listener.onRegisterListener(this);
   }

   public void unregisterListener(PNServerListener listener) {
      this.serverListeners.remove(listener);
      listener.onUnregisterListener(this);
   }

   public void close() {
      PNUtil.log(this, "Closing server " + serverSocket);

      try {
         this.serverSocket.close();
      }
      catch (IOException e) {
         PNUtil.error(e, this, "Failed to close server socket!");
      }

      // Call close on all server listeners
      for (int i = 0; i < this.serverListeners.size(); i++) {
         this.serverListeners.get(i).onClose(this);
      }
   }

   public ServerSocket getServerSocket() {
      return this.serverSocket;
   }

   public int getPort() {
      return this.port;
   }

   public static void main(String[] args) {
      if (args.length > 0) {
         new PNServer(args);
      }
      else {
         new PNServer(new String[] {
            "3000",
         });
      }
   }

}
