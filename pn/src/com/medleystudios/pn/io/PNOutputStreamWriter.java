package com.medleystudios.pn.io;

import com.medleystudios.pn.util.PNUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;

public class PNOutputStreamWriter implements Runnable {

   private final Object closeLock = new Object();

   private boolean closed = false;
   private OutputStream out;

   private String errorMessage = null;

   /**
    * Runs when we close the output stream
    */
   private Runnable onClosed;

   // We need constant read/write time for each data chunk and we also need FIFO, so a Queue is the perfect data type
   // to store the data chunks
   /**
    * Represents the size of each write-chunk. Bounded from above by MTU for Ethernet (1500 bytes)
    */
   private static final int CHUNK_SIZE = 1024;
   private LinkedList<byte[]> chunks;

   /**
    * The index at which to begin reading data from the front chunk
    */
   private int frontChunkReaderIndex;

   /**
    * The next index at which to write data in the back chunk
    */
   private int backChunkWriterIndex;

   public PNOutputStreamWriter(OutputStream out, Runnable onClosed) {
      this.out = out;
      this.chunks = new LinkedList<>();
      this.frontChunkReaderIndex = 0;
      this.backChunkWriterIndex = 0;
      this.onClosed = onClosed;
   }

   @Override
   public void run() {
      start();
      loop();
   }

   private synchronized void start() {
   }

   private void loop() {
      while (true) {
         try {
            Thread.sleep(2);
         }
         catch (InterruptedException e) {
            PNUtil.fatalError(e, this, "Unable to sleep thread");
         }

         synchronized (this) {
            if (isClosed()) break;

            int chunksSize = this.chunks.size();
            int length = (backChunkWriterIndex - frontChunkReaderIndex) + (chunksSize - 1) * CHUNK_SIZE;
            if (length <= 0) continue;

            boolean writeSuccessful = false;

            while (chunksSize > 0) {
               byte[] front = this.chunks.peek();
               int writeLength = chunksSize == 1 ? backChunkWriterIndex - frontChunkReaderIndex
                  : front.length - frontChunkReaderIndex;
               try {
                  this.out.write(front, frontChunkReaderIndex, writeLength);
                  chunksSize--;
                  writeSuccessful = true;
                  this.frontChunkReaderIndex += writeLength;
                  if (this.frontChunkReaderIndex >= CHUNK_SIZE) {
                     this.frontChunkReaderIndex -= CHUNK_SIZE;
                     this.chunks.remove();
                  }
               }
               catch (IOException e) {
                  PNUtil.error(e, this, "Failed to write data! Closing output stream.");
                  this.setErrorMessage(e.getMessage());
                  this.close();
                  break;
               }
            }

            if (writeSuccessful == true) {
               try {
                  this.out.flush();
               }
               catch (IOException e) {
                  PNUtil.error(e, this, "Failed to flush data! Closing output stream.");
                  this.setErrorMessage(e.getMessage());
                  this.close();
               }
            }
         }
      }
   }

   public synchronized void write(byte[] bytes) {
      if (this.isClosed()) return;

      byte[] back = chunks.peekLast();
      if (back == null) {
         back = new byte[CHUNK_SIZE];
         chunks.add(back);
      }
      for (int i = 0; i < bytes.length; i++) {
         back[backChunkWriterIndex++] = bytes[i];
         if (backChunkWriterIndex == CHUNK_SIZE) {
            back = new byte[CHUNK_SIZE];
            chunks.add(back);
            backChunkWriterIndex = 0;
         }
      }
   }

   public synchronized String getErrorMessage() {
      return this.errorMessage;
   }

   public synchronized String setErrorMessage(String value) {
      return this.errorMessage = value;
   }

   public boolean isClosed() {
      synchronized (closeLock) {
         return this.closed;
      }
   }

   /**
    * Closes the output stream writer and the underlying {@link OutputStream}
    */
   public void close() {
      close(true);
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
   public synchronized void close(boolean closeStream) {
      synchronized (closeLock) {
         if (isClosed()) return;
         this.closed = true;

         if (closeStream == true) {
            try {
               this.out.close();
            }
            catch (IOException e) {
               PNUtil.error(e, this, "Failed to close output stream.");
            }
         }

         if (this.onClosed != null) {
            this.onClosed.run();
            this.onClosed = null;
         }
      }
   }
}
