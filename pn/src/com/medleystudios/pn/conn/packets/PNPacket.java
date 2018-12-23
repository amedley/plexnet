package com.medleystudios.pn.conn.packets;

import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.io.PNStreamable;

public interface PNPacket extends PNStreamable<PNInputStreamReader, PNOutputStreamWriter> {

   short getPacketId();

}
