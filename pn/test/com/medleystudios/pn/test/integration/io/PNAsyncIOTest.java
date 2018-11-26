package com.medleystudios.pn.test.integration.io;

import com.medleystudios.pn.PN;
import com.medleystudios.pn.io.PNAsyncBufferedInputStream;
import com.medleystudios.pn.io.PNAsyncBufferedOutputStream;
import com.medleystudios.pn.io.PNInputStreamReader;
import com.medleystudios.pn.io.PNOutputStreamWriter;
import com.medleystudios.pn.test.PNTestHelper;
import com.medleystudios.pn.test.integration.PNIntegrationTestHelper;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PNAsyncIOTest {

   public static final String READ_WRITE_FILE_PATH = PNIntegrationTestHelper.IO_TEST_FILES_DIR + "/readwrite.txt";

   @Test
   public void testReadWrite() {
      try {
         FileOutputStream fos = new FileOutputStream(READ_WRITE_FILE_PATH);
         PNAsyncBufferedOutputStream out = PNAsyncBufferedOutputStream.start(fos, PNTestHelper.ASYNC_RUN_DELAY);
         PNOutputStreamWriter<PNAsyncBufferedOutputStream> writer = new PNOutputStreamWriter<>(out);
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

         assertTrue(230 > out.getOutputTotal());
         assertTrue(out.getWriteTotal() > out.getOutputTotal());
         PN.sleep(PNTestHelper.ASYNC_WAIT_TIME);
         assertEquals(230, out.getOutputTotal());


         FileInputStream fis = new FileInputStream(READ_WRITE_FILE_PATH);
         PNAsyncBufferedInputStream in = PNAsyncBufferedInputStream.start(fis, PNTestHelper.ASYNC_RUN_DELAY);
         PNInputStreamReader<PNAsyncBufferedInputStream> reader = new PNInputStreamReader<>(in);


         assertTrue(230 > in.getInputTotal());
         PN.sleep(PNTestHelper.ASYNC_WAIT_TIME);
         assertTrue(in.getInputTotal() > in.getReadTotal());
         assertEquals(230, in.getInputTotal());


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

         out.close(false);
         in.close(false);
         fos.close();
         fis.close();

      }
      catch (FileNotFoundException e) {
         e.printStackTrace();
      }
      catch (IOException e) {
         e.printStackTrace();
      }
   }

}
