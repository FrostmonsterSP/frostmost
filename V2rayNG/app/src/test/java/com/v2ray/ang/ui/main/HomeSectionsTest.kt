package com.v2ray.ang.ui.main

import com.v2ray.ang.enums.RoutingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeSectionsTest {

    @Test fun startsCollapsed() {
        val state = HomeSectionState()
        assertFalse(state.isExpanded(HomeSection.Routing))
        assertFalse(state.isExpanded(HomeSection.Servers))
        assertEquals(emptySet<HomeSection>(), state.expanded)
    }

    @Test fun toggleExpandsThenCollapses() {
        val expanded = HomeSectionState().toggle(HomeSection.Routing)
        assertTrue(expanded.isExpanded(HomeSection.Routing))
        assertFalse(expanded.isExpanded(HomeSection.Servers))
        assertEquals(setOf(HomeSection.Routing), expanded.expanded)

        val collapsed = expanded.toggle(HomeSection.Routing)
        assertFalse(collapsed.isExpanded(HomeSection.Routing))
        assertEquals(emptySet<HomeSection>(), collapsed.expanded)
    }

    @Test fun toggleLeavesTheSourceStateUntouched() {
        val initial = HomeSectionState()
        val expanded = initial.toggle(HomeSection.Routing)
        assertFalse(initial.isExpanded(HomeSection.Routing))
        assertEquals(HomeSectionState(), initial)
        assertEquals(HomeSectionState(setOf(HomeSection.Routing)), expanded)
    }

    @Test fun expandAndCollapseAreIdempotent() {
        val expanded = HomeSectionState().expand(HomeSection.Routing)
        assertSame(expanded, expanded.expand(HomeSection.Routing))
        assertEquals(setOf(HomeSection.Routing), expanded.expanded)

        val collapsed = expanded.collapse(HomeSection.Routing)
        assertFalse(collapsed.isExpanded(HomeSection.Routing))
        assertSame(collapsed, collapsed.collapse(HomeSection.Routing))
    }

    @Test fun collapseOfClosedSectionKeepsOthers() {
        val servers = HomeSectionState().expand(HomeSection.Servers)
        val both = servers.collapse(HomeSection.Routing)
        assertTrue(both.isExpanded(HomeSection.Servers))
        assertFalse(both.isExpanded(HomeSection.Routing))
    }

    @Test fun sectionsExpandIndependently() {
        val routing = HomeSectionState().expand(HomeSection.Routing)
        val both = routing.expand(HomeSection.Servers)
        assertTrue(both.isExpanded(HomeSection.Routing))
        assertTrue(both.isExpanded(HomeSection.Servers))

        val serversOnly = both.collapse(HomeSection.Routing)
        assertFalse(serversOnly.isExpanded(HomeSection.Routing))
        assertTrue(serversOnly.isExpanded(HomeSection.Servers))
        assertEquals(setOf(HomeSection.Servers), serversOnly.expanded)
    }

    @Test fun summaryIsLoadingUntilPresetIsRead() {
        assertEquals(RoutingSummary.Loading, routingSummaryKey(null, presetLoaded = false))
        assertEquals(RoutingSummary.Loading, routingSummaryKey(RoutingType.GLOBAL, presetLoaded = false))
        // A null preset before loading is "not read yet", not custom rules.
        assertEquals(RoutingSummary.Custom, routingSummaryKey(null, presetLoaded = true))
    }

    @Test fun summaryIsCustomForRulesMatchingNoPreset() {
        assertEquals(RoutingSummary.Custom, routingSummaryKey(preset = null, presetLoaded = true))
    }

    @Test fun summaryIsPresetForEveryNamedPreset() {
        RoutingType.entries.forEach { type ->
            assertEquals(RoutingSummary.Preset, routingSummaryKey(type, presetLoaded = true), type.name)
        }
    }
}
