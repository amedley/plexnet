package com.medleystudios.pn.server;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.conn.PNConnection;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.util.PNUtil;

import java.io.IOException;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PNServer implements Runnable {

   private final Object closeLock = new Object();

   private final PNServerRunArguments runArguments;

   private ServerSocket serverSocket;
   private PNClientAcceptHandler clientAcceptHandler;
   private ServerState state = ServerState.INIT;

   private final int port;
   private final String host;
   private final int maxConnections;

   private String errorMessage = null;

   public enum ServerState {
      INIT,                      // The server has yet started establishing
      ESTABLISHING_HOST,         // In the process of establishing a stable host.
      FAILED_TO_HOST,            // Server failed during ESTABLISHING_HOST.
      HOSTING,                   // The host is stable and accepting connections
      HOST_ENDED,                // Means that the host has ended and is no longer open, bound, or accepting connections
   }

   private PNServer(PNServerRunArguments runArguments) {
      this.runArguments = runArguments;

      PN.log(this, "" + runArguments);

      // Process run arguments
      this.port = this.runArguments.getPort();
      this.host = this.runArguments.getHost();
      this.maxConnections = this.runArguments.getMaxConnections();
   }

   public PNServer(String[] args) {
      this((PNServerRunArguments)(new PNServerRunArguments().read(args)));
   }

   @Override
   public void run() {
      start();
      loop();
   }

   private synchronized void start() {
      PN.log(this, "Hosting server on port " + this.port);

      this.setState(ServerState.ESTABLISHING_HOST);
      PNConnection.host(this.host, this.port)
         .thenAccept((serverSocketResolver) -> {
            synchronized (this) {
               if (serverSocketResolver.didSucceed()) {
                  this.serverSocket = serverSocketResolver.getServerSocket();
                  this.setState(ServerState.HOSTING);
               }
               else {
                  // Failed to host
                  this.errorMessage = serverSocketResolver.getErrorMessage();
                  this.setState(ServerState.FAILED_TO_HOST);
               }

               this.clientAcceptHandler = new PNClientAcceptHandler(this, (PNConnection connection) -> {
                  PN.log(this, "Got a connection! " + connection);

                  try { Thread.sleep(2000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  PN.log(this, "Testing sending bytes: " + connection);

                  connection.getWriter().write(new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 20, 21, 22, 23, 24, 25, 26, 27, 28, 29 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 30, 31, 32, 33, 34, 35, 36, 37, 38, 39 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 40, 41, 42, 43, 44, 45, 46, 47, 48, 49 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 50, 51, 52, 53, 54, 55, 56, 57, 58, 59 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 60, 61, 62, 63, 64, 65, 66, 67, 68, 69 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 70, 71, 72, 73, 74, 75, 76, 77, 78, 79 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 80, 81, 82, 83, 84, 85, 86, 87, 88, 89 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  connection.getWriter().write(new byte[] { 90, 91, 92, 93, 94, 95, 96, 97, 98, 99 });
                  try { Thread.sleep(1000); }
                  catch (InterruptedException e) { e.printStackTrace(); }

                  try {
                     Thread.sleep(2000);
                  }
                  catch (InterruptedException e) {
                     e.printStackTrace();
                  }

                  PN.log(this, "Testing orderly close: " + connection);
                  connection.close();

                  PN.log(this, "Tests finished!");

               }, () -> {
                  PN.log("Accept handler FAILED!");
               });
               new Thread(this.clientAcceptHandler).start();
            }
         });
   }

   private void loop() {
      long i = 0;
      while (true) {
         try {
            Thread.sleep(2);
         }
         catch (InterruptedException e) {
            PN.fatalError(e, this, "Failed to sleep thread");
         }

         synchronized (this) {
            if (this.isInitializing()) continue;
            if (this.isEstablishingHost()) continue;
            if (!this.isHosting()) {
               PN.log(this, "Server no longer hosting! Exiting...");
               break;
            }
            if (!this.checkHost()) {
               PN.log(this, "Server host checks failed! Exiting...");
               break;
            }

            i++;
            if (i % 1000 == 0) {
               PN.log("SERVER: " + this.serverSocket.toString());
            }
         }
      }
   }

   private synchronized boolean checkHost() {
      // If we've already set the server to NOT hosting, then nothing else needs to happen here
      if (this.serverSocket == null || !isHosting()) {
         return false;
      }

      if (this.serverSocket.isClosed() || !this.serverSocket.isBound()) {
         setState(ServerState.HOST_ENDED);
         PN.log(this, "Server host ended!");
      }

      return true;
   }

   public synchronized void close() {
      synchronized (closeLock) {
         PN.log(this, "Closing server " + serverSocket);
         try {
            this.serverSocket.close();
         }
         catch (IOException e) {
            PN.error(e, this, "Failed to close server socket!");
         }
         this.serverSocket = null;
      }
   }

   public synchronized boolean isInitializing() {
      return this.state == ServerState.INIT;
   }

   public synchronized boolean isEstablishingHost() {
      return this.state == ServerState.ESTABLISHING_HOST;
   }

   public synchronized boolean didFailToHost() {
      return this.state == ServerState.FAILED_TO_HOST;
   }

   public synchronized boolean isHosting() {
      return this.state == ServerState.HOSTING;
   }

   public synchronized boolean didHostEnd() {
      return this.state == ServerState.HOST_ENDED;
   }

   private synchronized void setState(ServerState state) {
      ServerState prev = this.state;
      ServerState next = state;

      if (next == ServerState.INIT) {
         throw new IllegalStateException();
      }
      else if (next == ServerState.ESTABLISHING_HOST) {
         if (prev != ServerState.INIT) {
            throw new IllegalStateException();
         }
      }
      else if (next == ServerState.FAILED_TO_HOST) {
         if (prev != ServerState.ESTABLISHING_HOST) {
            throw new IllegalStateException();
         }
      }
      else if (next == ServerState.HOSTING) {
         if (prev != ServerState.ESTABLISHING_HOST) {
            throw new IllegalStateException();
         }
      }
      else if (next == ServerState.HOST_ENDED) {
         if (prev != ServerState.HOSTING) {
            throw new IllegalStateException();
         }
      }

      this.state = next;
   }

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.serverSocket.isClosed();
      }
   }

   public synchronized boolean isBound() {
      return this.serverSocket.isBound();
   }

   public synchronized ServerSocket getServerSocket() {
      return this.serverSocket;
   }

   public static void main(String[] args) {
      PNServer server;
      if (args.length > 0) {
         server = new PNServer(args);
      }
      else {
         server = new PNServer(new String[] {
            "0.0.0.0",
            "3000",
            "3",
         });
      }
      new Thread(server).start();
   }

}
