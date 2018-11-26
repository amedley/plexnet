package com.medleystudios.pn.io;

import java.io.IOException;

public interface PNStreamable<In extends PNInputStreamReader, Out extends PNOutputStreamWriter> {

   void read(In reader) throws IOException;
   void write(Out writer) throws IOException;

}
