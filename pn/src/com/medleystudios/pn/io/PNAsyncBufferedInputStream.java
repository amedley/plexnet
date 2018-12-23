package com.medleystudios.pn.io;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.medleystudios.pn.util.PNUtil.u;

public class PNAsyncBufferedInputStream extends FilterInputStream implements Runnable, PNErrorStorable {

   private final Object closeLock = new Object();
   private final Object bufferLock = new Object();

   private boolean reachedEnd = false;
   private boolean closed = false;

   /**
    * Runs when we close the input stream
    */
   private Runnable onClosed;

   private PNErrorStorage errorStorage = new PNErrorStorage();

   // Most chunks won't be larger than 8kb. This is roughly the same size as the default buffer size
   // in java.io.BufferedInputStream
   private static final int INITIAL_BUFFER_SIZE = 8000;

   // Scalable, circular buffer
   private byte[] buffer;
   private int size = 0;
   private int setter = 0;
   private int reader = 0;

   private long inputTotal = 0;
   private long readTotal = 0;

   private boolean ran = false;

   private final long asyncDelayMillis;

   public static PNAsyncBufferedInputStream start(InputStream in) {
      return start(in, null, 0);
   }

   public static PNAsyncBufferedInputStream start(InputStream in, Runnable onClosed) {
      return start(in, onClosed, 0);
   }

   public static PNAsyncBufferedInputStream start(InputStream in, long asyncDelayMillis) {
      return start(in, null, asyncDelayMillis);
   }

   public static PNAsyncBufferedInputStream start(InputStream in, Runnable onClosed, long asyncDelayMillis) {
      PNAsyncBufferedInputStream abis = new PNAsyncBufferedInputStream(in, onClosed, asyncDelayMillis);
      new Thread(abis).start();
      return abis;
   }

   protected PNAsyncBufferedInputStream(InputStream in, Runnable onClosed, long asyncDelayMillis) {
      super(in);
      this.onClosed = onClosed;
      this.buffer = new byte[INITIAL_BUFFER_SIZE];
      this.asyncDelayMillis = asyncDelayMillis;
   }

   @Override
   public void run() {
      if (ran == true) {
         throw new UnsupportedOperationException(this.getClass().getSimpleName() + " Cannot call 'run' more than once");
      }
      ran = true;

      if (asyncDelayMillis > 0) {
         PN.sleep(asyncDelayMillis);
      }

      consume();
   }

   private void consume() {
      try {
         while (true) {
            if (isClosed()) break;

            int read = this.in.read(); // blocks

            if (read != -1) {
               synchronized (bufferLock) {
                  if (size >= buffer.length) {
                     scale();
                  }
                  buffer[setter++] = (byte)read;
                  size++;
                  inputTotal++;
                  if (setter >= buffer.length) {
                     setter = 0;
                  }
               }
            }
            else {
               PN.info(this, "Reached end of input stream! Closing input stream.");
               this.reachedEnd = true;
               this.close();
            }
         }
      }
      catch (IOException e) {
         if (isClosed()) {
            PN.info(this, "Input stream '.parse' was interrupted by an IOException. The stream was manually closed " +
               "after the parse started blocking. IOException: " + e.getMessage());
         }
         else {
            PN.storeAndLogError(e, this, "Input stream '.parse' interrupted by an unexpected IOException! Closing input stream.");
            this.close();
         }
      }
   }

   private byte input() {
      synchronized (bufferLock) {
         if (size == 0) {
            throw new IndexOutOfBoundsException();
         }
         byte v = buffer[reader++];
         if (reader >= buffer.length) {
            reader = 0;
         }
         size--;
         readTotal++;
         return v;
      }
   }

   @Override
   public int read() {
      return input();
   }

   @Override
   public int read(byte bytes[]) {
      return read(bytes, 0, bytes.length);
   }

   @Override
   public int read(byte bytes[], int off, int len) {
      synchronized (bufferLock) {
         if (bytes == null) {
            throw new NullPointerException();
         }
         else if (off < 0 || len < 0 || len > bytes.length - off) {
            throw new IndexOutOfBoundsException();
         }
         else if (len == 0) {
            return 0;
         }

         int read = 0;
         for (int i = 0; i < len; i++) {
            bytes[off + i] = input();
            if (size == 0) break;
         }
         return read;
      }
   }

