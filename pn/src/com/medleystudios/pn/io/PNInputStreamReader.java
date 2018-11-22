package com.medleystudios.pn.io;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.util.PNUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;

public class PNInputStreamReader implements Runnable {

   private final Object closeLock = new Object();
   private final Object readLock = new Object();

   private boolean reachedEnd = false;
   private boolean closed = false;
   private InputStream in;

   /**
    * Runs when we close the input stream
    */
   private Runnable onClosed;

   private String errorMessage = null;

   // We need constant read/write time for each data chunk and we also need FIFO, so a Queue is the perfect data type
   // to store the data chunks
   /**
    * Represents the size of each read-chunk. Bounded from above by MTU for Ethernet (1500 bytes)
    */
   private static final int CHUNK_SIZE = 1024;
   private LinkedList<byte[]> chunks;
   private final byte[] buffer = new byte[CHUNK_SIZE];

   /**
    * The next index at which to begin reading from the back chunk
    */
   private int backChunkReaderIndex;

   /**
    * @param in       The input stream to read from
    * @param onClosed Runs when the input stream closes.
    */
   public PNInputStreamReader(InputStream in, Runnable onClosed) {
      this.in = in;
      this.onClosed = onClosed;

      this.chunks = new LinkedList<>();
      this.backChunkReaderIndex = 0;
   }

   @Override
   public void run() {
      // consume blocks because it has an InputStream.read() call
      while (consume()) ;
   }

   /**
    * TODO
    * We need some way to packet-ize the data.
    * Probably not meant for either InputStreamReader or InputStreamWriter.
    * Maybe this should go in PNConnection?
    * Do we need a totally different class for packets?
    */
   public void process() {

   }

   /**
    * @return Returns true if it should continue to consume, false otherwise
    */
   private boolean consume() {
      int read = -1;

      // synchronized to this and closeLock
      if (isClosed()) return false;

      IOException errorIO = null;

      synchronized (readLock) {
         try {
            // blocks until data is received, we reach the end, or an exception is thrown
            read = this.in.read(buffer, 0, buffer.length);
         }
         catch (IOException e) {
            errorIO = e;
         }

         synchronized (this) {
            if (errorIO != null) {
               // One of the reasons an IOException may have been thrown is if we intentionally closed the stream
               // We have to check if this stream ran close()
               if (isClosed()) {
                  PN.log(this, "InputStream.read stopped blocking due to intentional close.");
               }
               else {
                  PN.error(errorIO, this, "Failed to read data! Closing input stream.");
                  this.setErrorMessage(errorIO.getMessage());
                  this.close();
               }
               return false;
            }
            else {
               // successful read
               if (read == -1) {
                  // reached end of stream
                  PN.log(this, "Reached end of input stream! Closing input stream.");
                  this.reachedEnd = true;
                  this.close();
                  return true;
               }
               else {
                  // consume data
                  byte[] back = this.chunks.peekLast();
                  if (back == null) {
                     back = new byte[CHUNK_SIZE];
                     this.chunks.add(back);
                  }
                  for (int i = 0; i < read; i++) {
                     back[backChunkReaderIndex++] = buffer[i];
                     if (backChunkReaderIndex >= CHUNK_SIZE) {
                        back = new byte[CHUNK_SIZE];
                        this.chunks.add(back);
                        backChunkReaderIndex = 0;
                     }
                  }
                  byte[][] chunks = new byte[this.chunks.size()][];
                  for (int i = 0; i < chunks.length; i++) {
                     chunks[i] = this.chunks.get(i);
                  }
                  PN.log(this, "Received bytes [len: " + read + ", data: " + PNUtil.toString(chunks) + "]");
                  return true;
               }
            }
         }
      }
   }

   public synchronized boolean didReachEnd() {
      return this.reachedEnd;
   }

   public synchronized String getErrorMessage() {
      return this.errorMessage;
   }

   protected synchronized String setErrorMessage(String value) {
      return this.errorMessage = value;
   }

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.closed;
      }
   }

   /**
    * Closes the input stream reader and the underlying {@link InputStream}
    */
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
    * the
    * underlying stream. If the stream is in use when it gets indirectly closed, it will throw an error due to being
    * interrupted.
    * <p>
    * Setting closeStream to false allows the reader to be in a "closed state" before the underlying stream is
    * interrupted. The reader recognizes this and treats the error as expected rather than unexpected. This also means
    * that the error will not be stored, which is how you can check if the close operation went as expected depending
    * on whether or not you choose to close the underlying stream on this call.
    * <p>
    * To see a more direct reason for the option to not close the underlying stream, see {@link #consume()}'s
    * implementation.
    *
    * @param closeStream Set true to close the underlying {@link InputStream}. Set false to not close the underlying
    *                    {@link InputStream}, but perform the rest of the close operation. Setting this to false is
    *                    useful in case you know the {@link InputStream} will be closed by a different call after this
    *                    call.
    */
   public synchronized void close(boolean closeStream) {
      synchronized (closeLock) {
         if (isClosed()) return;
         this.closed = true;

         if (closeStream == true) {
            try {
               this.in.close();
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
}
