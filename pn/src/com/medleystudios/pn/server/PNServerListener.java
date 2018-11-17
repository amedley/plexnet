package com.medleystudios.pn.server;

public interface PNServerListener {

   void onRegisterListener(PNServer server);
   void onUnregisterListener(PNServer server);
   void onClose(PNServer server);

}
