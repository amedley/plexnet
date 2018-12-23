package com.medleystudios.pn.server;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.client.PNServerClient;
import com.medleystudios.pn.conn.PNConnection;
import com.medleystudios.pn.policy.PNPolicy;
import com.medleystudios.pn.policy.PNPolicyFactory;
import com.medleystudios.pn.policy.server.PNAcceptancePolicy;
import com.medleystudios.pn.session.PNSessionFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import static com.ea.async.Async.await;

public class PNClientAcceptHandler implements Runnable {

   private PNServer server;
   private ServerSocket serverSocket;
   private final AcceptHandler onAccept;
   private final Runnable onFailedAccept;
   private PNSessionFactory sessionFactory;
   private PNPolicyFactory acceptPolicyFactory;

   public PNClientAcceptHandler(PNServer server, AcceptHandler onAccept, Runnable onFailedAccept,
                                PNSessionFactory sessionFactory, PNPolicyFactory acceptPolicyFactory) {
      super();
      this.server = server;
      this.serverSocket = this.server.getServerSocket();
      this.onAccept = onAccept;
      this.onFailedAccept = onFailedAccept;
      this.sessionFactory = sessionFactory;
      this.acceptPolicyFactory = acceptPolicyFactory;
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
            PN.info(this, "Accepting next client...");
            socket = this.serverSocket.accept();
            socket.setTcpNoDelay(true);
            socket.setReuseAddress(true);
         }
         catch (IOException e) {
            if (server.isClosed()) {
               PN.info(this, "Client accept handler exiting...");
            }
            else {
               PN.error(e, this, "Failed to accept client socket!");
               this.failedAccept();
            }
            break;
         }

         PNConnection.get(socket)
            .thenAccept((connection) -> {
               // Got a connection object!
               PNServerClient client = new PNServerClient(connection, this.sessionFactory);
               PN.info(this, "Client connected, running policy enforcement\n\t" + client);

               // Enforce the accept policy on the client
               PNPolicy.PolicyResolver resolver;
               try {
                  PNAcceptancePolicy acceptPolicy = this.acceptPolicyFactory.create();
                  acceptPolicy.setClient(client);
                  resolver = await(acceptPolicy.enforce());
               }
               catch (Exception e) {
                  // Failed during enforcement
                  PN.error(e, this, "There was a problem running policy enforcement on newly connected client. " +
                     "Closing connection to\n\t" + client);

                  try {
                     // Try closing the connection
                     connection.close();
                  }
                  catch (Exception e2) {
                     // Log connection-close fail reason
                     PN.error(e2, this, "Failed to 'close' on connection of newly connected client which caused a " +
                        "problem running policy enforcement\n\t" + client);
                  }

                  // return so we don't misuse a null 'resolver'
                  return;
               }

               // At this point the resolver definitely exists and either failed or succeeded in policy enforcement
               if (!resolver.didSucceed()) {
                  // The client did not succeed policy enforcement
                  String failReason = resolver.getErrorStorage().getTopError().getMessage();
                  PN.info(this, client + " failed during accept policy enforcement. Sending " +
                     "connection rejection packet with reason: " + failReason);

                  try {
                     // Try sending a packet to them telling them why their connection was rejected
                     client.getConnection().sendPacket(new PNRejectClientPacket(failReason));
                  }
                  catch (Exception e) {
                     // Failed to send the connection rejection packet. No worries.
                     PN.error(e, this, "Failed to send connection rejection packet to newly connected client which" +
                        "failed during policy enforcement.\n\t" + client + " failed -> reason: " + failReason);

                     // DO NOT return here we still have to close disconnect the client
                  }

                  PN.info(this, "Disconnecting newly connected client which failed during policy " +
                     "enforcement.\n\t" + client);

                  // Disconnect the client. client's thread will run execution until the next tick when it sees
                  // that it has disconnected
                  client.disconnect();
               }
               else {
                  PN.info(this, "Policy enforcement successful on newly connected client. Sending authenticated " +
                     "session id to client.\n\t" + client);

                  try {
                     // Try sending the session ID to the client
                     client.getConnection().sendPacket(
                        new PNAcceptClientPacket(client.getSession().getId().getData()));
                  }
                  catch (Exception e) {
                     // Failed to send the packet ID to the client
                     PN.error(e, this, "Failed to send connection acceptance packet to newly connected client.\n\t"
                        + client);

                     // Disconnect the client. client's thread will run execution until the next tick when it sees
                     // that it has disconnected
                     client.disconnect();
                     return;
                  }

                  PN.info(this, "Sent authenticated session ID to client. Client acceptance complete!\n\t"
                     + client);
                  this.accept(client);
               }
            })
            .exceptionally((t) -> {
               // Failed getting a PNConnection object from the socket
               PN.error(t, this, "Failed to create a PNConnection from newly connected socket\n\t" + socket);
               try {
                  socket.close();
               }
               catch (IOException e) {
                  PN.error(e, this, "Failed to 'close' on newly connected socket which failed getting PNConnection\n\t"
                     + socket);
               }
               this.failedAccept();
               return null;
            });
      }

      cleanUp();
   }

   private synchronized void cleanUp() {
      PN.info(this, "No longer accepting connections");

      this.server = null;
      this.serverSocket = null;
      this.sessionFactory = null;
   }

   private synchronized void accept(PNServerClient client) {
      if (this.onAccept != null) {
         this.onAccept.run(client);
      }
   }

   private synchronized void failedAccept() {
      if (this.onFailedAccept != null) {
         this.onFailedAccept.run();
      }
   }

   public interface AcceptHandler {
      void run(PNServerClient client);
   }
}
