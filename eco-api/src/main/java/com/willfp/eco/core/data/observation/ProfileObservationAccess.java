package com.willfp.eco.core.data.observation;
import com.willfp.eco.core.data.keys.PersistentDataKey;
import java.util.*;
/** Framework observation, never a load or default read. Caller must use its own registered keys. */
public interface ProfileObservationAccess {
 default StorageReader storageReader(){return null;}
 CachedProfileValues peek(UUID uuid, Set<PersistentDataKey<?>> keys);
}
