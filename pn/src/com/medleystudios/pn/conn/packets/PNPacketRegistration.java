package com.medleystudios.pn.conn.packets;

public class PNPacketRegistration {

   private PNPacketFactory factory;
   private PNPacketHandler handler;

   public PNPacketRegistration(PNPacketFactory factory, PNPacketHandler handler) {
      this.factory = factory;
      this.handler = handler;
   }

   public PNPacketFactory getFactory() {
      return this.factory;
   }

   public PNPacketHandler getHandler() {
      return this.handler;
   }

}
