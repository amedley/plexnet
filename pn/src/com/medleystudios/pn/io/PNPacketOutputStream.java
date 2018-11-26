package com.medleystudios.pn.io;

import java.io.OutputStream;

public class PNPacketOutputStream extends PNAsyncBufferedOutputStream {

   private boolean didBegin = false;
   private int initialSize = 0;

   public static PNPacketOutputStream start(OutputStream out) {
      return start(out, null, 0);
   }

   public static PNPacketOutputStream start(OutputStream out, Runnable onClosed) {
      PNPacketOutputStream abos = new PNPacketOutputStream(out, onClosed, 0);
      new Thread(abos).start();
      return abos;
   }

   public static PNPacketOutputStream start(OutputStream out, long asyncDelayMillis) {
      return start(out, null, asyncDelayMillis);
   }

   public static PNPacketOutputStream start(OutputStream out, Runnable onClosed, long asyncDelayMillis) {
      PNPacketOutputStream abos = new PNPacketOutputStream(out, onClosed, asyncDelayMillis);
      new Thread(abos).start();
      return abos;
   }


   protected PNPacketOutputStream(OutputStream out, Runnable onClosed, long asyncDelayMillis) {
      super(out, onClosed, asyncDelayMillis);
   }

   public void begin(short id) {
      synchronized (this) {
         if (this.didBegin == true) {
            throw new UnsupportedOperationException("Cannot call 'begin' for a single packet more than once");
         }
         this.didBegin = true;

         // disable flush so the output stream doesn't flush midway through writing the packet
         this.disableFlush();

         // write a fake length (will be updated in the ".end" call)
         // this is just to shift the payload to the right 4 bytes
         this.write(0);
         this.write(0);
         this.write(0);
         this.write(0);

         // take the initial size (excludes the length bytes)
         this.initialSize = this.getSize();

         // write in the id so the client doesn't have to
         this.write((id << 8) & 0xFF);
         this.write(id & 0xFF);

         // now we wait for the client to call "end"
      }
   }

   public void end() {
      synchronized (this) {
         if (!this.didBegin) {
            throw new UnsupportedOperationException("Cannot call 'end' for packet that did not begin writing");
         }
         this.didBegin = false;

         // figure out the true length of this packet now that it is done writing
         int length = this.getSize() - this.initialSize;

         // overwrite the length
         byte[] buf = this.getBuffer();
         buf[this.initialSize - 4] = (byte)((length << 24) & 0xFF);
         buf[this.initialSize - 3] = (byte)((length << 16) & 0xFF);
         buf[this.initialSize - 2] = (byte)((length << 8) & 0xFF);
         buf[this.initialSize - 1] = (byte)(length & 0xFF);

         // enable flush so the output stream can send the data written
         this.enableFlush();
      }
   }

}
