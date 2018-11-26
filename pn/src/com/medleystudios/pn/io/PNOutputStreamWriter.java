package com.medleystudios.pn.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class PNOutputStreamWriter<T extends OutputStream> {

   T out;

   public PNOutputStreamWriter(T out) {
      if (out == null) throw new IllegalArgumentException("out cannot be null");
      this.out = out;
   }

   public void writeBoolean(boolean v) throws IOException {
      out.write((byte)(v ? 1 : 0));
   }

   public void writeByte(int v) throws IOException {
      out.write((byte)(v & 0xff));
   }

   public void writeShort(int v) throws IOException {
      this.out.write((byte)((v >> 8) & 0xff));
      this.out.write((byte)(v & 0xff));
   }

   public void writeInt(int v) throws IOException {
      this.out.write((byte)((v >> 24) & 0xff));
      this.out.write((byte)((v >> 16) & 0xff));
      this.out.write((byte)((v >> 8) & 0xff));
      this.out.write((byte)(v & 0xff));
   }

   public void writeLong(long v) throws IOException {
      this.out.write((byte)((v >> 56) & 0xff));
      this.out.write((byte)((v >> 48) & 0xff));
      this.out.write((byte)((v >> 40) & 0xff));
      this.out.write((byte)((v >> 32) & 0xff));
      this.out.write((byte)((v >> 24) & 0xff));
      this.out.write((byte)((v >> 16) & 0xff));
      this.out.write((byte)((v >> 8) & 0xff));
      this.out.write((byte)(v & 0xff));
   }

   public void writeFloat(float v) throws IOException {
      writeInt(Float.floatToIntBits(v));
   }

   public void writeDouble(double v) throws IOException {
      writeLong(Double.doubleToLongBits(v));
   }

   public void writeString(String v) throws IOException {
      byte[] bytes = v.getBytes(Charset.forName("UTF-8"));
      int length = bytes.length;
      this.out.write((byte)((length >> 24) & 0xff));
      this.out.write((byte)((length >> 16) & 0xff));
      this.out.write((byte)((length >> 8) & 0xff));
      this.out.write((byte)(length & 0xff));

      for (int i = 0; i < bytes.length; i++) {
         this.out.write(bytes[i]);
      }
   }

   public T getOutputStream() {
      return this.out;
   }

}
