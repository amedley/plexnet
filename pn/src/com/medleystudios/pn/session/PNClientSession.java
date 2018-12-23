package com.medleystudios.pn.session;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.client.PNClient;

public class PNClientSession extends PNSession<PNClient> {

   public PNClientSession(SessionId sessionId) {
      super(sessionId);
   }

   @Override
   public void tick(PNClient client) {

   }

   @Override
   public void disconnected(PNClient client, boolean disconnectedSelf) {
      PN.info(this, "disconnected: " + client);
   }

   @Override
   protected void registerPackets() {
   }

   @Override
   public String log(String message) {
      return "PNClientSession[" + this.getId().getData() + "] " + message;
   }
}
