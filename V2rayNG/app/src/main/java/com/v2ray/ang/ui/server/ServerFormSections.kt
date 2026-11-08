package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/**
 * One labelled card of a form: a section title above a [FrostSurface] card that holds the fields.
 *
 * This is the unit the server editors are rebuilt from. The old editor was one undifferentiated
 * column of outlined boxes, which is why a 20-field screen read as a wall: nothing told the reader
 * where "connection" ended and "transport" began. Grouping into cards gives the eye a landing place
 * every few fields, and the card tone gives the fields an inner surface to sit on instead of
 * floating straight on the page.
 *
 * The title is a real heading for accessibility, so TalkBack can jump between sections instead of
 * walking every field.
 *
 * The section card is told apart from the page by tone (`surface` on `background`), never by a
 * decorative border.
 */
@Composable
fun FrostFormSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FrostSpacing.s),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = frostPageColors().muted(),
            modifier = Modifier
                .padding(start = FrostSpacing.xs)
                .semantics { heading() },
        )
        FrostSurface(
            colors = frostCardColors(),
            modifier = Modifier.fillMaxWidth(),
            shape = FrostRadius.card,
        ) {
            Column(
                modifier = Modifier.padding(FrostSpacing.l),
                verticalArrangement = Arrangement.spacedBy(FrostSpacing.xs),
                content = content,
            )
        }
    }
}
