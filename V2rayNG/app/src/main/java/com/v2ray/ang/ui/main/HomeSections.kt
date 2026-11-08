package com.v2ray.ang.ui.main

import com.v2ray.ang.enums.RoutingType

/**
 * Inline, expandable configuration sections of the home screen. Pure of Android and Compose so the
 * expansion rules stay testable on the JVM; the composables render this state and own nothing of it.
 */
internal enum class HomeSection { Routing, Servers }

/**
 * Which inline sections are open. Immutable: every function returns a new instance, so both sections
 * may be expanded at the same time and an unchanged call is a no-op ([equals] keeps recomposition quiet).
 */
internal data class HomeSectionState(val expanded: Set<HomeSection> = emptySet()) {

    fun isExpanded(section: HomeSection): Boolean = section in expanded

    /** Opens a collapsed section and closes an expanded one. */
    fun toggle(section: HomeSection): HomeSectionState =
        if (isExpanded(section)) collapse(section) else expand(section)

    fun expand(section: HomeSection): HomeSectionState =
        if (isExpanded(section)) this else HomeSectionState(expanded + section)

    fun collapse(section: HomeSection): HomeSectionState =
        if (isExpanded(section)) HomeSectionState(expanded - section) else this
}

/** What the routing section header shows as the current value, before it is mapped to a string. */
internal enum class RoutingSummary { Loading, Custom, Preset }

/**
 * The current routing mode of the header: [RoutingSummary.Loading] until the preset is read from
 * settings, then [RoutingSummary.Custom] for rules that match no preset (a null preset after loading)
 * and [RoutingSummary.Preset] for a named one.
 */
internal fun routingSummaryKey(preset: RoutingType?, presetLoaded: Boolean): RoutingSummary = when {
    !presetLoaded -> RoutingSummary.Loading
    preset == null -> RoutingSummary.Custom
    else -> RoutingSummary.Preset
}
