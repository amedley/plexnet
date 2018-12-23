package com.medleystudios.pn.util;

import java.util.ArrayList;
import java.util.List;

public class PNNestedArrayList<T extends Object> extends ArrayList<T> {

   private List<PNNestedArrayList<T>> children = new ArrayList<>();

   public PNNestedArrayList<T> addChild(PNNestedArrayList<T> child) {
      // no self-referencing
      if (child == this) {
         return this;
      }
      this.children.add(child);
      return this;
   }


   @Override
   public boolean add(T value) {
      if (value == null) return false;
      return super.add(value);
   }

   @Override
   public void add(int index, T value) {
      if (value == null) return;
      super.add(index, value);
   }

   /**
    * A call to 'nest' will return the nested elements in an order such that each parent's immediate list of elements
    * is subsequently followed by its child nests, starting with the top-most PNNestedArrayList. Override this method to
    * implement different types of sorting.
    *
    * @return Returns a unidimensional list of all the nested elements. Each parent's immediate list of elements is
    * subsequently followed by its child nests, which are subsequently followed by their child nests, and so on.
    */
   public List<T> nest() {
      List<T> nest = new ArrayList<>();
      for (int i = 0; i < this.size(); i++) {
         nest.add(this.get(i));
      }

      List<PNNestedArrayList<T>> childNest = getChildNest();
      for (int i = 0; i < childNest.size(); i++) {
         List<T> child = childNest.get(i);

         for (int j = 0; j < child.size(); j++) {
            nest.add(child.get(j));
         }
      }
      return nest;
   }

   public List<PNNestedArrayList<T>> getChildNest() {
      List<PNNestedArrayList<T>> nest = new ArrayList<>();

      for (int i = 0; i < children.size(); i++) {
         PNNestedArrayList<T> child = children.get(i);
         nest.add(child);
         List<PNNestedArrayList<T>> childChildNest = child.getChildNest();

         for (int j = 0; j < childChildNest.size(); j++) {
            nest.add(childChildNest.get(j));
         }
      }

      return nest;
   }

   public List<PNNestedArrayList<T>> getChildren() {
      return children;
   }

   public void removeAll() {
      while (size() > 0) {
         remove(size() - 1);
      }
   }

   public void cleanNest() {
      removeAll();
      while (children.size() > 0) {
         children.remove(children.size() - 1).cleanNest();
      }
   }

}
