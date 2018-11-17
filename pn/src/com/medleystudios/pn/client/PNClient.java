package com.medleystudios.pn.client;

import com.medleystudios.pn.PNUtil;

import java.io.IOException;
import java.net.Socket;

public class PNClient {

   private final PNClientRunArguments runArguments;

   private String host;
   private int port;

   private PNClient(PNClientRunArguments runArguments) {
      this.runArguments = runArguments;

      PNUtil.log(this, "" + runArguments);

      // Process run arguments
      this.host = this.runArguments.getHost();
      this.port = this.runArguments.getPort();

      PNUtil.log(this, "Initializing client with connection endpoint " + this.host + ":" + this.port);

      Socket test = null;
      try {
         test = new Socket(host, port);
         PNUtil.log("LOL GOT A CONNECTION DUDE: " + test);
      }
      catch (IOException e) {
         e.printStackTrace();
      }

      try {
         test.close();
      }
      catch (IOException e) {
         e.printStackTrace();
      }

   }

   public PNClient(String[] args) {
      this((PNClientRunArguments)(new PNClientRunArguments().read(args)));
   }

   public static void main(String[] args) {
      if (args.length > 0) {
         new PNClient(args);
      }
      else {
         new PNClient(new String[] {
            "localhost",
            "3000",
         });
      }
   }

}
