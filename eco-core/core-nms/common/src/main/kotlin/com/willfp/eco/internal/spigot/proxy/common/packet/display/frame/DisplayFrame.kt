package com.willfp.eco.internal.spigot.proxy.common.packet.display.frame

import com.willfp.eco.core.items.HashedItem
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

data class DisplayFrame(val items: Map<Byte, HashedItem>) {
    /** Visit each slot once without building changed-slot lists and sets. */
    inline fun visitSlots(
        newFrame: DisplayFrame,
        changed: (Byte) -> Unit,
        unchanged: (Byte) -> Unit
    ) {
        for ((slot, data) in newFrame.items) {
            if (items[slot]?.hash != data.hash) changed(slot) else unchanged(slot)
        }
    }

    fun getChangedSlots(newFrame: DisplayFrame): List<Byte> {
        val changes = mutableListOf<Byte>()

        for ((slot, data) in newFrame.items) {
            if (items[slot]?.hash != data.hash) {
                changes.add(slot)
            }
        }

        return changes
    }

    fun getItem(slot: Byte): ItemStack? {
        return items[slot]?.item
    }

    /**
     * The hash of the item the cached item for this slot was made from, or null if the slot
     * wasn't in this frame.
     */
    fun getHash(slot: Byte): Int? {
        return items[slot]?.hash
    }

    companion object {
        val EMPTY = DisplayFrame(emptyMap())
    }
}

private val frames = ConcurrentHashMap<UUID, DisplayFrame>()

var Player.lastDisplayFrame: DisplayFrame
    get() {
        return frames[this.uniqueId] ?: DisplayFrame.EMPTY
    }
    set(value) {
        frames[this.uniqueId] = value
    }

fun clearFrames() {
    frames.clear()
}
