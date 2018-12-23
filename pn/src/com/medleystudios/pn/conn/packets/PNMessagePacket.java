package com.medleystudios.pn.conn.packets;

import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;

import java.io.IOException;

public class PNMessagePacket implements PNPacket {

   public String message;

   public PNMessagePacket() {
      this.message = null;
   }

   public PNMessagePacket(String sessionIdData) {
      if (sessionIdData == null) throw new IllegalArgumentException("reason null");

      this.message = sessionIdData;
   }

   @Override
   public short getPacketId() {
      return PNPackets.MESSAGE_PACKET;
   }

   @Override
   public void read(PNInputStreamReader reader) throws IOException {
      this.message = reader.readString();
   }

   @Override
   public void write(PNOutputStreamWriter writer) throws IOException {
      writer.writeString(message);
   }

}
