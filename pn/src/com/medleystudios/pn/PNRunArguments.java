package com.medleystudios.pn;

import com.medleystudios.pn.util.PNUtil;

import java.util.ArrayList;
import java.util.List;

public class PNRunArguments {

   protected List<RunArgument> arguments;

   public PNRunArguments() {
      this.arguments = new ArrayList<>();
   }

   // This should be the only setter
   /**
    * Reads the array of run arguments which then get parsed and validated into usable types
    * @param args The string args which get parsed into {@link RunArgument} values
    * @return Returns the parsed arguments
    */
   public PNRunArguments read(String[] args) {
      for (int i = 0; i < this.arguments.size(); i++) {
         this.arguments.get(i).read(args);
      }
      return this;
   }

   public RunArgument addRunArgument(String field, RunArgument.ArgumentType type) {
      RunArgument arg = new RunArgument(field, type, arguments.size());
      this.arguments.add(arg);
      return arg;
   }

   @Override
   public String toString() {
      return "[" + this.getClass().getName() + " " + PNUtil.toString(this.arguments) + "]";
   }

   public static class RunArgument {

      public enum ArgumentType {
         STRING,
         INT,
         DOUBLE,
         FLOAT,
      }

      private String field;
      private int location;
      private final ArgumentType type;

      private String data = null;
      private int dataInt = 0;
      private double dataDouble = 0.0;
      private float dataFloat = 0.0f;

      public RunArgument(String field, ArgumentType type, int location) {
         this.field = field;
         this.location = location;
         this.type = type;
      }

      public void read(String[] args) {
         if (location < 0 || location >= args.length) {
            PN.fatalError(new RuntimeException(), this, "Unable to locate run argument: " + this + ". Args with length " + args.length + ": [" + String.join(", " +
               "", args) + "]");
            return;
         }
         data = args[location];
         testDataExists();

         try {
            dataInt = Integer.parseInt(data);
         }
         catch (NumberFormatException e) {
            testParseFailedOnCriticalType(ArgumentType.INT, e);
         }

         try {
            dataDouble = Double.parseDouble(data);
         }
         catch (NumberFormatException e) {
            testParseFailedOnCriticalType(ArgumentType.DOUBLE, e);
         }

         try {
            dataFloat = Float.parseFloat(data);
         }
         catch (NumberFormatException e) {
            testParseFailedOnCriticalType(ArgumentType.FLOAT, e);
         }

      }

      private void testParseFailedOnCriticalType(ArgumentType argType, RuntimeException e) {
         if (this.type == argType) {
            PN.fatalError(e, this, "Failed to parse run argument with critical type: " + this);
         }
      }

      public String getString() {
         testDataExists();
         return this.data;
      }

      public int getInteger() {
         testDataExists();
         return this.dataInt;
      }

      public double getDouble() {
         testDataExists();
         return this.dataDouble;
      }

      public float getFloat() {
         testDataExists();
         return this.dataFloat;
      }

      private void testDataExists() {
         if (data == null) {
            throw new NullPointerException("Data does not exist for run argument: " + this);
         }
      }
      @Override
      public String toString() {
         return "[" + this.getClass().getName() + " field: " + field + ", type: " + type + ", location: " + location + ", data: " + data + "]";
      }
   }
}
