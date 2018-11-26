package com.medleystudios.pn;

import com.medleystudios.pn.util.error.PNError;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class PN {

   public static final TimeZone PN_UTC = TimeZone.getTimeZone("UTC");

   private static PrintStream printOutput = System.out;

   public static PrintStream getPrintOutput() {
      return printOutput;
   }

   public static void setPrintOutput(PrintStream printStream) {
      if (printStream == null) throw new IllegalArgumentException("printStream null");
      printOutput = printStream;
   }

   private static long currentLogID = 0;
   public static synchronized long nextLogID() {
      return currentLogID++;
   }

   public static String getThrowableLog(Throwable e) {
      if (e == null) return "";

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
      if (iso8601 == null) {
         throw new IllegalArgumentException("iso8601");
      }
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
      return toISO(new Date());
   }

   /**
    * @return The ISO8601-formatted string representing the date object in UTC
    */
   public static String toISO(Date date) {
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      df.setTimeZone(PN_UTC);
      return df.format(date);
   }

   private static void baseLog(String tag, String message, long logID) {
      if (message == null) message = "";
      String timestamp = PN.nowISO();
      printOutput.println(tag + ": " + timestamp + " L" + logID + " " + message);
   }

   private static void baseLog(String tag, Object caller, String message, long logID) {
      if (message == null) message = "";
      baseLog(tag, (caller != null ? caller.getClass().getName() + " " : "") + message, logID);
   }

   private static void baseLog(String tag, String callerClassName, String message, long logID) {
      if (message == null) message = "";
      baseLog(tag, (callerClassName != null ? callerClassName + " " : "") + message, logID);
   }

   private static void baseLog(String tag, String message) {
      if (message == null) message = "";
      baseLog(tag, message, nextLogID());
   }

   private static void baseLog(String tag, Object caller, String message) {
      if (message == null) message = "";
      baseLog(tag, caller, message, nextLogID());
   }

   private static void baseLog(String tag, String callerClassName, String message) {
      if (message == null) message = "";
      baseLog(tag, (callerClassName != null ? callerClassName + " " : "") + message, nextLogID());
   }



   public static void info(String message, long logID) {
      if (message == null) message = "";
      baseLog("INFO", message, logID);
   }

   public static void info(Object caller, String message, long logID) {
      if (message == null) message = "";
      baseLog("INFO", (caller != null ? caller.getClass().getName() + " " : "") + message, logID);
   }

   public static void info(String callerClassName, String message, long logID) {
      if (message == null) message = "";
      baseLog("INFO", (callerClassName != null ? callerClassName + " " : "") + message, logID);
   }

   public static void info(String message) {
      if (message == null) message = "";
      baseLog("INFO", message, nextLogID());
   }

   public static void info(Object caller, String message) {
      if (message == null) message = "";
      baseLog("INFO", caller, message, nextLogID());
   }

   public static void info(String callerClassName, String message) {
      if (message == null) message = "";
      baseLog("INFO", (callerClassName != null ? callerClassName + " " : "") + message, nextLogID());
   }



   public static void softError(String message, long logID) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      baseLog("SOFT ERROR", message, logID);
   }

   public static void softError(Object caller, String message, long logID) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      baseLog("SOFT ERROR", caller, message, logID);
   }

   public static void softError(String callerClassName, String message, long logID) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      baseLog("SOFT ERROR", (callerClassName != null ? callerClassName + " " : "") + message, logID);
   }

   public static void softError(String message) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      baseLog("SOFT ERROR", message, nextLogID());
   }

   public static void softError(Object caller, String message) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      baseLog("SOFT ERROR", caller, message, nextLogID());
   }

   public static void softError(String callerClassName, String message) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      baseLog("SOFT ERROR", (callerClassName != null ? callerClassName + " " : "") + message, nextLogID());
   }



   public static void errorStorage(PNErrorStorage errorStorage, Object caller, String message, long logID) {
      if (message == null) message = "";
      List<PNError> errors = errorStorage.nest();
      for (int i = 0; i < errors.size(); i++) {
         PNError error = errorStorage.get(i);
         String errorMessageLog = error.getMessage() != null ? " " + error.getMessage() : "";
         String errorCallerClassNameLog = error.getCallerClassName() != null ? " " + error.getCallerClassName() : "";
         message += "\n[Error storage " + i + "]" + errorCallerClassNameLog + errorMessageLog + "\n"
            + PN.getThrowableLog(error.getThrowable());
      }

      baseLog("ERROR STORAGE", caller, message, logID);
   }

   public static void errorStorage(PNErrorStorage errorStorage, String callerClassName, String message, long logID) {
      baseLog("ERROR STORAGE", (callerClassName != null ? callerClassName + " " : "") + message, logID);
   }

   public static void errorStorage(PNErrorStorage errorStorage, String message, long logID) {
      errorStorage(errorStorage, null, message, logID);
   }

   public static void errorStorage(PNErrorStorage errorStorage, Object caller, String message) {
      errorStorage(errorStorage, caller, message, nextLogID());
   }

   public static void errorStorage(PNErrorStorage errorStorage, String message) {
      errorStorage(errorStorage, null, message, nextLogID());
   }

   public static void errorStorage(PNErrorStorage errorStorage, String callerClassName, String message) {
      errorStorage(errorStorage, (callerClassName != null ? callerClassName + " " : "") + message, nextLogID());
   }



   public static void error(Throwable t, String message, long logID) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      // The reason we are not redirecting to System.err is because the first println goes on the same line as
      // System.out's current line in the console
      baseLog("ERROR", message + "\n" + PN.getThrowableLog(t), logID);
   }

   public static void error(Throwable t, Object caller, String message, long logID) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      // The reason we are not redirecting to System.err is because the first println goes on the same line as
      // System.out's current line in the console
      baseLog("ERROR", caller, message + "\n" + PN.getThrowableLog(t), logID);
   }

   public static void error(Throwable t, String callerClassName, String message, long logID) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      // The reason we are not redirecting to System.err is because the first println goes on the same line as
      // System.out's current line in the console
      baseLog("ERROR", (callerClassName != null ? callerClassName + " " : "") + message + "\n" + PN.getThrowableLog(t), logID);
   }

   public static void error(PNError error, long logID) {
      if (error == null) {
         error(null, null, null, logID);
      }
      else {
         error(error.getThrowable(), error.getCallerClassName(), error.getMessage(), logID);
      }
   }

   public static void error(Throwable t, String message) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      // The reason we are not redirecting to System.err is because the first println goes on the same line as
      // System.out's current line in the console
      error(t, message, nextLogID());
   }

   public static void error(Throwable t, Object caller, String message) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      // The reason we are not redirecting to System.err is because the first println goes on the same line as
      // System.out's current line in the console
      error(t, caller, message, nextLogID());
   }

   public static void error(Throwable t, String callerClassName, String message) {
      if (message == null) message = "";
      // Until we redirect to a file, just write to System.out
      // The reason we are not redirecting to System.err is because the first println goes on the same line as
      // System.out's current line in the console
      error(t, (callerClassName != null ? callerClassName + " " : "") + message, nextLogID());
   }

   public static void error(PNError error) {
      error(error, nextLogID());
   }

   public static PNError storeError(Throwable t, PNErrorStorable caller, String message) {
      PNError error = new PNError(t, caller, message);
      caller.getErrorStorage().add(error);
      return error;
   }

   public static PNError storeError(Throwable t, Object caller, PNErrorStorable storable, String message) {
      PNError error = new PNError(t, caller, message);
      storable.getErrorStorage().add(error);
      return error;
   }

   public static PNError storeError(Throwable t, String callerClassName, PNErrorStorable storable, String message) {
      PNError error = new PNError(t, callerClassName, message);
      storable.getErrorStorage().add(error);
      return error;
   }

   public static PNError storeAndLogError(Throwable t, PNErrorStorable caller, String message) {
      PNError error = new PNError(t, caller, message);
      caller.getErrorStorage().add(error);
      PN.error(error);
      return error;
   }


   public static void fatalError(Throwable t, String message) {
      if (message == null) message = "";
      error(t, message);
      System.exit(1);
   }

   public static void fatalError(Throwable t, Object caller, String message) {
      if (message == null) message = "";
      error(t, caller, message);
      System.exit(1);
   }

   public static void fatalError(Throwable t, String callerClassName, String message) {
      if (message == null) message = "";
      error(t, (callerClassName != null ? callerClassName + " " : "") + message);
      System.exit(1);
   }

   public static void fatalError(PNError error) {
      if (error == null) {
         fatalError(null, null);
      }
      else {
         fatalError(error.getThrowable(), error.getMessage());
      }
   }


   public static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      }
      catch (InterruptedException e) {
         PN.error(e, "Sleep failed!");
      }
   }

   public static void sleep(long millis, int nano) {
      try {
         Thread.sleep(millis, nano);
      }
      catch (InterruptedException e) {
         PN.error(e, "Sleep failed!");
      }
   }
}
