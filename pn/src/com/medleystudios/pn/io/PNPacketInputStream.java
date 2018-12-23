package com.medleystudios.pn.io;

import java.io.InputStream;

public class PNPacketInputStream extends PNAsyncBufferedInputStream {

   public static PNPacketInputStream start(InputStream in) {
      return start(in, null, 0);
   }

   public static PNPacketInputStream start(InputStream in, Runnable onClosed) {
      return start(in, onClosed, 0);
   }

   public static PNPacketInputStream start(InputStream in, long asyncDelayMillis) {
      return start(in, null, asyncDelayMillis);
   }

   public static PNPacketInputStream start(InputStream in, Runnable onClosed, long asyncDelayMillis) {
      PNPacketInputStream pis = new PNPacketInputStream(in, onClosed, asyncDelayMillis);
      new Thread(pis).start();
      return pis;
   }

   protected PNPacketInputStream(InputStream in, Runnable onClosed, long asyncDelayMillis) {
      super(in, onClosed, asyncDelayMillis);
   }

}
