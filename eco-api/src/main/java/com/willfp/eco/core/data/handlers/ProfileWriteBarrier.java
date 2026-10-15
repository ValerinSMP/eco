package com.willfp.eco.core.data.handlers;

import com.willfp.eco.core.data.keys.PersistentDataKey;
import java.util.UUID;

/** Native writer extension. Optional and inactive by default.
 * capture runs BEFORE local profile mutation, coalescing or executor submission; it MUST NOT do I/O.
 * For protected keys return the complete fenced persistence task (including native scalar changes in
 * the SAME transaction as its fence check). It replaces the ordinary serializer, never wraps a
 * serializer which commits on another connection. Throw before cache mutation when not admitted.
 * Null delegates an unowned key to the native serializer. Installed bindings are not removable:
 * removing a fence while old jobs exist would reopen an unguarded writer.
 */
@FunctionalInterface
public interface ProfileWriteBarrier {
    Runnable capture(UUID player, PersistentDataKey<?> key, Object value);
}
