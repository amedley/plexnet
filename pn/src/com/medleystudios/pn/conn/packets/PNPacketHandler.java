package com.medleystudios.pn.conn.packets;

import com.medleystudios.pn.client.PNClient;

public interface PNPacketHandler<D extends PNClient, P extends Object & PNPacket> {

   void handlePacket(D delegate, short id, P packet);

}
