package com.medleystudios.pn.client;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.conn.PNConnection;
import com.medleystudios.pn.io.PNAsyncBufferedOutputStream;
import com.medleystudios.pn.io.PNAsyncBufferedInputStream;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.net.Socket;

import static com.ea.async.Async.await;

public class PNClient implements Runnable, PNErrorStorable {

   public enum ClientState {
      INIT,                      // The connection has yet started establishing
      ESTABLISHING_CONNECTION,   // In the process of establishing a stable connection.
      FAILED_TO_CONNECT,         // Connection failed during ESTABLISHING_CONNECTION.
      CONNECTED,                 // The connection is stable and is handling IO
      DISCONNECTED_ORDERLY,      // Means that the connection has ended in an orderly manner
      DISCONNECTED_ABORTIVE,     // The connection has ended abortive
   }

   private final PNClientRunArguments runArguments;

   private PNConnection connection = null;
   private ClientState state = ClientState.INIT;

   private final String host;
   private final int port;

   private PNErrorStorage errorStorage = new PNErrorStorage();

   private PNClient(PNClientRunArguments runArguments) {
      this.runArguments = runArguments;

      PN.info(this, "" + runArguments);

      // Process run arguments
      this.host = this.runArguments.getHost();
      this.port = this.runArguments.getPort();
   }

   public PNClient(String[] args) {
      this((PNClientRunArguments)(new PNClientRunArguments().read(args)));
   }

   @Override
   public void run() {
      start();
      loop();
   }

   private synchronized void start() {
      PN.info(this, "Establishing client connection with endpoint " + this.host + ":" + this.port);
      this.setState(ClientState.ESTABLISHING_CONNECTION);
      PNConnection.connect(this.host, this.port)
         .thenAccept((socketResolver) -> {
            synchronized (this) {
               if (socketResolver.didSucceed()) {
                  Socket clientSocket = socketResolver.getSocket();
                  this.connection = await(PNConnection.get(clientSocket));
                  PN.info("Connection established: " + this.connection);
                  this.setState(ClientState.CONNECTED);
               }
               else {
                  this.getErrorStorage().add(socketResolver.getErrorStorage().getTopError());
                  PN.info("Failed to establish connection: " + this.getErrorStorage().getTopError().getMessage());
                  this.setState(ClientState.FAILED_TO_CONNECT);
               }
            }
         });

   }

   private void loop() {
      long i = 0;
      while (true) {
         PN.sleep(2);

         synchronized (this) {
            if (isInitializing()) continue;
            if (isEstablishingConnection()) continue;
            if (!isConnected()) {
               PN.info("Client no longer connected! Exiting...");
               break;
            }
            if (!checkConnection()) {
               PN.info("Client connection checks failed! Exiting...");
               break;
            }

            connection.process();

            i++;
            if (i % 1000 == 0) {
               PN.info("CLIENT: " + this.connection);
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
         if (this.connection.getInputStream().didReachEnd()) {
            setState(ClientState.DISCONNECTED_ORDERLY);
            PN.info(this, "Client disconnected ORDERLY");
         }
         else {
            setState(ClientState.DISCONNECTED_ABORTIVE);
            PN.info(this, "Client disconnected ABORTIVE");
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

   public synchronized boolean didDisconnectOrderly() {
      return this.state == ClientState.DISCONNECTED_ORDERLY;
   }

   public synchronized boolean didDisconnectAbortive() {
      return this.state == ClientState.DISCONNECTED_ABORTIVE;
   }

   public synchronized boolean didDisconnect() {
      return this.state == ClientState.DISCONNECTED_ABORTIVE || this.state == ClientState.DISCONNECTED_ORDERLY;
   }

   private synchronized void setState(ClientState state) {
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
         if (prev != ClientState.ESTABLISHING_CONNECTION) {
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

   public static void main(String[] args) {
      PNClient client;
      if (args.length > 0) {
         client = new PNClient(args);
      }
      else {
         client = new PNClient(new String[] {
            "localhost",
            "3000",
         });
      }
      new Thread(client).start();
   }
}
