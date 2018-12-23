package com.medleystudios.pn.conn.packets;

public class PNPackets {

   public static final int ID_BYTES = 2;
   public static final int LENGTH_BYTES = 4;

   private static short currentPacketId = 0;
   private static synchronized short nextPacketId() {
      return currentPacketId++;
   }

   public static short PING_PACKET = nextPacketId();
   public static short PONG_PACKET = nextPacketId();
   public static short MESSAGE_PACKET = nextPacketId();

}
