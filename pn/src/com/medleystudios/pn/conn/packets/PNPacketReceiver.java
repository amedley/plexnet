package com.medleystudios.pn.conn.packets;

import com.medleystudios.pn.io.PNInputStreamReader;

public interface PNPacketReceiver {

   void receivePacket(short id, PNInputStreamReader reader);

}
