package com.willfp.eco.internal.spigot.data.profiles

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.data.keys.PersistentDataKey
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*
The profile writer exists as an optimization to batch writes to the database.

This is necessary because values frequently change multiple times per tick,
and we don't want to write to the database every time a value changes.

Instead, we only commit the last value that was set every interval (default 1 tick).
 */


class ProfileWriter(
    private val plugin: EcoPlugin,
    private val handler: ProfileHandler
) {
    private val saveInterval = plugin.configYml.getInt("save-interval").toLong()
    private val autosaveInterval = plugin.configYml.getInt("autosave-interval").toLong()
    private val valuesToWrite = ConcurrentHashMap<WriteRequest<*>, com.willfp.eco.core.data.handlers.PreparedProfileWrite>()

    fun <T : Any> write(uuid: UUID, key: PersistentDataKey<T>, value: T) {
        writePrepared(uuid, key, prepareWrite(uuid, key, value))
    }

    fun <T : Any> prepareWrite(uuid: UUID, key: PersistentDataKey<T>, value: T): com.willfp.eco.core.data.handlers.PreparedProfileWrite {
        val dataHandler = if (key.isSavedLocally) handler.localHandler else handler.defaultHandler
        return dataHandler.prepareWrite(uuid, key, value)
    }

    fun <T : Any> writePrepared(uuid: UUID, key: PersistentDataKey<T>, write: com.willfp.eco.core.data.handlers.PreparedProfileWrite) {
        valuesToWrite.put(WriteRequest(uuid, key), write)?.supersede()
    }

    fun startTickingSaves() {
        plugin.scheduler.runTimer(20, saveInterval) {
            val iterator = valuesToWrite.iterator()

            while (iterator.hasNext()) {
                val (request, value) = iterator.next()
                // Conditional removal preserves a newer origin-captured write for this same key.
                if (!valuesToWrite.remove(request, value)) continue

                val dataHandler = if (request.key.isSavedLocally) handler.localHandler else handler.defaultHandler

                // Pass the value to the data handler
                dataHandler.writePrepared(value)
            }
        }
    }

    fun startTickingAutosave() {
        plugin.scheduler.runTimer(autosaveInterval, autosaveInterval) {
            if (handler.localHandler.shouldAutosave()) {
                handler.localHandler.save()
            }
        }
    }

    private data class WriteRequest<T>(val uuid: UUID, val key: PersistentDataKey<T>)
}

val PersistentDataKey<*>.isSavedLocally: Boolean
    get() = this.isLocal || EcoPlugin.getPlugin(this.key.namespace)?.isUsingLocalStorage == true
