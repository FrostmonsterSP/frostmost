package com.v2ray.ang.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.SubscriptionInfoParser
import com.v2ray.ang.util.Utils

private const val MILLIS_PER_SECOND = 1000L
private const val EXPIRY_DATE_PATTERN = "yyyy-MM-dd"
private const val NO_EXPIRY_SYMBOL = "\u221E"
private const val CARD_ALPHA = 0.97f

/** Locale-neutral values the subscription information box renders. */
internal data class SubscriptionInfoUiModel(
    val title: String?,
    val usedBytes: Long,
    /** Traffic quota in bytes; 0 means unlimited. */
    val totalBytes: Long,
    /** Used share of the quota in 0..1; null when the quota is unlimited. */
    val trafficFraction: Float?,
    /** Expiry in epoch milliseconds; null when the subscription never expires. */
    val expireMillis: Long?,
    val expired: Boolean,
    val supportUrl: String?,
    val webPageUrl: String?,
) {
    val trafficLow: Boolean get() = trafficFraction != null && trafficFraction >= TRAFFIC_WARNING_FRACTION

    companion object {
        const val TRAFFIC_WARNING_FRACTION = 0.9f
    }
}

internal fun buildSubscriptionInfoUiModel(
    info: SubscriptionUsageInfo,
    nowMillis: Long,
): SubscriptionInfoUiModel {
    val unlimited = info.total <= 0L
    val expireMillis = info.expire.takeIf { it > 0L }?.let { it * MILLIS_PER_SECOND }
    return SubscriptionInfoUiModel(
        title = info.title,
        usedBytes = info.used,
        totalBytes = if (unlimited) 0L else info.total,
        trafficFraction = if (unlimited) null else (info.used.toDouble() / info.total).toFloat().coerceIn(0f, 1f),
        expireMillis = expireMillis,
        expired = expireMillis != null && expireMillis <= nowMillis,
        supportUrl = info.supportUrl,
        webPageUrl = info.webPageUrl,
    )
}

/**
 * How much of the expiry warning window is left, for the card's expiry bar; null means no bar.
 *
 * The bar is **not** the paid period. A subscription panel publishes only the end date, never the
 * purchase or start date, so "share of the paid month" cannot be computed at all: any bar built
 * that way would invent its starting point. The bar therefore measures the warning window
 * [SUBSCRIPTION_EXPIRY_WINDOW_DAYS]: full while the end date is a month or more away, empty once it
 * has passed, and shrinking in between. A bar that reads "a month left" is honest; one that reads
 * "half the period used" would not be.
 *
 *  - null when [expireMillis] is null — the subscription never expires, so there is nothing to fill;
 *  - 0f when [expired] or the end date is already behind [nowMillis] — an empty rail reads as
 *    "nothing left" and matches the "expired on …" line next to it;
 *  - otherwise `remaining / window`, clamped: a date beyond the window is a full bar, not an
 *    overflow, because the bar cannot show anything above "a month".
 */
internal fun subscriptionExpiryProgress(
    expireMillis: Long?,
    expired: Boolean,
    nowMillis: Long,
): Float? {
    if (expireMillis == null) return null
    if (expired) return 0f
    val remainingMillis = expireMillis - nowMillis
    if (remainingMillis <= 0L) return 0f
    return (remainingMillis.toDouble() / EXPIRY_WINDOW_MILLIS).toFloat().coerceIn(0f, 1f)
}

/** Days of notice before the end date that the expiry bar fills over. */
internal const val SUBSCRIPTION_EXPIRY_WINDOW_DAYS = 30L

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
private val EXPIRY_WINDOW_MILLIS = SUBSCRIPTION_EXPIRY_WINDOW_DAYS * MILLIS_PER_DAY

/**
 * Fixed box on top of a subscription group showing the account information published by the
 * subscription panel: traffic, expiry date and shortcuts (support link, panel page, update).
 */
