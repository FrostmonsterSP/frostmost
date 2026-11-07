package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.SubscriptionFailure
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.handler.ImportClassifier
import java.net.URI

/** Where a subscription import started from a link or the first-run screen stands. */
sealed interface HomeImportState {
    data object Idle : HomeImportState

    /** The subscription is being downloaded; the screen shows "downloading" instead of an empty page. */
    data class Downloading(val url: String) : HomeImportState

    /** A link arrived while subscriptions exist: waits for the user's "Add «title»?" answer. */
    data class Confirm(val url: String, val title: String) : HomeImportState

    /** First-run import failed: the screen stays with [url] in the field and a retry button. */
    data class Failed(val url: String, val reason: SubscriptionFailure) : HomeImportState
}

/** First subscription is added at once; later ones ask, naming the panel title or, without one, the host. */
internal fun nextImportState(url: String, hasSubscriptions: Boolean, title: String?): HomeImportState =
    if (!hasSubscriptions) {
        HomeImportState.Downloading(url)
    } else {
        HomeImportState.Confirm(url, title?.takeIf { it.isNotBlank() } ?: linkDisplayHost(url))
    }

/** Only the host is ever shown for an address: its path and query may hold a secret token. */
internal fun linkDisplayHost(url: String): String =
    runCatching { URI(url.trim()).host }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "…"

/**
 * The failure stays on the first-run screen; otherwise the state is Idle and the address goes back to the
 * Add sheet (see MainUiState.importDraft). [firstRun] must be the predicate that decides the first-run screen.
 */
internal fun importFailureState(url: String, firstRun: Boolean, reason: SubscriptionFailure): HomeImportState =
    if (firstRun) HomeImportState.Failed(url, reason) else HomeImportState.Idle

/** Same predicate as [shouldShowFirstRun]: no subscription and no server. */
internal fun isFirstRun(hasSubscriptions: Boolean, serverCount: Int): Boolean = !hasSubscriptions && serverCount == 0

/** Guid of the stored subscription with exactly this [url]; a repeated link opens it without downloading. */
internal fun existingSubscriptionGuid(subscriptions: List<SubscriptionCache>, url: String): String? =
    subscriptions.firstOrNull { it.guid.isNotEmpty() && it.subscription.url == url }?.guid

/** Value for EXTRA_IMPORT_LINK from shared text: the raw text (any kind is classified later), "" when blank. */
internal fun sharedTextExtra(text: String?): String = text?.trim().orEmpty()

/**
 * Value for `MainActivity.EXTRA_IMPORT_LINK`: the validated address, or "" when the link is not
 * acceptable (MainActivity then shows "this does not look like a subscription link").
 */
internal fun deepLinkExtra(host: String?, urlParam: String?, fragment: String? = null): String {
    val url = ImportClassifier.parseDeepLink(host, urlParam)?.url ?: return ""
    // `scheme://install-sub?url=https://h/s#Name`: the name after # belongs to the link, not to the address; keep it as before.
    return if (!fragment.isNullOrEmpty() && '#' !in url) "$url#$fragment" else url
}
