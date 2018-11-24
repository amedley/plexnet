package com.medleystudios.pn.util;

public class PNErrors {

   public static class ExceededMaxCapacity extends RuntimeException {

      public ExceededMaxCapacity(String message) {
         super(message);
      }

      public ExceededMaxCapacity() {
         this("Exceeded max capacity");
      }

   }

}
