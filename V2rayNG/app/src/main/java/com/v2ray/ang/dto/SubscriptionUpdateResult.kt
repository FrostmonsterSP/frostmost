package com.v2ray.ang.dto

/** Why a single subscription update produced no servers. */
enum class SubscriptionFailure { DOWNLOAD, NO_SERVERS }

/**
 * Result of subscription update operation
 */
data class SubscriptionUpdateResult(
    val configCount: Int = 0,      // Total configs updated
    val successCount: Int = 0,     // Subscriptions updated successfully
    val failureCount: Int = 0,     // Subscriptions failed to update
    val skipCount: Int = 0,        // Subscriptions skipped (disabled)
    val failure: SubscriptionFailure? = null // Reason of the first failure, null on success
) {
    /**
     * Combine two results by adding their counts
     */
    operator fun plus(other: SubscriptionUpdateResult): SubscriptionUpdateResult {
        return SubscriptionUpdateResult(
            configCount = this.configCount + other.configCount,
            successCount = this.successCount + other.successCount,
            failureCount = this.failureCount + other.failureCount,
            skipCount = this.skipCount + other.skipCount,
            failure = this.failure ?: other.failure
        )
    }
}

