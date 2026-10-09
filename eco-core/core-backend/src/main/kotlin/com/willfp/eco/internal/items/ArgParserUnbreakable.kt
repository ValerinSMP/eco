package com.willfp.eco.internal.items

import com.willfp.eco.internal.items.templates.FlagArgParser
import org.bukkit.inventory.meta.ItemMeta

object ArgParserUnbreakable : FlagArgParser("unbreakable") {
    override fun apply(meta: ItemMeta) {
        meta.isUnbreakable = true
    }

    override fun test(meta: ItemMeta): Boolean {
        return meta.isUnbreakable
    }
}
