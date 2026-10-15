package com.willfp.eco.core.data.handlers

import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProfileWriteBarrierTest {
    private fun handler() = object : PersistentDataHandler("test", 1) {
        override fun getSavedUUIDs(): Set<UUID> = emptySet()
    }
    private fun key(): PersistentDataKey<Int> = mockk<PersistentDataKey<Int>>().also {
        every { it.type } returns PersistentDataKeyType.INT
    }
    private fun serializer(h: PersistentDataHandler, result: AtomicInteger) {
        PersistentDataKeyType.INT.registerSerializer(h, object : DataTypeSerializer<Int>() {
            override fun readAsync(uuid: UUID, key: PersistentDataKey<Int>): Int? = null
            override fun writeAsync(uuid: UUID, key: PersistentDataKey<Int>, value: Int) { result.set(value) }
        })
    }
    @Test fun ordinaryWriterStillPersistsAndPreparesWithoutIO() {
        val h = handler(); val calls = AtomicInteger(); serializer(h, calls)
        try {
            val prepared = h.prepareWrite(UUID.randomUUID(), key(), 7)
            assertEquals(0, calls.get())
            h.writePrepared(prepared); prepared.completion().toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertEquals(7, calls.get())
        } finally { h.shutdown() }
    }
    @Test fun originStampSurvivesQueueAndUsesGuardedReplacementOnly() {
        val h = handler(); val native = AtomicInteger(); val written = AtomicInteger(); serializer(h, native)
        var revision = 3
        val captureThread = Thread.currentThread()
        h.installProfileWriteBarrier { _, _, value ->
            assertSame(captureThread, Thread.currentThread())
            val captured = revision
            Runnable { assertNotSame(captureThread, Thread.currentThread()); written.set(captured * 100 + value as Int) }
        }
        try {
            val prepared = h.prepareWrite(UUID.randomUUID(), key(), 7)
            revision = 9
            h.writePrepared(prepared); prepared.completion().toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertEquals(307, written.get()); assertEquals(0, native.get())
            assertThrows(IllegalStateException::class.java) { h.installProfileWriteBarrier { _, _, _ -> null } }
        } finally { h.shutdown() }
    }
    @Test fun preRegistrationAndSupersededWritesCannotBypassNewFence() {
        val h = handler(); val calls = AtomicInteger(); serializer(h, calls)
        try {
            val old = h.prepareWrite(UUID.randomUUID(), key(), 10)
            val superseded = h.prepareWrite(UUID.randomUUID(), key(), 20)
            superseded.supersede()
            assertThrows(IllegalStateException::class.java) { h.writePrepared(superseded) }
            h.installProfileWriteBarrier { _, _, _ -> Runnable { calls.set(99) } }
            h.writePrepared(old)
            assertThrows(java.util.concurrent.ExecutionException::class.java) { old.completion().toCompletableFuture().get(2, TimeUnit.SECONDS) }
            assertEquals(0, calls.get())
        } finally { h.shutdown() }
    }
    @Test fun admissionFailureHappensBeforeAJobCanBeQueued() {
        val h = handler(); val calls = AtomicInteger(); serializer(h, calls)
        h.installProfileWriteBarrier { _, _, _ -> throw IllegalStateException("Profile frozen; retain original operation") }
        try {
            assertThrows(IllegalStateException::class.java) { h.prepareWrite(UUID.randomUUID(), key(), 7) }
            assertEquals(0, calls.get())
        } finally { h.shutdown() }
    }
}
