package com.willfp.eco.core.data.handlers;
import com.willfp.eco.core.data.keys.PersistentDataKey;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.CompletionStage;
/** Privileged native-plugin SPI, NOT an observation or consumer API. MAIN entry except factory I/O tasks.
 * Uses the already initialized MySQL handler; no credentials/new pool. Optional on other backends.
 */
public interface ProfileMutationAccess {
    interface Factory { ProfileWriteBarrier create(DataSource initializedPool,String prefix); }
    boolean supported();
    void install(Factory factory);
    CompletionStage<Void> flush(UUID player);
    CompletionStage<Void> flushAll();
    /** Does not load a missing profile, enqueue writes, or create defaults. */
    boolean publish(UUID player,Set<PersistentDataKey<?>> owned,Map<PersistentDataKey<?>,Object> committed);
}
