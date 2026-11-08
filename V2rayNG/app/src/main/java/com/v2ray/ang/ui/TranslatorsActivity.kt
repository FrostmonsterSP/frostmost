package com.v2ray.ang.ui

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.dto.TranslatorsCredit
import com.v2ray.ang.dto.TranslatorsParser
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.FrostCardGroup
import com.v2ray.ang.ui.compose.FrostSpacing
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.frostPageColors
import com.v2ray.ang.util.Utils

class TranslatorsActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        TranslatorsScreen(onBackClick = { finish() })
    }
}

@Composable
fun TranslatorsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val credits = remember {
        TranslatorsParser.parse(Utils.readTextFromAssets(context, "translators.json"))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = frostPageColors().container,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_translators),
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = FrostSpacing.screenH,
                end = FrostSpacing.screenH,
                top = FrostSpacing.s,
                bottom = innerPadding.calculateBottomPadding() + FrostSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(FrostSpacing.cardGap)
        ) {
            items(credits, key = { it.language }) { credit ->
                TranslationCreditCard(credit)
            }
            item {
                NavigationBarsSpacer()
            }
        }
    }
}

@Composable
private fun TranslationCreditCard(credit: TranslatorsCredit) {
    FrostCardGroup {
        Column {
            Text(
                text = credit.language,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(
                    start = FrostSpacing.cardPadding,
                    end = FrostSpacing.cardPadding,
                    top = FrostSpacing.m,
                    bottom = FrostSpacing.s,
                )
            )
            credit.contributors.forEachIndexed { index, contributor ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = FrostSpacing.l),
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )
                }
                ContributorRow(
                    displayName = contributor.displayName ?: contributor.name,
                    linkUrl = contributor.url
                )
            }
        }
    }
}

/**
 * One translator. A contributor with a link is a single focusable node whose label names the person;
 * the GitHub glyph beside it is decorative, so the row is not announced twice.
 */
@Composable
private fun ContributorRow(displayName: String, linkUrl: String?) {
    val context = LocalContext.current
    val isLink = linkUrl != null
    val openLabel = stringResource(R.string.acc_open_profile, displayName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isLink) {
                    Modifier.clickable(role = Role.Button, onClickLabel = openLabel) {
                        Utils.openUri(context, linkUrl)
                    }
                } else {
                    Modifier
                }
            )
            .padding(
                start = FrostSpacing.cardPadding,
                end = FrostSpacing.cardPadding,
                top = FrostSpacing.m,
                bottom = FrostSpacing.m,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.m),
    ) {
        if (isLink) {
            Icon(
                painter = painterResource(R.drawable.ic_github_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
