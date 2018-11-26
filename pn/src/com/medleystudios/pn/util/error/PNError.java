package com.medleystudios.pn.util.error;

public class PNError {

   private Throwable throwable;
   private String message;
   private String callerClassName;

   public PNError(Throwable throwable, Object caller) {
      this(throwable, caller, null);
   }

   public PNError(String message, Object caller) {
      this(null, caller, message);
   }

   public PNError(Throwable throwable) {
      this(throwable, null, null);
   }

   public PNError(String message) {
      this(null, null, message);
   }

   public PNError(Throwable throwable, String callerClassName, String message) {
      this.throwable = throwable;
      this.callerClassName = callerClassName;
      this.message = message;
   }

   public PNError(Throwable throwable, Object caller, String message) {
      this(throwable, caller != null ? caller.getClass().getName() : null, message);
   }

   public Throwable getThrowable() {
      return this.throwable;
   }

   public String getMessage() {
      return this.message;
   }

   public String getCallerClassName() {
      return this.callerClassName;
   }

   @Override
   public String toString() {
      return "PNError[" + (callerClassName != null ? callerClassName + " " : "") + message + "]";
   }

}
