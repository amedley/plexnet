package com.medleystudios.pn.server;

import com.medleystudios.pn.PNRunArguments;

public class PNServerRunArguments extends PNRunArguments {

   private PNRunArguments.RunArgument port;

   public PNServerRunArguments() {
      super();
      this.port = this.addRunArgument("PORT", RunArgument.ArgumentType.INT);
   }

   public int getPort() {
      return this.port.getInteger();
   }

}
