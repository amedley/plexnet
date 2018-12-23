package com.medleystudios.pn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PNOptions implements Cloneable {

   protected Map<String, List<Option>> params;

   public PNOptions() {
      this.params = new HashMap<>();
   }

   public List<Option> put(String field, String[] values) {
      if (field == null) throw new IllegalArgumentException("Field is null");
      field = field.trim();
      if (field.isEmpty()) throw new IllegalArgumentException("Field is empty");
      if (values == null) values = new String[] { };

      List<Option> options = new ArrayList<>(values.length);
      for (int i = 0; i < values.length; i++) {
         String v = values[i];

         if (v == null) continue;

         v = v.trim();
         if (v.isEmpty()) continue;

         options.add(new Option().read(v));
      }
      params.put(field, options);
      return options;
   }

   public Option get(String field) {
      return getAt(field, 0);
   }

   public int getInteger(String field) throws NullPointerException {
      Option opt = get(field);
      if (opt == null) throw new NullPointerException();
      return opt.getInteger();
   }

   public long getLong(String field) throws NullPointerException {
      Option opt = get(field);
      if (opt == null) throw new NullPointerException();
      return opt.getLong();
   }

   public double getDouble(String field) throws NullPointerException {
      Option opt = get(field);
      if (opt == null) throw new NullPointerException();
      return opt.getDouble();
   }

   public float getFloat(String field) throws NullPointerException {
      Option opt = get(field);
      if (opt == null) throw new NullPointerException();
      return opt.getFloat();
   }

   public String getString(String field) throws NullPointerException {
      Option opt = get(field);
      if (opt == null) throw new NullPointerException();
      return opt.getString();
   }

   public Option getAt(String field, int i) {
      field = field.toLowerCase().trim();
      List<Option> results = getOptions(field);
      if (i < 0 || i >= results.size()) return null;
      return results.get(i);
   }

   public List<Option> getOptions(String field) {
      field = field.toLowerCase().trim();
      return params.get(field);
   }

   public PNOptions parse(String[] args) {
      List<Option> options = null;
      for (int i = 0; i < args.length; i++) {
         final String arg = args[i];   // already trimmed by the nature of "run arguments"
         final String argLower = arg.toLowerCase();

         if (arg.charAt(0) == '-') {
            int firstLetterIndex = -1;

            findLetter: for (int c = 0; c < arg.length(); c++) {
               char chLower = argLower.charAt(c);
               if (chLower >= 'a' && chLower <= 'z') {
                  firstLetterIndex = c;
                  break findLetter;
               }
            }
            if (firstLetterIndex == -1) {
               PN.softError(this, "Error at argument " + arg);
               break;
            }

            options = new ArrayList<>();
            params.put(arg.substring(firstLetterIndex), options);
         }
         else if (options != null) {
            // Construct and parse Option and add that to options
            options.add(new Option().read(arg));
         }
         else {
            PN.softError(this, "Illegal parameter usage");
            break;
         }
      }
      return this;
   }

   @Override
   public PNOptions clone() {
      PNOptions result = new PNOptions();

      Iterator<String> it = this.params.keySet().iterator();
      while (it.hasNext()) {
         String paramName = it.next();
         List<Option> paramOptions = this.params.get(paramName);

         List<Option> paramOptionsDeepCopy = new ArrayList<>(paramOptions.size());
         result.params.put(paramName, paramOptionsDeepCopy);
         for (int i = 0; i < paramOptions.size(); i++) {
            paramOptionsDeepCopy.add(paramOptions.get(i).clone());
         }
      }

      return result;
   }

   @Override
   public String toString() {
      return "PNOptions[" + this.params.toString() + "]";
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PNOptions pnOptions = (PNOptions)o;
      return Objects.equals(params, pnOptions.params);
   }

   @Override
   public int hashCode() {
      return Objects.hash(params);
   }

   public static class Option implements Cloneable {
      private String data = null;
      private int dataInt = 0;
      private long dataLong = 0;
      private double dataDouble = 0.0;
      private float dataFloat = 0.0f;

      public Option read(String data) {
         if (data == null) {
            throw new IllegalArgumentException("data must not be null");
         }
         this.data = data;

         try {
            dataLong = Long.parseLong(data);
            dataInt = Integer.parseInt(data);
            dataDouble = Double.parseDouble(data);
            dataFloat = Float.parseFloat(data);
         }
         catch (NumberFormatException e) {
            // ignore
         }
         return this;
      }

      public String getString() {
         return this.data;
      }

      public long getLong() {
         return this.dataLong;
      }

      public int getInteger() {
         return this.dataInt;
      }

      public double getDouble() {
         return this.dataDouble;
      }

      public float getFloat() {
         return this.dataFloat;
      }

      @Override
      public String toString() {
         return "PNOption[" + this.data + "]";
      }

      @Override
      public Option clone() {
         return new Option().read(this.data);
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;
         Option option = (Option)o;
         return dataInt == option.dataInt &&
            dataLong == option.dataLong &&
            Double.compare(option.dataDouble, dataDouble) == 0 &&
            Float.compare(option.dataFloat, dataFloat) == 0 &&
            Objects.equals(data, option.data);
      }

      @Override
      public int hashCode() {
         return Objects.hash(data, dataInt, dataLong, dataDouble, dataFloat);
      }
   }
}
