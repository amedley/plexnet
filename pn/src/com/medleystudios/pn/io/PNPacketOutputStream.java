package com.medleystudios.pn.io;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.conn.packets.PNPacket;

import java.io.IOException;
import java.io.OutputStream;

public class PNPacketOutputStream extends PNAsyncBufferedOutputStream {

   public static PNPacketOutputStream start(OutputStream out) {
      return start(out, null, 0);
   }

   public static PNPacketOutputStream start(OutputStream out, Runnable onClosed) {
      PNPacketOutputStream pos = new PNPacketOutputStream(out, onClosed, 0);
      new Thread(pos).start();
      return pos;
   }

   public static PNPacketOutputStream start(OutputStream out, long asyncDelayMillis) {
      return start(out, null, asyncDelayMillis);
   }

   public static PNPacketOutputStream start(OutputStream out, Runnable onClosed, long asyncDelayMillis) {
      PNPacketOutputStream pos = new PNPacketOutputStream(out, onClosed, asyncDelayMillis);
      new Thread(pos).start();
      return pos;
   }

   protected PNPacketOutputStream(OutputStream out, Runnable onClosed, long asyncDelayMillis) {
      super(out, onClosed, asyncDelayMillis);
   }

   public boolean write(PNPacket packet, PNOutputStreamWriter writer) {
      synchronized (this) {
         synchronized (this.getBufferLock()) {
            if (this.isClosed()) {
               return false;
            }
            try {
               // write a fake length (will be updated in the ".end" call)
               // this is just to shift the payload to the right 4 bytes so we can overwrite the length in '.end'.
               writer.writeInt(0);

               // take the initial size (excludes the length bytes)
               int initialSize = this.getSize();

               // write in the id so the client doesn't have to
               writer.writeShort(packet.getPacketId());

               // write the payload
               packet.write(writer);

               int length = this.getSize() - initialSize;

               // overwrite the length
               byte[] buf = this.getBuffer();
               buf[initialSize - 4] = (byte)((length << 24) & 0xFF);
               buf[initialSize - 3] = (byte)((length << 16) & 0xFF);
               buf[initialSize - 2] = (byte)((length << 8) & 0xFF);
               buf[initialSize - 1] = (byte)(length & 0xFF);

               return true;
            }
            catch (IOException e) {
               PN.storeAndLogError(e, this, "Failed to write packet! Packet ID: " + packet.getPacketId()
                  + ", closing output stream");
               this.close(true);
               return false;
            }
         }
      }
   }

}
