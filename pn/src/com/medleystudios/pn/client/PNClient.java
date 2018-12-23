package com.medleystudios.pn.client;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.PNOptions;
import com.medleystudios.pn.conn.PNConnection;
import com.medleystudios.pn.conn.packets.PNPacketReceiver;
import com.medleystudios.pn.io.PNAsyncBufferedInputStream;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.session.PNClientSession;
import com.medleystudios.pn.session.PNSession;
import com.medleystudios.pn.session.PNSessionFactory;
import com.medleystudios.pn.util.PNLogger;
import com.medleystudios.pn.util.error.PNError;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.IOException;
import java.net.Socket;
import java.util.Objects;

import static com.ea.async.Async.await;

public class PNClient<S extends PNSession> implements Runnable, PNErrorStorable, PNLogger, PNPacketReceiver {

   public enum ClientState {
      INIT,                      // The connection has yet started establishing
      ESTABLISHING_CONNECTION,   // In the process of establishing a stable connection.
      FAILED_TO_CONNECT,         // Connection failed during ESTABLISHING_CONNECTION.
      CONNECTED,                 // The connection is stable and is handling IO
      DISCONNECTED_SELF,         // Means that this client disconnected itself
      DISCONNECTED_ORDERLY,      // Means that the connection has ended in an orderly manner
      DISCONNECTED_ABORTIVE,     // The connection has ended abortive
   }

   private static PNOptions defaultOptions;
   static {
      setDefaultOptions(new String[] {
         "-host", "localhost",
         "-port", "3000",
      });
   }

   private final PNOptions options;

   private final ClientId id;
   private PNConnection connection = null;
   private ClientState state = ClientState.INIT;

   private final String host;
   private final int port;

   private PNErrorStorage errorStorage = new PNErrorStorage();

   private Object sessionLock = new Object();
   private S session;
   private final PNSessionFactory sessionFactory;

   protected PNClient(PNOptions options, PNSessionFactory sessionFactory) {
      if (options == null) throw new IllegalArgumentException("options");
      if (sessionFactory == null) throw new IllegalArgumentException("sessionFactory");

      this.id = ClientId.next();
      this.options = options;
      this.sessionFactory = sessionFactory;
      this.session = null;

      PN.info(this, "" + options);

      this.host = this.options.getString("host");
      this.port = this.options.getInteger("port");

      new Thread(this).start();
   }

   // This is purely overridden in PNServerClient, which is why it is only package-visible.
   PNClient(PNConnection connection, PNSessionFactory sessionFactory) {
      if (connection == null) throw new IllegalArgumentException("connection");
      if (sessionFactory == null) throw new IllegalArgumentException("sessionFactory");

      this.id = ClientId.next();
      this.options = null;
      this.sessionFactory = sessionFactory;
      this.session = null;
      connected(connection);

      PN.info(this, "Client initializing with connection " + connection);

      this.host = connection.getHost();
      this.port = connection.getPort();

      new Thread(this).start();
   }

   private PNClient(String[] args, PNSessionFactory sessionFactory) {
      this(defaultOptions.clone().parse(args), sessionFactory);
   }

   public void startSession(PNSession.SessionId sessionId) {
      if (this.session != null) {
         throw new UnsupportedOperationException("Cannot call startSession more than once!");
      }
      this.session = (S)this.sessionFactory.create(sessionId);
      this.errorStorage.addChild(this.session.getErrorStorage());
   }

   public static void setDefaultOptions(PNOptions options) {
      if (options == null) {
         throw new IllegalArgumentException("options null");
      }
      defaultOptions = options;
   }

   public static void setDefaultOptions(String[] args) {
      if (args == null) {
         throw new IllegalArgumentException("args nul");
      }
      setDefaultOptions(new PNOptions().parse(args));
   }

   public static PNOptions getDefaultOptions() {
      return defaultOptions;
   }

   @Override
   public void run() {
      start();
      loop();
   }

   public S getSession() {
      return this.session;
   }

   private synchronized void connected(PNConnection connection) {
      this.setState(ClientState.CONNECTED);
      this.connection = connection;
      this.connection.setPacketReceiver(this::receivePacket);
      this.getErrorStorage().addChild(this.connection.getErrorStorage());
      PN.info("Connection established: " + this.connection);
   }

   private synchronized void start() {
      if (connection == null) {
         // The connection could be non-null if it runs the package-level constructor (which initializes with a
         // connection)
         PN.info(this, "Establishing client connection with endpoint " + this.host + ":" + this.port);
         this.setState(ClientState.ESTABLISHING_CONNECTION);
         PNConnection.connect(this.host, this.port)
            .thenAccept((socketResolver) -> {
               synchronized (this) {
                  if (socketResolver.didSucceed()) {
                     Socket clientSocket = socketResolver.getSocket();
                     this.connected(await(PNConnection.get(clientSocket)));
                  }
                  else {
                     this.getErrorStorage().add(socketResolver.getErrorStorage().getTopError());
                     PN.info("Failed to establish connection: " + this.getErrorStorage().getTopError().getMessage());
                     this.setState(ClientState.FAILED_TO_CONNECT);
                  }
               }
            });
      }
   }

