package com.medleystudios.pn.policy.server;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.client.PNClient;
import com.medleystudios.pn.client.PNServerClient;
import com.medleystudios.pn.conn.packets.PNMessagePacket;
import com.medleystudios.pn.conn.packets.PNPacketReceiver;
import com.medleystudios.pn.conn.packets.PNPackets;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.policy.PNPolicy;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class PNAcceptancePolicy implements PNPolicy {

   private boolean failed = false;
   private boolean success = false;
   private String errorMessage = null;
   private int maxTime = 1000;
   private int time = maxTime;
   private Queue<PNMessagePacket> messages = new LinkedList<>();

   public synchronized boolean didFail() {
      return this.failed;
   }

   public synchronized boolean didSucceed() {
      return this.success;
   }

   public synchronized String getErrorMessage() {
      return this.errorMessage;
   }

   public synchronized int getMaxTime() {
      return this.maxTime;
   }

   protected synchronized void resetTime() {
      this.time = maxTime;
   }

   public synchronized int getTime() {
      return this.time;
   }

   protected final PNMessagePacket awaitNextMessage() {
      while (time > 0 && getErrorMessage() == null && !didFail() && !didSucceed()) {
         PN.sleep(2);
         time = Math.max(time - 2, 0);
         if (messages.size() > 0) {
            time = maxTime;
            return messages.remove();
         }
      }
      return null;
   }

   protected final synchronized void setErrorMessage(String errorMessage) {
      if (this.errorMessage != null) return;
      this.errorMessage = errorMessage;
   }

   private PNPacketReceiver receiver = (short id, PNInputStreamReader reader) -> {
      synchronized (this) {
         if (getErrorMessage() != null || didFail() || didSucceed()) {
            return;
         }

         try {
            if (id == PNPackets.MESSAGE_PACKET) {
               PNMessagePacket message = new PNMessagePacket();
               message.read(reader);
               messages.add(message);
            }
         }
         catch (IOException e) {
            setErrorMessage("Problem reading packet with ID: " + id + ", IOException: " + e.getMessage());
         }
      }
   };

   @Override
   public CompletableFuture<PolicyResolver> enforce(Map<String, Object> options) {
      return supplyAsync(() -> {
         PNServerClient client = (PNServerClient)options.get("client");
         if (client == null) {
            return new PolicyResolver("Invalid client object");
         }
         if (!client.isConnected()) {
            return new PolicyResolver("Client is not connected!");
         }

         client.getConnection().setPacketReceiver(receiver);

         PNMessagePacket readyPacket = awaitNextMessage();
         if (readyPacket != null) {
            // Handle
         }

         // We need to handle when an error message happens, time = 0, or the packet message is incorrect...
         // Maybye we can automate the error message check and time = 0 handler in a separate function???
         if (errorMessage != null) {
         }

         if (time == 0) {
            client.getConnection().sendPacket(new PNMessagePacket("RAN_OUT_OF_TIME"));
            return new PolicyResolver("Ran out of time.");
         }
      });
   }

   @Override
   public CompletableFuture<PolicyResolver> abide(Map<String, Object> options) {
      return supplyAsync(() -> {
         PNClient client = (PNClient)options.get("client");
         if (client == null) {
            return new PolicyResolver("Invalid client object");
         }
         if (!client.isConnected()) {
            return new PolicyResolver("Client is not connected!");
         }

         client.getConnection().sendPacket(new PNMessagePacket("READY"));

         return new PolicyResolver();
      });
   }

}
