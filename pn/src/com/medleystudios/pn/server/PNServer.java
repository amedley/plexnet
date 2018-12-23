package com.medleystudios.pn.server;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.PNOptions;
import com.medleystudios.pn.client.PNClient;
import com.medleystudios.pn.client.PNServerClient;
import com.medleystudios.pn.conn.PNConnection;
import com.medleystudios.pn.policy.server.PNAcceptancePolicy;
import com.medleystudios.pn.policy.PNPolicyFactory;
import com.medleystudios.pn.session.PNServerClientSession;
import com.medleystudios.pn.session.PNSessionFactory;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.Closeable;
import java.io.IOException;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class PNServer<S extends PNServerClientSession> implements Closeable, Runnable, PNErrorStorable {

   private final Object closeLock = new Object();

   private static PNOptions defaultOptions;
   static {
      setDefaultOptions(new String[] {
         "-host", "0.0.0.0",
         "-port", "3000",
      });
   }

   private final PNOptions options;

   private boolean closed = false;

   private ServerSocket serverSocket;
   private PNClientAcceptHandler clientAcceptHandler;
   private ServerState state = ServerState.INIT;

   private final int port;
   private final String host;

   private final Map<PNClient.ClientId, PNServerClient> clients;

   private PNErrorStorage errorStorage = new PNErrorStorage();

   private final PNSessionFactory sessionFactory;
   private final PNPolicyFactory acceptPolicyFactory;

   public enum ServerState {
      INIT,                      // The server has yet started establishing
      ESTABLISHING_HOST,         // In the process of establishing a stable host.
      FAILED_TO_HOST,            // Server failed during ESTABLISHING_HOST.
      HOSTING,                   // The host is stable and accepting connections
      HOST_ENDED,                // Means that the host has ended and is no longer open, bound, or accepting connections
   }

   public PNServer(PNOptions runOptions, PNSessionFactory sessionFactory, PNPolicyFactory acceptPolicyFactory) {
      this.options = runOptions;
      this.sessionFactory = sessionFactory;
      this.acceptPolicyFactory = acceptPolicyFactory;

      PN.info(this, "" + runOptions);

      // Process run arguments
      this.host = this.options.getString("host");
      this.port = this.options.getInteger("port");
      this.clients = new HashMap<>();

      new Thread(this).start();
   }

   public PNServer(String[] args, PNSessionFactory sessionFactory, PNPolicyFactory acceptPolicyFactory) {
      this(defaultOptions.clone().parse(args), sessionFactory, acceptPolicyFactory);
   }

   public static void setDefaultOptions(PNOptions options) {
      if (options == null) {
         throw new IllegalArgumentException("options null");
      }
      PNServer.defaultOptions = options;
   }

   public static void setDefaultOptions(String[] args) {
      if (args == null) {
         throw new IllegalArgumentException("args nul");
      }
      setDefaultOptions(new PNOptions().parse(args));
   }

   public static PNOptions getDefaultOptions() {
      return PNServer.defaultOptions;
   }

   @Override
   public void run() {
      start();
      loop();
   }

   protected synchronized void clientAccepted(PNServerClient client) {
      PN.info(this, "Client connected " + client);
      this.clients.put(client.getId(), client);
   }

   protected synchronized void acceptFailed() {
      PN.info("Failed to accept a connection");
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

               this.clientAcceptHandler = new PNClientAcceptHandler(this, this::clientAccepted, this::acceptFailed,
                  sessionFactory, acceptPolicyFactory);
               new Thread(this.clientAcceptHandler).start();
            }
         });
   }

   private void loop() {
      while (true) {
         PN.sleep(2);

         synchronized (this) {
            if (!tick()) {
               break;
            }
         }
      }
   }

   protected boolean tick() {
      if (this.isInitializing()) return true;
      if (this.isEstablishingHost()) return true;
      if (!this.isHosting()) {
         PN.info(this, "Server no longer hosting!");
         return false;
      }
      if (!this.checkHost()) {
         PN.info(this, "Server host checks failed!");
         return false;
      }

      Iterator<PNClient.ClientId> idsIt = clients.keySet().iterator();
      while (idsIt.hasNext()) {
         PNClient.ClientId id = idsIt.next();
         PNServerClient client = getClient(id);
         if (!client.isConnected()) {
            // Remove the client if they are disconnected so that the server stops managing the associated
            // PNServerClientSession
            removeClient(client.getId());

            if (client.didDisconnect()) {
               logClientDisconnected(client, client.didDisconnectSelf());
            }
            else {
               logClientFailedUnknown(client);
            }
         }
      }
      return true;
   }

   protected synchronized void logClientDisconnected(PNServerClient client, boolean disconnectedSelf) {
      synchronized (client) {
         if (disconnectedSelf == true) {
            PN.info(this, "Successfully disconnected client connection " + client);
         }
         else {
            PN.info(this, "Lost client connection " + client);
         }
      }
   }

   protected synchronized void logClientFailedUnknown(PNServerClient client) {
      synchronized (client) {
         PN.info(this, "Client failed for unknown cause " + client);
      }
   }

   private synchronized PNServerClient removeClient(PNClient.ClientId id) {
      return this.clients.remove(id);
   }

   public synchronized PNServerClient getClient(PNClient.ClientId id) {
      return this.clients.get(id);
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

   public synchronized void close() {
      synchronized (closeLock) {
         if (isClosed()) return;
         this.closed = true;

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

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.closed;
      }
   }

   public synchronized boolean isBound() {
      return this.serverSocket.isBound();
   }

   public synchronized ServerSocket getServerSocket() {
      return this.serverSocket;
   }

   public static void main(String[] args) {
      new PNServer(args, PNServerClientSession::new, PNAcceptancePolicy::new);
   }

}
