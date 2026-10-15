package com.willfp.eco.core.data.handlers;

import com.willfp.eco.core.data.keys.PersistentDataKey;
import com.willfp.eco.core.registry.Registrable;
import com.willfp.eco.core.tuples.Pair;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles persistent data.
 */
public abstract class PersistentDataHandler implements Registrable {
    /**
     * The id.
     */
    private final String id;

    /**
     * The executor.
     */
    public static final int DEFAULT_THREADS = 8;

    private final ExecutorService executor;

    private volatile ProfileWriteBarrier profileWriteBarrier;
    private final java.util.concurrent.ConcurrentMap<UUID, java.util.Set<PreparedProfileWrite>> pendingWrites = new ConcurrentHashMap<>();
    public final CompletionStage<Void> awaitProfileWrites(UUID player) {
        var snapshot = new java.util.ArrayList<CompletableFuture<?>>();
        pendingWrites.forEach((uuid,writes) -> { if(player==null||player.equals(uuid))writes.forEach(w -> snapshot.add(w.completion().toCompletableFuture())); });
        return CompletableFuture.allOf(snapshot.toArray(CompletableFuture[]::new)).minimalCompletionStage();
    }

    /** One immutable binding per handler. Bootstrap must register before accepting owned mutations. */
    public final synchronized void installProfileWriteBarrier(ProfileWriteBarrier barrier) {
        java.util.Objects.requireNonNull(barrier);
        if (profileWriteBarrier != null) throw new IllegalStateException("Profile writer already bound");
        profileWriteBarrier = barrier;
    }

    /** Capture origin context on the calling thread, before changing the profile cache. No SQL here. */
    public final <T> PreparedProfileWrite prepareWrite(UUID uuid, PersistentDataKey<T> key, T value) {
        java.util.Objects.requireNonNull(uuid);java.util.Objects.requireNonNull(key);java.util.Objects.requireNonNull(value);
        ProfileWriteBarrier captured = profileWriteBarrier;
        Runnable guarded = captured == null ? null : captured.capture(uuid, key, value);
        DataTypeSerializer<T> serializer = key.getType().getSerializer(this);
        Runnable persist = guarded != null ? guarded : () -> serializer.writeAsync(uuid, key, value);
        return new PreparedProfileWrite(this, uuid, () -> {
            // Pre-registration jobs must not bypass a newly installed barrier.
            // Do not recapture a session/revision on this I/O thread.
            if (captured != profileWriteBarrier) throw new IllegalStateException("Writer binding changed; reconcile original mutation");
            persist.run();
        });
    }

    public final void writePrepared(PreparedProfileWrite write) {
        java.util.Objects.requireNonNull(write);
        if(write.owner()!=this)throw new IllegalArgumentException("Wrong profile handler");
        pendingWrites.compute(write.player(), (id,set) -> {
            if(set==null)set=ConcurrentHashMap.newKeySet();set.add(write);return set;
        });
        write.completion().whenComplete((v,e) -> pendingWrites.computeIfPresent(write.player(), (id,set) -> {
            set.remove(write);return set.isEmpty()?null:set;
        }));
        write.dispatch(this, executor);
    }

    /**
     * Create a new persistent data handler.
     *
     * @param id The id.
     */
    protected PersistentDataHandler(@NotNull final String id) {
        this(id, DEFAULT_THREADS);
    }

    protected PersistentDataHandler(@NotNull final String id, final int threads) {
        this.id = id;
        this.executor = Executors.newFixedThreadPool(Math.max(1, threads));
    }

    /**
     * Get all UUIDs with saved data.
     * <p>
     * This is a blocking operation.
     *
     * @return All saved UUIDs.
     */
    public abstract Set<UUID> getSavedUUIDs();

    /**
     * Save to disk.
     * <p>
     * If write commits to disk, this method does not need to be overridden.
     * <p>
     * This method is called asynchronously.
     */
    protected void doSave() {
        // Save to disk
    }

    /**
     * If the handler should autosave.
     *
     * @return If the handler should autosave.
     */
    public boolean shouldAutosave() {
        return true;
    }

    /**
     * Save the data.
     */
    public final void save() {
        executor.submit(this::doSave);
    }

    /**
     * Read a key from persistent data on the calling thread; blocks until completed.
     *
     * @param uuid The uuid.
     * @param key  The key.
     * @param <T>  The type of the key.
     * @return The value, or null if not found.
     */
    @Nullable
    public final <T> T read(@NotNull final UUID uuid,
                            @NotNull final PersistentDataKey<T> key) {
        try {
            return key.getType().getSerializer(this).readAsync(uuid, key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Write a key to persistent data.
     *
     * @param uuid  The uuid.
     * @param key   The key.
     * @param value The value.
     * @param <T>   The type of the key.
     */
    public final <T> void write(@NotNull final UUID uuid,
                                @NotNull final PersistentDataKey<T> key,
                                @NotNull final T value) {
        writePrepared(prepareWrite(uuid, key, value));
    }

    /**
     * Serialize profile.
     *
     * @param uuid The uuid to serialize.
     * @param keys The keys to serialize.
     * @return The serialized data.
     */
    @NotNull
    public final SerializedProfile serializeProfile(@NotNull final UUID uuid,
                                                    @NotNull final Set<PersistentDataKey<?>> keys) {
        Map<PersistentDataKey<?>, CompletableFuture<Object>> futures = keys.stream()
                .collect(Collectors.toMap(
                        key -> key,
                        key -> CompletableFuture.supplyAsync(() -> read(uuid, key), executor)
                ));

        Map<PersistentDataKey<?>, Object> data = futures.entrySet().stream()
                .map(entry -> new Pair<PersistentDataKey<?>, Object>(entry.getKey(), entry.getValue().join()))
                .filter(entry -> entry.getSecond() != null)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));

        return new SerializedProfile(uuid, data);
    }

    /**`
     * Load profile data.
     *
     * @param profile The profile.
     */
    @SuppressWarnings("unchecked")
    public final void loadSerializedProfile(@NotNull final SerializedProfile profile) {
        for (Map.Entry<PersistentDataKey<?>, Object> entry : profile.data().entrySet()) {
            PersistentDataKey<?> key = entry.getKey();
            Object value = entry.getValue();

            // This cast is safe because the data is serialized
            write(profile.uuid(), (PersistentDataKey<? super Object>) key, value);
        }
    }

    /**
     * Save and shutdown the handler.
     *
     * @throws InterruptedException If the writes could not be awaited.
     */
    public final void shutdown() throws InterruptedException {
        doSave();

        if (executor.isShutdown()) {
            return;
        }

        executor.shutdown();
        while (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
            // Wait
        }
    }

    @Override
    @NotNull
    public final String getID() {
        return id;
    }
}
