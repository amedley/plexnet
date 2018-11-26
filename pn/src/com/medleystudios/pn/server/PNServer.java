package com.medleystudios.pn.server;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.conn.PNConnection;
import com.medleystudios.pn.conn.packets.PNPingPacket;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.IOException;

import java.net.ServerSocket;
import java.util.Date;

public class PNServer implements Runnable, PNErrorStorable {

   private final Object closeLock = new Object();

   private final PNServerRunArguments runArguments;

   private ServerSocket serverSocket;
   private PNClientAcceptHandler clientAcceptHandler;
   private ServerState state = ServerState.INIT;

   private final int port;
   private final String host;
   private final int maxConnections;

   private PNErrorStorage errorStorage = new PNErrorStorage();

   public enum ServerState {
      INIT,                      // The server has yet started establishing
      ESTABLISHING_HOST,         // In the process of establishing a stable host.
      FAILED_TO_HOST,            // Server failed during ESTABLISHING_HOST.
      HOSTING,                   // The host is stable and accepting connections
      HOST_ENDED,                // Means that the host has ended and is no longer open, bound, or accepting connections
   }

   private PNServer(PNServerRunArguments runArguments) {
      this.runArguments = runArguments;

      PN.info(this, "" + runArguments);

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
      PN.info(this, "Hosting server on port " + this.port);

      this.setState(ServerState.ESTABLISHING_HOST);
      PNConnection.host(this.host, this.port)
         .thenAccept((serverSocketResolver) -> {
            synchronized (this) {
               if (serverSocketResolver.didSucceed()) {
                  this.serverSocket = serverSocketResolver.getServerSocket();
                  this.setState(ServerState.HOSTING);
               }
               else {
                  this.getErrorStorage().add(serverSocketResolver.getErrorStorage().getTopError());
                  this.setState(ServerState.FAILED_TO_HOST);
               }

               this.clientAcceptHandler = new PNClientAcceptHandler(this, (PNConnection connection) -> {
                  synchronized (this) {
                     PN.info(this, "Got a connection! " + connection);

                     PN.sleep(2000);

                     PN.info(this, "Sending packet");
                     PNPingPacket ping = new PNPingPacket();
                     for (int i = 0; i < 50; i++) {
                        PN.sleep(10);
                        ping.dates.add(new Date());
                     }
                     try {
                        ping.write(connection.getWriter());
                     }
                     catch (IOException e) {
                        PN.storeAndLogError(e, this, "Error writing packet to connection " + connection);
                        connection.close();
                        return;
                     }


                     for (int i = 0; i < 100; i++) {
                        PN.sleep(10);
                        // TODO
                        // Remember to run 'connection.process' in the server loop when we start using that
                        connection.process();
                     }



                     PN.sleep(2000);

                     PN.info(this, "Testing orderly close: " + connection);
                     connection.close();

                     PN.info(this, "Tests finished!");
                  }
               }, () -> {
                  PN.info("Accept handler FAILED!");
               });
               new Thread(this.clientAcceptHandler).start();
            }
         });
   }

   private void loop() {
      long i = 0;
      while (true) {
         PN.sleep(2);

         synchronized (this) {
            if (this.isInitializing()) continue;
            if (this.isEstablishingHost()) continue;
            if (!this.isHosting()) {
               PN.info(this, "Server no longer hosting! Exiting...");
               break;
            }
            if (!this.checkHost()) {
               PN.info(this, "Server host checks failed! Exiting...");
               break;
            }

            i++;
            if (i % 1000 == 0) {
               PN.info("SERVER: " + this.serverSocket.toString());
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
         PN.info(this, "Server host ended!");
      }

      return true;
   }

   public synchronized void close() {
      synchronized (closeLock) {
         PN.info(this, "Closing server " + serverSocket);
         try {
            this.serverSocket.close();
         }
         catch (IOException e) {
            PN.error(e, this, "Failed to close server socket!");
         }
         this.serverSocket = null;
      }
   }

   @Override
   public PNErrorStorage getErrorStorage() {
      return this.errorStorage;
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
