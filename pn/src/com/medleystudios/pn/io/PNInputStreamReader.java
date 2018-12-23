package com.medleystudios.pn.io;

import com.medleystudios.pn.PN;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import static com.medleystudios.pn.util.PNUtil.u;

public class PNInputStreamReader {
   private InputStream in;

   public PNInputStreamReader(InputStream in) {
      this.in = in;
   }

   public int readU() throws IOException {
      return u((byte)in.read());
   }

   public long readUL() throws IOException {
      return readU();
   }

   public boolean readBoolean() throws IOException {
      return (byte)in.read() == 1;
   }

   public byte readByte() throws IOException {
      return (byte)in.read();
   }

   public short readShort() throws IOException {
      return (short)((readU() << 8)
         | readU());
   }

   public int readInt() throws IOException {
      return (readU() << 24)
         | (readU() << 16)
         | (readU() << 8)
         | readU();
   }

   public long readLong() throws IOException {
      return (readUL() << 56)
         | (readUL() << 48)
         | (readUL() << 40)
         | (readUL() << 32)
         | (readUL() << 24)
         | (readUL() << 16)
         | (readUL() << 8)
         | readUL();
   }

   public float readFloat() throws IOException {
      return Float.intBitsToFloat(readInt());
   }

   public double readDouble() throws IOException {
      return Double.longBitsToDouble(readLong());
   }

   public String readString() throws IOException {
      int length = readInt();
      try {
         byte[] data = new byte[length];
         in.read(data, 0, length);
         return new String(data, "UTF-8");
      }
      catch (UnsupportedEncodingException e) {
         PN.error(e, this, "UTF-8 is not a supported encoding on this machine!");
         return null;
      }
   }

   public int available() throws IOException {
      return in.available();
   }
}