   @Override
   public synchronized void receivePacket(short id, PNInputStreamReader reader) {
      synchronized (this.sessionLock) {
         if (this.session != null) {
            try {
               this.session.receivePacket(this, id, reader);
            }
            catch (IOException e) {
               PN.storeAndLogError(e, this.session, "Failed to read packet with ID: " + id + ". Disconnecting\n\t" +
                  this);
               this.disconnect();
            }
         }
      }
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

   @Override
   public String log(String message) {
      return "Client[" + id.getData() + "] " + message;
   }

   public boolean tick() {
      if (isInitializing()) return true;
      if (isEstablishingConnection()) return true;
      if (!isConnected()) {
         PN.info(this, "No longer connected!");
         return false;
      }
      if (!checkConnection()) {
         PN.info(this, "Connection checks failed!");
         if (this.didDisconnect()) {
            if (this.didDisconnectSelf() || this.didDisconnectOrderly()) {
               PN.info(this, "Detected safe disconnection state " + this.getState() + ". Running connection process " +
                  "before closing. Final input data will be read if any exists.");

               connection.process();
            }
         }
         return false;
      }

      process();
      return true;
   }

   protected synchronized void process() {
      connection.process();

      if (connection.isConnected()) {
         synchronized (this.sessionLock) {
            if (this.session != null) {
               this.session.callTick(this);
            }
         }
      }
   }

   private synchronized boolean checkConnection() {
      // If we've already set the client to NOT connected, then nothing else needs to happen here
      if (this.connection == null || !isConnected()) {
         return false;
      }

      if (this.connection.isClosed()) {
         PNAsyncBufferedInputStream in = this.connection.getInputStream();
         PNError error = getErrorStorage().getTopError();
         String info;
         if (in.didReachEnd()) {
            setState(ClientState.DISCONNECTED_ORDERLY);
            info = "Disconnected ORDERLY " + this;
         }
         else {
            setState(ClientState.DISCONNECTED_ABORTIVE);
            info = "Disconnected ABORTIVE " + this;
            if (error != null) {
               info += "\n\t" + error;
            }
            else {
               info += ", no error to report.";
            }
         }
         PN.info(this, info);

         synchronized (this.sessionLock) {
            if (this.session != null) {
               this.session.disconnected(this, false);
            }
         }

         return false;
      }
      return true;
   }

   public synchronized boolean isInitializing() {
      return this.state == ClientState.INIT;
   }

   public synchronized boolean isEstablishingConnection() {
      return this.state == ClientState.ESTABLISHING_CONNECTION;
   }

   public synchronized boolean didFailToConnect() {
      return this.state == ClientState.FAILED_TO_CONNECT;
   }

   public synchronized boolean isConnected() {
      return this.state == ClientState.CONNECTED;
   }

   public synchronized boolean didDisconnectSelf() {
      return this.state == ClientState.DISCONNECTED_SELF;
   }

   public synchronized boolean didDisconnectOrderly() {
      return this.state == ClientState.DISCONNECTED_ORDERLY;
   }

   public synchronized boolean didDisconnectAbortive() {
      return this.state == ClientState.DISCONNECTED_ABORTIVE;
   }

   public synchronized boolean didDisconnect() {
      return this.state == ClientState.DISCONNECTED_ABORTIVE
         || this.state == ClientState.DISCONNECTED_ORDERLY
         || this.state == ClientState.DISCONNECTED_SELF;
   }

   protected synchronized void setState(ClientState state) {
      ClientState prev = this.state;
      ClientState next = state;

      if (next == ClientState.INIT) {
         throw new IllegalStateException();
      }
      else if (next == ClientState.ESTABLISHING_CONNECTION) {
         if (prev != ClientState.INIT) {
            throw new IllegalStateException();
         }
      }
      else if (next == ClientState.FAILED_TO_CONNECT) {
         if (prev != ClientState.ESTABLISHING_CONNECTION) {
            throw new IllegalStateException();
         }
      }
      else if (next == ClientState.CONNECTED) {
         if (prev != ClientState.INIT && prev != ClientState.ESTABLISHING_CONNECTION) {
            throw new IllegalStateException();
         }
      }
      else if (next == ClientState.DISCONNECTED_SELF) {
         if (prev != ClientState.CONNECTED) {
            throw new IllegalStateException();
         }
      }
      else if (next == ClientState.DISCONNECTED_ORDERLY) {
         if (prev != ClientState.CONNECTED) {
            throw new IllegalStateException();
         }
      }
      else if (next == ClientState.DISCONNECTED_ABORTIVE) {
         if (prev != ClientState.CONNECTED) {
            throw new IllegalStateException();
         }
      }

      this.state = next;
   }

   public ClientId getId() {
      return this.id;
   }

   public synchronized void disconnect() {
      PN.info(this, "Disconnecting...");
      if (this.connection.disconnect()) {
         this.setState(ClientState.DISCONNECTED_SELF);
         PN.info(this, "Disconnect successful");
         synchronized (this.sessionLock) {
            if (this.session != null) {
               this.session.disconnected(this, true);
            }
         }
      }
   }

   @Override
   public PNErrorStorage getErrorStorage() {
      return this.errorStorage;
   }

   public synchronized ClientState getState() {
      return this.state;
   }

   public synchronized PNConnection getConnection() {
      return this.connection;
   }

   @Override
   public String toString() {
      return "PNClient[id=" + id.data + ", state=" + state + ", " + connection + "]";
   }

   public static class ClientId {
      private static long nextId = 0;

      private long data = 0;

      private ClientId() {
      }

      public long getData() {
         return this.data;
      }

      private synchronized static ClientId next() {
         ClientId id = new ClientId();
         nextId++;
         id.data = nextId;
         return id;
      }

      @Override
      public String toString() {
         return "ClientId[" + this.getData() + "]";
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         ClientId clientId = (ClientId)o;
         return data == clientId.data;
      }
      @Override
      public int hashCode() {
         return Objects.hash(data);
      }
   }

   public static void main(String[] args) {
      new PNClient(args, PNClientSession::new);
   }
}
