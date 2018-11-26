package com.medleystudios.pn.util.error;

import com.medleystudios.pn.util.PNNestedArrayList;

import java.util.List;

public class PNErrorStorage extends PNNestedArrayList<PNError> {

   // TODO: Add various levels of priority to PNError and then handle the varying priorities in PNErrorStorage

   public PNError getTopError() {
      List<PNError> nest = this.nest();
      if (nest.size() == 0) return null;
      return nest.get(0);
   }

}
