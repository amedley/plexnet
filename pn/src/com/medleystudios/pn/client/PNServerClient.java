package com.medleystudios.pn.client;

import com.medleystudios.pn.conn.PNConnection;
import com.medleystudios.pn.session.PNServerClientSession;
import com.medleystudios.pn.session.PNSession;
import com.medleystudios.pn.session.PNSessionFactory;

public final class PNServerClient extends PNClient<PNServerClientSession> {

   public PNServerClient(PNConnection connection, PNSessionFactory sessionFactory) {
      super(connection, sessionFactory);
      this.startSession(PNSession.SessionId.next());
   }

   @Override
   public String toString() {
      return "PNServerClient[id=" + getId().getData() + ", state=" + getState() + ", " + getConnection() + "]";
   }

}
