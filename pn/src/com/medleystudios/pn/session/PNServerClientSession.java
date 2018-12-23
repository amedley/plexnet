package com.medleystudios.pn.session;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.client.PNServerClient;

public class PNServerClientSession extends PNSession<PNServerClient> {

   public PNServerClientSession(SessionId sessionId) {
      super(sessionId);
   }

   @Override
   public void tick(PNServerClient client) {

   }

   @Override
   public void disconnected(PNServerClient client, boolean disconnectedSelf) {
      PN.info(this, "disconnected: " + client);
   }

   @Override
   protected void registerPackets() {
   }

   @Override
   public String log(String message) {
      return "PNServerClientSession[" + this.getId().getData() + "] " + message;
   }
}
