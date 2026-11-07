package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType

/** Detects which routing preset the current rules equal, if any. */
object RoutingPresetMatcher {
    /**
     * Compares the current unlocked rules with each preset; locked rules are the user's own
     * and are skipped. Ids are ignored since presets carry none.
     */
    fun match(current: List<RulesetItem>, presets: Map<RoutingType, List<RulesetItem>>): RoutingType? {
        val normalized = current.filter { it.locked != true }.map(::normalize)
        return presets.entries.firstOrNull { (_, rules) -> rules.map(::normalize) == normalized }?.key
    }

    private fun normalize(item: RulesetItem) = item.copy(id = "", locked = false)
}
