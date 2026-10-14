package com.willfp.eco.core.data.observation;
import com.willfp.eco.core.data.keys.PersistentDataKey;
import java.util.*;
/** Bounded typed read capability. Does not use profiles or default values. */
public interface StorageReader {
 Map<UUID, Map<PersistentDataKey<?>, Object>> read(Set<UUID> uuids, Set<PersistentDataKey<?>> keys, ReadControl control) throws Exception;
}
