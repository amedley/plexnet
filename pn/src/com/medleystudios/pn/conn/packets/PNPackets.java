package com.medleystudios.pn.conn.packets;

public class PNPackets {

   private static short currentPacketID = 0;

   public static final int ID_BYTES = 2;
   public static final int LENGTH_BYTES = 4;

   public static synchronized short nextPacketID() {
      return currentPacketID++;
   }

   public static short PING_PACKET = nextPacketID();
   public static short PONG_PACKET = nextPacketID();

}
