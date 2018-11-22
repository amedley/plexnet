package com.medleystudios.pn.util;

import com.medleystudios.pn.PN;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

public class PNUtil {

   public static <T extends Object> String toString(List<T> list, String indent) {
      String result = "[";

      for (int i = 0; i < list.size(); i++) {
         Object value = list.get(i);
         String valueString;

         if (value instanceof List) {
            valueString = toString((List<Object>)value, indent + "\t");
         }
         else if (value instanceof Object[]) {
            // It's worth keeping in mind that a multidimentional primitive type is of type Object[]
            // So if we have a child value with something like int[][], that will process here
            valueString = toString((Object[])value, indent + "\t");
         }
         else if (value instanceof boolean[]) {
            valueString = "\t" + Arrays.toString((boolean[])value);
         }
         else if (value instanceof byte[]) {
            valueString = "\t" + Arrays.toString((byte[])value);
         }
         else if (value instanceof short[]) {
            valueString = "\t" + Arrays.toString((short[])value);
         }
         else if (value instanceof char[]) {
            valueString = "\t" + Arrays.toString((char[])value);
         }
         else if (value instanceof int[]) {
            valueString = "\t" + Arrays.toString((int[])value);
         }
         else if (value instanceof long[]) {
            valueString = "\t" + Arrays.toString((long[])value);
         }
         else if (value instanceof float[]) {
            valueString = "\t" + Arrays.toString((float[])value);
         }
         else if (value instanceof double[]) {
            valueString = "\t" + Arrays.toString((double[])value);
         }
         else {
            valueString = "" + value;
         }

         result += "\n\t" + indent + valueString + ",";
      }

      return result + "\n" + indent + "]";
   }

   public static <T extends Object> String toString(List<T> list) {
      return toString(list, "");
   }

   public static <T extends Object> String toString(T[] array, String indent) {
      List<T> list = new ArrayList<>();

      for (int i = 0; i < array.length; i++) {
         list.add(array[i]);
      }

      return toString(list, indent);
   }

   public static <T extends Object> String toString(T[] array) {
      return toString(array, "");
   }

}
