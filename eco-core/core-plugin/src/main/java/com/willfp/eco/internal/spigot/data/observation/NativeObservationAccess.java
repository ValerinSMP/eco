package com.willfp.eco.internal.spigot.data.observation;
import com.willfp.eco.core.data.observation.*;
import com.willfp.eco.core.data.keys.PersistentDataKey;
import com.willfp.eco.internal.spigot.data.profiles.ProfileHandler;
import java.util.*;
public final class NativeObservationAccess implements ProfileObservationAccess {
 private final ProfileHandler handler;
 public NativeObservationAccess(ProfileHandler handler) { this.handler=handler; }
 public StorageReader storageReader(){
  var active=handler.getDefaultHandler();
  return active instanceof com.willfp.eco.internal.spigot.data.handlers.impl.MySQLPersistentDataHandler mysql?mysql.getObservationReader():null;
 }
 public CachedProfileValues peek(UUID uuid, Set<PersistentDataKey<?>> keys) {
  var profile=handler.peekPlayerProfile(uuid);
  return profile==null?CachedProfileValues.absent():new CachedProfileValues(true,profile.peekCachedValues(keys));
 }
}
