package com.willfp.eco.internal.spigot.proxy.common.packet.display.frame

import com.willfp.eco.core.items.HashedItem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class DisplayFrameTests {
    private fun frame(vararg hashes: Int): DisplayFrame = DisplayFrame(
        hashes.mapIndexed { slot, hash ->
            slot.toByte() to mockk<HashedItem> { every { getHash() } returns hash }
        }.toMap()
    )

    private fun assertVisits(old: DisplayFrame, new: DisplayFrame, expected: List<Int>) {
        val changed = mutableListOf<Int>()
        val unchanged = mutableListOf<Int>()
        old.visitSlots(new, { changed.add(it.toInt()) }, { unchanged.add(it.toInt()) })
        assertEquals(expected, changed)
        assertEquals(new.items.keys.map { it.toInt() }.filterNot { it in expected }, unchanged)
        assertEquals(old.getChangedSlots(new).map { it.toInt() }, changed)
    }

    @Test fun initialFrameDisplaysEverySlot() = assertVisits(DisplayFrame.EMPTY, frame(1, 2, 3), listOf(0, 1, 2))
    @Test fun unchangedFrameReusesEverySlot() = assertVisits(frame(1, 2, 3), frame(1, 2, 3), emptyList())
    @Test fun changedAndAddedSlotsRefresh() = assertVisits(frame(1, 2), frame(1, 9, 3), listOf(1, 2))
    @Test fun removedSlotsAreNotVisited() = assertVisits(frame(1, 2, 3), frame(1), emptyList())
    @Test fun expiryRefreshesIdenticalItems() = assertVisits(DisplayFrame.EMPTY, frame(1, 1), listOf(0, 1))

    @Test fun emptyInventoryDoesNotVisitRemovedSlots() = assertVisits(frame(1, 2), DisplayFrame.EMPTY, emptyList())

    @Test fun typicalPlayerInventoryRefreshesOnlyChangedSlots() {
        val old = IntArray(46) { it }
        val new = old.copyOf().apply { this[0] = 100; this[40] = 101; this[45] = 102 }
        assertVisits(frame(*old), frame(*new), listOf(0, 40, 45))
    }

    @Test fun repeatedHashValuesRemainSlotSpecific() = assertVisits(frame(1, 2, 1), frame(2, 1, 1), listOf(0, 1))

    @Test fun randomizedFramesMatchPreviousAlgorithm() {
        val random = Random(20261004)
        // Reuse mocks to keep this deterministic comparison inexpensive.
        val pool = (0..7).map { hash -> mockk<HashedItem> { every { getHash() } returns hash } }
        repeat(500) {
            fun nextFrame() = DisplayFrame((0 until random.nextInt(0, 47)).associate {
                it.toByte() to pool[random.nextInt(pool.size)]
            })
            val old = nextFrame()
            val new = nextFrame()
            assertVisits(old, new, old.getChangedSlots(new).map { it.toInt() })
        }
    }
}