   @Override
   public long skip(long n) {
      long skipped = 0;
      synchronized (bufferLock) {
         for (int i = 0; i < n; i++) {
            if (size == 0) {
               return skipped;
            }
            reader++;
            if (reader >= buffer.length) {
               reader = 0;
            }
            size--;
            readTotal++;
            skipped++;
         }
      }
      return skipped;
   }

   /**
    * Peeks ahead by 'offset' bytes and returns the value parse at that offset. The value is in the range [-1, 255]
    * @param offset The number of bytes to peak ahead
    * @return Returns -1 if no value was found, or the unsigned representation of the byte [0, 255] at the offset
    */
   public int peekU(int offset) {
      if (offset >= size) return -1;
      int r = reader + offset;
      if (r >= buffer.length)
         r -= buffer.length;

      return u(buffer[r]);
   }

   /**
    * Scales the circular buffer 2x
    */
   private void scale() {
      synchronized (bufferLock) {
         byte[] replacement = new byte[buffer.length * 2];
         for (int i = 0; i < size; i++) {
            replacement[i] = buffer[reader++];
            if (reader >= buffer.length) {
               reader = 0;
            }
         }
         buffer = replacement;
         reader = 0;
         setter = size;
      }
   }

   public synchronized boolean didReachEnd() {
      return this.reachedEnd;
   }

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.closed;
      }
   }

   /**
    * Closes the input stream reader and the underlying {@link InputStream}
    */
   @Override
   public void close() {
      close(true);
   }

   /**
    * Closes the input stream reader permanently.
    * <p>
    * Allows you to specify whether or not to close the underlying stream, which may prevent from closing the
    * underlying stream more than once in case it will be closed by a subsequent call.
    * <p>
    * If you know that after this call the underlying stream is going to be indirectly closed by a separate call, then
    * set closeStream to false. That will allow the reader to perform the close operation without officially closing
    * the underlying stream. If the stream is in use when it gets indirectly closed, it will throw an error due to being
    * interrupted.
    * <p>
    * Setting closeStream to false allows the reader to be in a "closed state" before the underlying stream is
    * interrupted. The reader recognizes this and treats the error as expected rather than unexpected. This also means
    * that the error will not be stored, which is how you can check if the close operation went as expected depending
    * on whether or not you choose to close the underlying stream on this call. This distinguishes abortive and orderly
    * close operations.
    * <p>
    * To see a more direct reason for the option to not close the underlying stream, see {@link #consume()}'s
    * implementation.
    *
    * @param closeStream Set true to close the underlying {@link InputStream}. Set false to not close the underlying
    *                    {@link InputStream}, but perform the rest of the close operation. Setting this to false is
    *                    useful in case you know the {@link InputStream} will be closed by a different call after this
    *                    call.
    */
   public void close(boolean closeStream) {
      synchronized (closeLock) {
         if (this.isClosed()) return;
         this.closed = true;
      }

      synchronized (this) {
         if (closeStream == true) {
            try {
               super.close();
            }
            catch (IOException e) {
               PN.error(e, this, "Failed to close input stream.");
            }
         }

         if (this.onClosed != null) {
            this.onClosed.run();
            this.onClosed = null;
         }
      }
   }

   /**
    * @return Returns the buffer lock object for external use
    */
   public Object getBufferLock() {
      return bufferLock;
   }

   /**
    * Returns an estimate of the number of bytes available. Not thread safe.
    * <p>
    * To safely rely on the accuracy of the return value, synchronize with the buffer lock (see {@link
    * #getBufferLock()}).
    * <p>
    * It is standard to ignore the potential usefulness of {@link InputStream#available()} because in other streams
    * it is merely an estimate. However, in {@link PNAsyncBufferedInputStream}, if synchronized to the buffer lock,
    * {@link #available()} is accuracy-perfect and should be used alongside the parse methods.
    *
    * @return Returns the number of bytes that can be parse.
    */
   @Override
   public int available() {
      return this.size;
   }

   public long getReadTotal() {
      return this.readTotal;
   }

   public long getInputTotal() {
      return this.inputTotal;
   }

   @Override
   public PNErrorStorage getErrorStorage() {
      return this.errorStorage;
   }

}
