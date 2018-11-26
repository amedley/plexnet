package com.medleystudios.pn.conn.packets;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.io.PNPacketOutputStream;
import com.medleystudios.pn.io.PNStreamable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PNPingPacket implements PNStreamable<PNInputStreamReader<?>, PNOutputStreamWriter<PNPacketOutputStream>> {

   public List<Date> dates = new ArrayList<>();

   @Override
   public void read(PNInputStreamReader<?> reader) throws IOException {
      int datesSize = reader.readShort();
      for (int i = 0; i < datesSize; i++) {
         dates.add(PN.dateFromISO(reader.readString()));
      }
   }

   @Override
   public void write(PNOutputStreamWriter<PNPacketOutputStream> writer) throws IOException {
      PNPacketOutputStream out = writer.getOutputStream();
      out.begin(PNPackets.PING_PACKET);

      writer.writeShort(dates.size());
      for (int i = 0; i < dates.size(); i++) {
         writer.writeString(PN.toISO(dates.get(i)));
      }

      out.end();
   }
}
