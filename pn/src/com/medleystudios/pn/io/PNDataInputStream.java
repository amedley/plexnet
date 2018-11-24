package com.medleystudios.pn.io;

import com.medleystudios.pn.PN;

import java.io.UnsupportedEncodingException;

import static com.medleystudios.pn.util.PNUtil.u;

public class PNDataInputStream {

   private byte[] data;
   private int reader;

   public PNDataInputStream() {
      reset(null);
   }

   public void reset(byte[] data) {
      this.reader = 0;
      this.data = data;
   }

   public byte read() {
      return this.data[reader++];
   }

   public int readU() {
      return u(read());
   }

   public long readUL() {
      return u(read());
   }

   public boolean readBoolean() {
      return read() == 1;
   }

   public byte readByte() {
      return read();
   }

   public short readShort() {
      return (short)((readU() << 8)
         | readU());
   }

   public int readInt() {
      return (readU() << 24)
         | (readU() << 16)
         | (readU() << 8)
         | readU();
   }

   public long readLong() {
      return (readUL() << 56)
         | (readUL() << 48)
         | (readUL() << 40)
         | (readUL() << 32)
         | (readUL() << 24)
         | (readUL() << 16)
         | (readUL() << 8)
         | readUL();
   }

   public float readFloat() {
      return Float.intBitsToFloat(readInt());
   }

   public double readDouble() {
      return Double.longBitsToDouble(readLong());
   }

   public String readString() {
      int length = readInt();
      try {
         String result = new String(this.data, reader, length, "UTF-8");
         reader += length;
         return result;
      }
      catch (UnsupportedEncodingException e) {
         PN.error(e, this, "UTF-8 is not a supported encoding on this machine!");
         return null;
      }
   }

}
