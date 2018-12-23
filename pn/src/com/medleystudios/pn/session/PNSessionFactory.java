package com.medleystudios.pn.session;

public interface PNSessionFactory {

   PNSession create(PNSession.SessionId sessionId);

}
