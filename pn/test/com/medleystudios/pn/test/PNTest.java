package com.medleystudios.pn.test;

import com.medleystudios.pn.io.PNDataInputStream;
import com.medleystudios.pn.io.PNDataOutputStream;
import org.junit.jupiter.api.Test;

import static com.medleystudios.pn.util.PNUtil.u;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PNTest {

   @Test
   public void testU() {
      for (int i = 0; i < 256; i++) {
         byte b = (byte)i;
         assertEquals(u(b), i);

         System.out.println(b + ", " + i);
      }
   }

   @Test
   public void testBytePacking() {

      int b1 = 213;
      byte realByte = (byte)b1;

      int b2 = u(realByte);

      assertEquals(b1, b2);
      assertEquals((byte)b2, b1 - 256);


      byte[] s1Buffer = new byte[2];
      short s1 = -30000;
      s1Buffer[0] = (byte)((s1 >> 8) & 0xFF);
      s1Buffer[1] = (byte)((s1 >> 0) & 0xFF);
      short s1res = (short)((u(s1Buffer[0]) << 8) | (u(s1Buffer[1]) << 0));
      assertEquals(s1, s1res);


      byte[] s2Buffer = new byte[2];
      short s2 = 30000;
      s2Buffer[0] = (byte)((s2 >> 8) & 0xFF);
      s2Buffer[1] = (byte)((s2 >> 0) & 0xFF);
      short s2res = (short)((u(s2Buffer[0]) << 8) | (u(s2Buffer[1]) << 0));
      assertEquals(s2, s2res);

   }

   @Test
   public void testDataIOStreams() {
      PNDataOutputStream writer = new PNDataOutputStream();

      writer.writeByte(-200);
      writer.writeByte(-100);
      writer.writeByte(-50);
      writer.writeByte(50);
      writer.writeByte(100);
      writer.writeByte(200);

      writer.writeShort(-50000);
      writer.writeShort(-30000);
      writer.writeShort(-200);
      writer.writeShort(-100);
      writer.writeShort(100);
      writer.writeShort(200);
      writer.writeShort(30000);
      writer.writeShort(50000);

      writer.writeInt(-100000);
      writer.writeInt(-30000);
      writer.writeInt(-18);
      writer.writeInt(18);
      writer.writeInt(30000);
      writer.writeInt(100000);

      writer.writeLong((long)-1000000000 * 100);
      writer.writeLong((long)-300000);
      writer.writeLong((long)-18000);
      writer.writeLong((long)18000);
      writer.writeLong((long)300000);
      writer.writeLong((long)1000000000 * 100);

      writer.writeFloat(127598.77f);
      writer.writeFloat(8129057f);
      writer.writeFloat(10.34787f);
      writer.writeFloat(-10.34787f);
      writer.writeFloat(-8129057f);
      writer.writeFloat(-127598.77f);

      writer.writeDouble(127598.753535700d);
      writer.writeDouble(8129050074545267d);
      writer.writeDouble(10.3478712121200d);
      writer.writeDouble(-10.3478706543654360d); // will be approximated due to size
      writer.writeDouble(-8129050523507d);
      writer.writeDouble(-12759878123581.5325d); // will be approximated due to size

      writer.writeString("This is a string! It has characters!");
      writer.writeString("Another string! Yes!");




      PNDataInputStream reader = new PNDataInputStream();
      reader.reset(writer.getData());

      assertEquals(56, reader.readByte());   // (byte)-200 = 56
      assertEquals(-100, reader.readByte());
      assertEquals(-50, reader.readByte());
      assertEquals(50, reader.readByte());
      assertEquals(100, reader.readByte());
      assertEquals(-56, reader.readByte());   // (byte)200 = -56

      assertEquals(15536, reader.readShort()); // (short)-50000 = 15536
      assertEquals(-30000, reader.readShort());
      assertEquals(-200, reader.readShort());
      assertEquals(-100, reader.readShort());
      assertEquals(100, reader.readShort());
      assertEquals(200, reader.readShort());
      assertEquals(30000, reader.readShort());
      assertEquals(-15536, reader.readShort()); // (short)50000 = -15536

      assertEquals(-100000, reader.readInt());
      assertEquals(-30000, reader.readInt());
      assertEquals(-18, reader.readInt());
      assertEquals(18, reader.readInt());
      assertEquals(30000, reader.readInt());
      assertEquals(100000, reader.readInt());

      assertEquals((long)-1000000000 * 100, reader.readLong());
      assertEquals((long)-300000, reader.readLong());
      assertEquals((long)-18000, reader.readLong());
      assertEquals((long)18000, reader.readLong());
      assertEquals((long)300000, reader.readLong());
      assertEquals((long)1000000000 * 100, reader.readLong());

      assertEquals(127598.77f, reader.readFloat());
      assertEquals(8129057f, reader.readFloat());
      assertEquals(10.34787f, reader.readFloat());
      assertEquals(-10.34787f, reader.readFloat());
      assertEquals(-8129057f, reader.readFloat());
      assertEquals(-127598.77f, reader.readFloat());

      assertEquals(127598.753535700d, reader.readDouble());
      assertEquals(8129050074545267d, reader.readDouble());
      assertEquals(10.34787121212d, reader.readDouble());
      assertEquals(-10.3478706543654360d, reader.readDouble());   // will be approximated due to size
      assertEquals(-8129050523507d, reader.readDouble());
      assertEquals(-12759878123581.5325d, reader.readDouble());   // will be approximated due to size

      assertEquals("This is a string! It has characters!", reader.readString());
      assertEquals("Another string! Yes!", reader.readString());
   }

}
