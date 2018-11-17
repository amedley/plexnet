package com.medleystudios.pn.client;

import com.medleystudios.pn.PNRunArguments;

public class PNClientRunArguments extends PNRunArguments {

   private PNRunArguments.RunArgument host;
   private PNRunArguments.RunArgument port;

   public PNClientRunArguments() {
      super();
      this.host = this.addRunArgument("HOST", RunArgument.ArgumentType.STRING);
      this.port = this.addRunArgument("PORT", RunArgument.ArgumentType.INT);
   }

   public String getHost() {
      return this.host.getString();
   }

   public int getPort() {
      return this.port.getInteger();
   }
}
