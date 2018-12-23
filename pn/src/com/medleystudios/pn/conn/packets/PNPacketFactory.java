package com.medleystudios.pn.conn.packets;

public interface PNPacketFactory<T extends Object & PNPacket> {

   T create();

}
