package com.medleystudios.pn.policy;

import com.medleystudios.pn.PNOptions;
import com.medleystudios.pn.util.error.PNError;
import com.medleystudios.pn.util.error.PNErrorStorable;
import com.medleystudios.pn.util.error.PNErrorStorage;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface PNPolicy {

   /**
    * @return Returns a {@link PolicyResolver}, on which a call to {@link PolicyResolver#didSucceed()} will return
    * true on success, false otherwise in which case {@link PolicyResolver#getErrorStorage()} will contain an error
    * with the a message stating the reason why it failed to enforce.
    */
   CompletableFuture<PolicyResolver> enforce(Map<String, Object> options);

   /**
    * @return Returns a {@link PolicyResolver}, on which a call to {@link PolicyResolver#didSucceed()} will return
    * true on success, false otherwise in which case {@link PolicyResolver#getErrorStorage()} will contain an error
    * with the a message stating the reason why we failed to abide.
    */
   CompletableFuture<PolicyResolver> abide(Map<String, Object> options);

   class PolicyResolver implements PNErrorStorable {
      private PNErrorStorage errorStorage = new PNErrorStorage();
      private boolean success;

      public PolicyResolver() {
         this.success = true;
      }

      public PolicyResolver(String errorMessage) {
         this.errorStorage.add(new PNError(errorMessage));
         this.success = false;
      }

      public boolean didSucceed() {
         return this.success;
      }

      @Override
      public PNErrorStorage getErrorStorage() {
         return this.errorStorage;
      }

   }

}
