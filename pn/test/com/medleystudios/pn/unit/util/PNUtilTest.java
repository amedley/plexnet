package com.medleystudios.pn.unit.util;

import org.junit.jupiter.api.Test;
import static com.medleystudios.pn.util.PNUtil.u;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PNUtilTest {

   @Test
   public void testU() {
      for (int i = 0; i < 256; i++) {
         byte b = (byte)i;
         assertEquals(u(b), i);
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

}
