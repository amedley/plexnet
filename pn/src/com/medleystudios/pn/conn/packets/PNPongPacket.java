package com.medleystudios.pn.conn.packets;

import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;

import java.io.IOException;

public class PNPongPacket implements PNPacket {

   @Override
   public short getPacketId() {
      return PNPackets.PONG_PACKET;
   }

   @Override
   public void read(PNInputStreamReader reader) throws IOException {
   }

   @Override
   public void write(PNOutputStreamWriter writer) throws IOException {
   }
}
