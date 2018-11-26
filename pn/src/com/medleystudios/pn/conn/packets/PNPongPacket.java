package com.medleystudios.pn.conn.packets;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.io.PNPacketOutputStream;
import com.medleystudios.pn.io.PNStreamable;

import java.io.IOException;
import java.util.Date;

public class PNPongPacket implements PNStreamable<PNInputStreamReader<?>, PNOutputStreamWriter<PNPacketOutputStream>> {

   public Date date = null;

   @Override
   public void read(PNInputStreamReader<?> reader) throws IOException {
      date = PN.dateFromISO(reader.readString());
   }

   @Override
   public void write(PNOutputStreamWriter<PNPacketOutputStream> writer) throws IOException {
      PNPacketOutputStream out = writer.getOutputStream();
      out.begin(PNPackets.PONG_PACKET);

      writer.writeString(PN.toISO(date));

      out.end();
   }
}
