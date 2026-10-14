package com.willfp.eco.core.data.observation;
import com.willfp.eco.core.data.keys.PersistentDataKey;
import java.util.*;
/** Presence is independent of defaults; values must be immutable scalars. */
public record CachedProfileValues(boolean loaded, Map<PersistentDataKey<?>, Object> values) {
 public CachedProfileValues { values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
 public static CachedProfileValues absent() { return new CachedProfileValues(false, Map.of()); }
}
