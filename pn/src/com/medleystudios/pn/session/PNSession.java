package com.medleystudios.pn.session;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.client.PNClient;
import com.medleystudios.pn.conn.packets.PNPacket;
import com.medleystudios.pn.conn.packets.PNPacketFactory;
import com.medleystudios.pn.conn.packets.PNPacketHandler;
import com.medleystudios.pn.conn.packets.PNPacketRegistration;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.policy.PNPolicyFactory;
import com.medleystudios.pn.policy.server.PNAcceptancePolicy;
import com.medleystudios.pn.server.PNServer;
import com.medleystudios.pn.util.PNLogger;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class PNSession<T extends PNClient> implements PNLogger, PNErrorStorable {

   /**
    * A unique ID (unique for the life of the running program) that identifies the PNSession's relationship to the
    * delegate {@link PNClient}. Underlying data is {@link SessionId#DATA_LENGTH} random bytes encoded in base64.
    * <p>
    * The {@link SessionId} is also used as cross-network identification of the client. The reason for this is so
    * that clients have the same ID client-side as they do server-side.
    * <p>
    * To manage what requirements {@link PNClient} must pass in order to successfully retrieve a {@link SessionId} from
    * the server, see {@link PNAcceptancePolicy} and its neighboring implementations. These implementations can all
    * be overridden via usage of {@link PNPolicyFactory} and {@link PNServer#acceptPolicyFactory}
    *
    * @see PNAcceptancePolicy
    * @see PNPolicyFactory
    * @see PNServer#acceptPolicyFactory
    */
   private final SessionId id;
   private PNErrorStorage errorStorage;
   private long ticks;
   private ArrayList<PNPacketRegistration> packetRegistrations;

   public PNSession(SessionId sessionId) {
      this.id = sessionId;
      this.errorStorage = new PNErrorStorage();
      this.ticks = 0;
      this.packetRegistrations = new ArrayList<>();
      this.registerPackets();
   }

   /**
    * The unique {@link SessionId} relating to a {@link PNClient} delegate. This ID is also responsible for stateless
    * identification which acts as validation for this client passing the server's client-acceptance process. For
    * more information on that, see {@link PNAcceptancePolicy} and its neighboring implementations, or read the
    * full documentation of this class's member {@link #id}.
    *
    * @return Returns the unique {@link SessionId}, which is also used for stateless identification involved in passing
    * the {@link PNAcceptancePolicy} during the server's client-acceptance process.
    * @see {@link PNAcceptancePolicy}
    * @see {@link #id}
    */
   public SessionId getId() {
      return this.id;
   }

   @Override
   public PNErrorStorage getErrorStorage() {
      return this.errorStorage;
   }

   /**
    * Called by the delegate (thread-safe) on each tick (roughly every ~2 ms)
    * <p>
    * When the client session 'ticks', that means it is running and should continue its application process.
    * <p>
    * When the client session permanently stops ticking, the delegate will call {@link #disconnected(PNClient, boolean)}
    *
    * @param delegate The delegate client object of parent class {@link PNClient}
    */
   protected abstract void tick(T delegate);

   /**
    * Responsible for calling the {@link #tick(PNClient)}} method. Increments {@link #ticks} when
    * {@link #tick(PNClient)} is finished.
    *
    * @param delegate The delegate to call {@link #tick(PNClient)} on.
    * @see #ticks()
    */
   public void callTick(T delegate) {
      tick(delegate);
      this.ticks++;
   }

   /**
    * @return Returns a long-int representing the countable number of times that the 'tick' method has been called by
    * the delegate.
    */
   public long ticks() {
      return this.ticks;
   }

   /**
    * Called by the delegate (thread-safe) when a packet is received.
    * <p>
    * The packet is sufficiently ready to be read, and its full length is present.
    * The packet's integrity in terms of allowed ranges are not tested, since that is implementation dependent.
    * To work with that, check each piece of data for its integrity while you are reading it.
    * <p>
    * It is suggested that if you find the data has been tampered with in sending (maliciously) then you should
    * disconnect the client and blacklist their IP for this server session.
    * <p>
    * Packets are received before {@link #tick(PNClient)} is called, and then are passed to their handler, which is
    * overridable in {@link #packet(short, PNPacketFactory, PNPacketHandler)}
    *
    * @param delegate Delegate client object of parent class {@link PNClient}. This delegate is also the recipient.
    * @param id       The 2-byte packet ID
    * @param reader   Reader that is wrapped to the underlying input stream.
    * @see #packet(short, PNPacketFactory, PNPacketHandler)
    */
   public final void receivePacket(T delegate, short id, PNInputStreamReader reader) throws IOException {
      PNPacketRegistration reg = this.getPacketRegistration(id);
      if (reg == null) {
         PN.info(this, "Received packet which has no handler. Packet ID: " + id + "\n\t" + delegate);
      }
      else {
         PN.info(this, "Received packet with ID: " + id + "\n\t" + delegate);
         PNPacket packet = (PNPacket)reg.getFactory().create();
         packet.read(reader);
         reg.getHandler().handlePacket(delegate, id, packet);
      }
   }

   /**
    * Called by the delegate (thread-safe) when a disconnection occurs on the delegate, or when the delegate
    * intentionally disconnects.
    * <p>
    * Responds to the delegate's {@link PNClient#didDisconnect()} state. If the delegate intentionally disconnected,
    * then disconnectedSelf will be true.
    * <p>
    * See {@link PNClient#getErrorStorage()} and {@link PNClient#getState()} to figure out more specific details about
    * the client's disconnection process.
    *
    * @param delegate         The delegate client object of parent class {@link PNClient}. The delegate's connection is
    *                         now
    *                         disconnected.
    * @param disconnectedSelf True if the delegate client intended to disconnect. False otherwise.
    * @see PNClient#getErrorStorage()
    * @see PNClient#getState()
    */
   public abstract void disconnected(T delegate, boolean disconnectedSelf);

   /**
    * Sends a packet using a delegate sender of type {@link PNClient}.
    * <p>
    * You can send packets at any point in time and the synchronization is handled under the hood. If you want to
    * make sure all packets are sent after packets are received, however, then make sure to send packets in the
    * {@link #tick(PNClient)} method.
    *
    * @param delegate The delegate responsible for sending the packet
    * @param packet   The packet to send. Delegates to {@link PNPacket#write(PNOutputStreamWriter)}
    */
   protected void sendPacket(T delegate, PNPacket packet) {
      delegate.getConnection().sendPacket(packet);
   }

   /**
    * Disconnects the delegate client
    * <p>
    * It is recommended to call this method within {@link #tick(PNClient)} instead of calling it externally. That will
    * prevent any synchronization issues.
    *
    * @param delegate The delegate client to disconnect
    */
   protected void disconnect(T delegate) {
      delegate.disconnect();
   }

   /**
    * Register packets to listen for their arrival. See {@link #packet(short, PNPacketFactory, PNPacketHandler)}
    */
   protected abstract void registerPackets();

   /**
    * The {@link #packet(short, PNPacketFactory, PNPacketHandler)}} method is what registers a packet to the session.
    * <p>
    * Target the default, argument-less, read-ready constructor for the packet.
    * In all cases, that would be PacketClassName::new.
    *
    * @param packetId The packet ID which uniquely identifies the packet with respect to other packets
    * @param factory The factory method (method reference) to create a new Packet of the type specified.
    * @param handler A handler method (lambda) that is called when the packet is received.
    * @param <P> The type of packet to construct. This is figured out by the way you specify your factory method and
    *           the handler lambda function.
    */
   protected <P extends Object & PNPacket> void packet(short packetId, PNPacketFactory<P> factory,
                                                       PNPacketHandler<T, P> handler) {
      while (this.packetRegistrations.size() <= packetId) {
         this.packetRegistrations.add(null);
      }
      this.packetRegistrations.set(packetId, new PNPacketRegistration(factory, handler));
   }

   /**
    * Finds a {@link PNPacketRegistration} if one exists for the given packetId
    * @param packetId The packetId to use to look up the {@link PNPacketRegistration}
    * @return Returns the {@link PNPacketRegistration} or null of one does not exist for the packetId
    */
   protected final PNPacketRegistration getPacketRegistration(short packetId) {
      if (packetId < 0 || packetId >= this.packetRegistrations.size())
         return null;
      return this.packetRegistrations.get(packetId);
   }

   /**
    * Finds a {@link PNPacketHandler} if one exists for the given packetId
    * @param packetId The packetId to use to look up the {@link PNPacketHandler}
    * @return Returns the {@link PNPacketHandler} or null of one does not exist for the packetId
    */
   protected final PNPacketHandler getPacketHandler(short packetId) {
      PNPacketRegistration reg = getPacketRegistration(packetId);
      if (reg == null)
         return null;
      return reg.getHandler();
   }

   /**
    * Finds a {@link PNPacketFactory} if one exists for the given packetId
    * @param packetId The packetId to use to look up the {@link PNPacketFactory}
    * @return Returns the {@link PNPacketFactory} or null of one does not exist for the packetId
    */
   protected final PNPacketFactory getPacketFactory(short packetId) {
      PNPacketRegistration reg = getPacketRegistration(packetId);
      if (reg == null)
         return null;
      return reg.getFactory();
   }

   public static class SessionId {

      private static Set<SessionId> generated = new HashSet<>();

      private static final int DATA_LENGTH = 16;
      private static final byte[] buffer = new byte[DATA_LENGTH];

      private String data;

      private SessionId() {
      }

      public String getData() {
         return this.data;
      }

      public synchronized static SessionId create(String data) {
         SessionId id = new SessionId();
         id.data = data;
         Base64.getDecoder().decode(id.data.getBytes(), id.buffer);
         return id;
      }

      public synchronized static SessionId next() {
         SessionId id = new SessionId();
         while (true) {
            PN.random.nextBytes(buffer);
            String next = new String(Base64.getEncoder().encode(buffer));
            if (!generated.contains(next)) {
               id.data = next;
               generated.add(id);
               break;
            }
         }
         return id;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         SessionId sessionId = (SessionId)o;
         return Objects.equals(data, sessionId.data);
      }

      @Override
      public int hashCode() {
         return Objects.hash(data);
      }

      @Override
      public String toString() {
         return "SessionID[" + this.getData() + "]";
      }
   }

}
