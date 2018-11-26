package com.medleystudios.pn.io;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class PNAsyncBufferedOutputStream extends FilterOutputStream implements Runnable, PNErrorStorable {

   private final Object closeLock = new Object();
   private final Object bufferLock = new Object();

   private boolean closed = false;

   private PNErrorStorage errorStorage = new PNErrorStorage();

   /**
    * Runs when we close the output stream
    */
   private Runnable onClosed;

   // Most chunks won't be larger than 8kb. This is roughly the same size as the default buffer size
   // in java.io.BufferedOutputStream
   private static final int INITIAL_BUFFER_SIZE = 8000;

   // Scalable buffer
   private byte[] buffer;
   private int size = 0;

   private int outputTotal = 0;
   private int writeTotal = 0;

   private boolean ran = false;

   private boolean flushEnabled = true;

   private final long asyncDelayMillis;

   public static PNAsyncBufferedOutputStream start(OutputStream out) {
      return start(out, null, 0);
   }

   public static PNAsyncBufferedOutputStream start(OutputStream out, Runnable onClosed) {
      PNAsyncBufferedOutputStream abos = new PNAsyncBufferedOutputStream(out, onClosed, 0);
      new Thread(abos).start();
      return abos;
   }

   public static PNAsyncBufferedOutputStream start(OutputStream out, long asyncDelayMillis) {
      return start(out, null, asyncDelayMillis);
   }

   public static PNAsyncBufferedOutputStream start(OutputStream out, Runnable onClosed, long asyncDelayMillis) {
      PNAsyncBufferedOutputStream abos = new PNAsyncBufferedOutputStream(out, onClosed, asyncDelayMillis);
      new Thread(abos).start();
      return abos;
   }


   protected PNAsyncBufferedOutputStream(OutputStream out, Runnable onClosed, long asyncDelayMillis) {
      super(out);
      this.onClosed = onClosed;
      this.buffer = new byte[INITIAL_BUFFER_SIZE];
      this.asyncDelayMillis = asyncDelayMillis;
   }

   @Override
   public void run() {
      if (ran == true) {
         throw new UnsupportedOperationException(this.getClass().getSimpleName() + " Called 'run' > 1 time");
      }
      ran = true;

      if (asyncDelayMillis > 0) {
         PN.sleep(asyncDelayMillis);
      }

      produce();
   }

   private void scale() {
      synchronized (bufferLock) {
         byte[] replacement = new byte[buffer.length * 2];
         for (int i = 0; i < size; i++) {
            replacement[i] = buffer[i];
         }
         buffer = replacement;
      }
   }

   private void produce() {
      try {
         while (true) {
            PN.sleep(2);

            synchronized (bufferLock) {
               if (isClosed()) break;
               flush();
            }
         }
      }
      catch (IOException e) {
         PN.storeAndLogError(e, this, "Failed to write data! Closing output stream.");
         this.close();
      }
   }

   private void output(int b) {
      if (size >= buffer.length) {
         scale();
      }
      buffer[size++] = (byte)b;
      writeTotal++;
   }

   protected void disableFlush() {
      synchronized (bufferLock) {
         flushEnabled = false;
      }
   }

   protected void enableFlush() {
      synchronized (bufferLock) {
         flushEnabled = true;
      }
   }

   @Override
   public void write(int b) {
      synchronized (bufferLock) {
         if (this.isClosed()) return;
         this.output(b);
      }
   }

   @Override
   public void write(byte[] bytes) {
      this.write(bytes, 0, bytes.length);
   }

   @Override
   public void write(byte[] bytes, int off, int len) {
      synchronized (bufferLock) {
         if (this.isClosed()) return;

         if (bytes == null) {
            throw new NullPointerException();
         }
         else if (off < 0 || len < 0 || len > bytes.length - off) {
            throw new IndexOutOfBoundsException();
         }
         else if (len == 0) {
            return;
         }

         for (int i = 0; i < len; i++) {
            this.output(bytes[off + i]);
         }
      }
   }

   /**
    * This should only be used for testing and/or if you are prepared to handle a potential mid-flush close on the
    * underlying output stream.
    * <p>
    * Flushes the output stream and does not rethrow the error.
    */
   public void flushUnsafe() {
      try { flush(); }
      catch (IOException e) { PN.error(e, this, "Failed unsafe flush"); } ;
   }

   @Override
   public void flush() throws IOException {
      synchronized (bufferLock) {
         if (!flushEnabled || size == 0) return;
         this.out.write(this.buffer, 0, size);
         outputTotal += size;
         super.flush();
         size = 0;
      }
   }

   public int getSize() {
      return this.size;
   }

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.closed;
      }
   }

   /**
    * Closes the output stream writer and the underlying {@link OutputStream}
    */
   @Override
   public void close() {
      close(true);
   }

   public Object getBufferLock() {
      return this.bufferLock;
   }

   /**
    * Closes the output stream writer permanently.
    * <p>
    * Allows you to specify whether or not to close the underlying stream, which may prevent from closing the
    * underlying stream more than once in case it will be closed by a subsequent call.
    *
    * @param closeStream Set true to close the underlying {@link OutputStream}. Set false to not close the underlying
    *                    {@link OutputStream}, but perform the rest of the close operation. Setting this to false is
    *                    useful in case you know the {@link OutputStream} will be closed by a subsequent.
    */
   public void close(boolean closeStream) {
      synchronized (this) {
         // contrary to the PNAsyncBufferedInputStream, we sync to the bufferLock when closing. This is because we
         // know the write method isn't blocking.
         synchronized (bufferLock) {
            synchronized (closeLock) {
               if (isClosed()) return;
               this.closed = true;

               if (closeStream == true) {
                  try {
                     super.close(); // calls flush
                  }
                  catch (IOException e) {
                     PN.error(e, this, "Failed to close output stream.");
                  }
               }
               else {
                  try {
                     this.flush();
                  }
                  catch (IOException e) {
                     PN.softError(this, "Failed to flush resources mid-close!");
                  }
               }

               if (this.onClosed != null) {
                  this.onClosed.run();
                  this.onClosed = null;
               }
            }
         }
      }
   }

   protected byte[] getBuffer() {
      return this.buffer;
   }

   public long getOutputTotal() {
      return this.outputTotal;
   }

   public long getWriteTotal() {
      return this.writeTotal;
   }

   @Override
   public PNErrorStorage getErrorStorage() {
      return this.errorStorage;
   }
}
