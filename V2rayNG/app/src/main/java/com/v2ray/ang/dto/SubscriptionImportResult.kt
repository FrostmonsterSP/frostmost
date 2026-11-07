package com.v2ray.ang.dto

/** Outcome of importing a single subscription URL. */
sealed interface SubscriptionImportResult {
    /** The subscription was created and its servers downloaded. */
    data class Added(val guid: String) : SubscriptionImportResult

    /** The URL is already a subscription; nothing was changed. */
    data class AlreadyExists(val guid: String) : SubscriptionImportResult

    /** The URL could not be downloaded; the created record was removed. */
    data object DownloadFailed : SubscriptionImportResult

    /** The response held no usable servers; the created record was removed. */
    data object NoServers : SubscriptionImportResult
}
