package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.RoutingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RoutingPresetMatcherTest {
    @Test
    fun presetMatchIgnoresLockedRules() {
        val preset = listOf(RulesetItem(remarks = "a"))
        val locked = RulesetItem(remarks = "mine", locked = true)
        val presets = mapOf(RoutingType.GLOBAL to preset)
        assertEquals(RoutingType.GLOBAL, RoutingPresetMatcher.match(preset + locked, presets))
        assertNull(RoutingPresetMatcher.match(listOf(RulesetItem(remarks = "b")), presets))
    }

    @Test
    fun matchIgnoresIdsButNotOrderOrContent() {
        val a = RulesetItem(remarks = "a", outboundTag = "direct")
        val b = RulesetItem(remarks = "b", outboundTag = "proxy")
        val presets = mapOf(RoutingType.WHITE to listOf(a, b))
        assertEquals(RoutingType.WHITE, RoutingPresetMatcher.match(listOf(a.copy(id = "x"), b), presets))
        assertNull(RoutingPresetMatcher.match(listOf(b, a), presets))
        assertNull(RoutingPresetMatcher.match(listOf(a, b.copy(enabled = false)), presets))
    }
}
