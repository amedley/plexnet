package com.medleystudios.pn;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class PN {

   public static final TimeZone PN_UTC = TimeZone.getTimeZone("UTC");

   public static String getThrowableLog(Throwable e) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);

      String result = sw.toString();
      try {
         sw.close();
         pw.close();
      }
      catch (IOException e1) {
         return "Unable to read Throwable information.";
      }
      return result;
   }

   /**
    * Create's a {@link Date} from an ISO8601-formatted string. The date is converted from UTC to the Client TZ.
    *
    * @param iso8601 The UTC date in ISO8601 format
    * @return A date representing the ISO8601 time, converted to the Client TZ
    */
   public static Date dateFromISO(String iso8601) {
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      df.setTimeZone(PN_UTC);
      try {
         return df.parse(iso8601);
      }
      catch (Exception e) {
         return null;
      }
   }

   /**
    * @return The ISO8601-formatted string representing the current date in UTC.
    */
   public static String nowISO() {
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      df.setTimeZone(PN_UTC);
      return df.format(new Date());
   }

   public static void log(String tag, String message, PrintStream out) {
      String timestamp = PN.nowISO();
      out.println(tag + ": " + timestamp + " " + message);
   }

   public static void log(String tag, Object from, String message, PrintStream out) {
      log(tag, from.getClass().getName() + " " + message, out);
   }

   public static void log(String message) {
      // Until we redirect to a file, just write to System.out
      log("LOG", message, System.out);
   }

   public static void log(Object from, String message) {
      // Until we redirect to a file, just write to System.out
      log("LOG", from, message, System.out);
   }

   public static void error(Throwable t, String message) {
      // Until we redirect to a file, just write to System.out
      // The reason we are not redirecting to System.err is because the first println goes on the same line as
      // System.out's current line in the console
      log("ERROR", message, System.out);
      t.printStackTrace();
   }

   public static void error(Throwable t, Object from, String message) {
      // Until we redirect to a file, just write to System.out
      // The reason we are not redirecting to System.err is because the first println goes on the same line as
      // System.out's current line in the console
      log("ERROR", from, message, System.out);
      t.printStackTrace();
   }

   public static void fatalError(Throwable t, String message) {
      error(t, message);
      System.exit(1);
   }

   public static void fatalError(Throwable t, Object from, String message) {
      error(t, from, message);
      System.exit(1);
   }
}
