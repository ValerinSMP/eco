package com.willfp.eco.internal.spigot.arrows

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.items.isEcoEmpty
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRemoveEvent
import org.bukkit.event.entity.ProjectileLaunchEvent

class ArrowDataListener(
    private val plugin: EcoPlugin
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onLaunch(event: ProjectileLaunchEvent) {
        val arrow = event.entity

        if (arrow !is Arrow) {
            return
        }

        if (arrow.shooter !is LivingEntity) {
            return
        }

        val entity = arrow.shooter as LivingEntity

        val item = entity.equipment?.itemInMainHand

        if (item.isEcoEmpty || item == null) {
            return
        }

        arrow.setMetadata("shot-from", this.plugin.createMetadataValue(item))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRemove(event: EntityRemoveEvent) {
        val arrow = event.entity

        if (arrow !is Arrow) {
            return
        }

        if (arrow.hasMetadata("shot-from")) {
            arrow.removeMetadata("shot-from", this.plugin)
        }
    }
}
