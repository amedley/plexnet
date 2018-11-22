package com.medleystudios.pn.server;

import com.medleystudios.pn.PNRunArguments;

public class PNServerRunArguments extends PNRunArguments {

   private PNRunArguments.RunArgument host;
   private PNRunArguments.RunArgument port;
   private PNRunArguments.RunArgument maxConnections;

   public PNServerRunArguments() {
      super();
      this.host = this.addRunArgument("HOST", RunArgument.ArgumentType.STRING);
      this.port = this.addRunArgument("PORT", RunArgument.ArgumentType.INT);
      this.maxConnections = this.addRunArgument("MAX_CONNECTIONS", RunArgument.ArgumentType.INT);
   }

   public String getHost() {
      return this.host.getString();
   }

   public int getPort() {
      return this.port.getInteger();
   }

   public int getMaxConnections() {
      return this.maxConnections.getInteger();
   }

}
