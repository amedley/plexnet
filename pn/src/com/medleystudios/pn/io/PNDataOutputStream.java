package com.medleystudios.pn.io;

import com.medleystudios.pn.util.PNErrors;

import java.io.DataOutputStream;
import java.nio.charset.Charset;

public class PNDataOutputStream {

   private byte[] data;
   private static final int DEFAULT_INITIAL_CAPACITY = 32;  // 32b
   private static final int DEFAULT_MAX_CAPACITY = 16384;   // 16kb
   private final int initialCapacity;
   private final int maxCapacity;
   private int capacity;
   private int size;
   private int writer;

   public PNDataOutputStream(int initialCapacity, int maxCapacity) {
      if (maxCapacity < initialCapacity) {
         throw new IllegalArgumentException("Max capacity " + maxCapacity + " cannot be less than initial capacity "
            + initialCapacity);
      }
      this.initialCapacity = initialCapacity;
      this.maxCapacity = maxCapacity;
      this.ensureCapacity(Math.min(this.maxCapacity, this.initialCapacity));
      reset();
   }

   public PNDataOutputStream() {
      this(DEFAULT_INITIAL_CAPACITY, DEFAULT_MAX_CAPACITY);
   }

   public void reset() {
      this.size = 0;
      this.writer = 0;
   }

   private void ensureCapacity(int amount) {
      if (this.capacity >= amount) {
         return;
      }
      if (amount > maxCapacity) {
         throw new PNErrors.ExceededMaxCapacity("Ensure capacity " + amount + " exceeds maximum capacity "
            + this.maxCapacity);
      }
      if (this.data == null) {
         this.data = new byte[amount];
      }
      else {
         byte[] replacement = new byte[amount];
         for (int i = 0; i < size; i++) {
            replacement[i] = this.data[i];
         }
         this.data = replacement;
      }
      this.capacity = this.data.length;
   }

   private void addSize(int amount) {
      int nextSize = this.size + amount;
      if (nextSize > this.capacity) {
         ensureCapacity(Math.max(this.capacity * 2, nextSize));
      }
      this.size = nextSize;
   }

   public void write(int b) {
      this.data[writer++] = (byte)b;
   }

   public void writeBoolean(boolean v) {
      this.addSize(1);
      write((byte)(v ? 1 : 0));
   }

   public void writeByte(int v) {
      this.addSize(1);
      write((byte)(v & 0xff));
   }

   public void writeShort(int v) {
      this.addSize(2);
      write((byte)((v >> 8) & 0xff));
      write((byte)(v & 0xff));
   }

   public void writeInt(int v) {
      this.addSize(4);
      write((byte)((v >> 24) & 0xff));
      write((byte)((v >> 16) & 0xff));
      write((byte)((v >> 8) & 0xff));
      write((byte)(v & 0xff));
   }

   public void writeLong(long v) {
      this.addSize(8);
      write((byte)((v >> 56) & 0xff));
      write((byte)((v >> 48) & 0xff));
      write((byte)((v >> 40) & 0xff));
      write((byte)((v >> 32) & 0xff));
      write((byte)((v >> 24) & 0xff));
      write((byte)((v >> 16) & 0xff));
      write((byte)((v >> 8) & 0xff));
      write((byte)(v & 0xff));
   }

   public void writeFloat(float v) {
      writeInt(Float.floatToIntBits(v));
   }

   public void writeDouble(double v) {
      writeLong(Double.doubleToLongBits(v));
   }

   public void writeString(String v) {
      byte[] bytes = v.getBytes(Charset.forName("UTF-8"));
      int length = bytes.length;
      this.addSize(4 + length);
      write((byte)((length >> 24) & 0xff));
      write((byte)((length >> 16) & 0xff));
      write((byte)((length >> 8) & 0xff));
      write((byte)(length & 0xff));

      for (int i = 0; i < bytes.length; i++) {
         write(bytes[i]);
      }
   }

   public byte[] getData() {
      byte[] result = new byte[size];
      for (int i = 0; i < result.length; i++) {
         result[i] = this.data[i];
      }
      return result;
   }

}
