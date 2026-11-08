package com.v2ray.ang.ui.compose

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * Dialogs of FrostMost.
 *
 * Two constraints shape this file. First, Material's `AlertDialog` takes no `contentColor`, so a
 * colour can only arrive through `containerColor`/`textContentColor`/`titleContentColor` or through
 * an explicit `Text(color = …)`: every text here therefore inherits from the dialog's own colours
 * and never assumes the page's. Second, a dialog is a card on a scrim, so it takes
 * [FrostRadius.card] and the card's own surface pair instead of Material's tinted defaults.
 */

/**
 * An action of a dialog. It is [FrostTextButton] plus an optional leading glyph, because a
 * destructive confirmation reads better with the trash icon it destroys with; the glyph is
 * decorative, since the label already names the action.
 */
@Composable
private fun DialogAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    if (leadingIcon == null) {
        FrostTextButton(label = label, onClick = onClick, modifier = modifier)
        return
    }
    Row(
        modifier
            .heightIn(min = FrostSpacing.touchTarget)
            .clip(FrostRadius.tile)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = FrostSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FrostSpacing.xs),
    ) {
        leadingIcon()
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String? = null,
    message: String,
    confirmText: String = stringResource(R.string.action_ok),
    dismissText: String? = stringResource(R.string.action_cancel),
    confirmIcon: @Composable (() -> Unit)? = null,
    messageTextAlign: TextAlign = TextAlign.Start,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val confirmFocusRequester = remember { FocusRequester() }
    val dismissFocusRequester = remember { FocusRequester() }
    val card = frostCardColors()

    LaunchedEffect(dismissText) {
        if (dismissText != null) dismissFocusRequester.requestFocus()
        else confirmFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = card.content,
                )
            }
        },
        text = {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = card.muted(),
                textAlign = messageTextAlign
            )
        },
        confirmButton = {
            DialogAction(
                label = confirmText,
                onClick = { onConfirm(); onDismiss() },
                modifier = Modifier.focusRequester(confirmFocusRequester),
                leadingIcon = confirmIcon,
            )
        },
        dismissButton = dismissText?.let { text ->
            {
                DialogAction(
                    label = text,
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(dismissFocusRequester),
                )
            }
        },
        containerColor = card.container,
        textContentColor = card.content,
        titleContentColor = card.content,
        shape = FrostRadius.card,
        tonalElevation = 0.dp,
    )
}

@Composable
fun DeleteConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    itemName: String? = null,
) {
    ConfirmDialog(
        message = if (itemName.isNullOrBlank()) message else "$message\n\n$itemName",
        messageTextAlign = TextAlign.Center,
        confirmText = stringResource(R.string.action_delete),
        confirmIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

data class InputField(
    val label: String,
    val value: String,
    val singleLine: Boolean = true,
    val visualTransformation: VisualTransformation = VisualTransformation.None
)

@Composable
fun InputDialog(
    title: String,
    fields: List<InputField>,
    onFieldChange: (Int, String) -> Unit,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val card = frostCardColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = card.content,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FrostSpacing.m)
            ) {
                fields.forEachIndexed { index, field ->
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { onFieldChange(index, it) },
                        label = { Text(field.label) },
                        singleLine = false,
                        maxLines = 5,
                        visualTransformation = field.visualTransformation,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            selectionColors = TextSelectionColors(
                                handleColor = MaterialTheme.colorScheme.primary,
                                backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            DialogAction(label = confirmText, onClick = onConfirm)
        },
        dismissButton = {
            DialogAction(label = dismissText, onClick = onDismiss)
        },
        containerColor = card.container,
        textContentColor = card.content,
        titleContentColor = card.content,
        shape = FrostRadius.card,
        tonalElevation = 0.dp,
    )
}

@Composable
fun QRCodeDialog(
    bitmap: Bitmap?,
    onDismiss: () -> Unit
) {
    if (bitmap == null) return
    val card = frostCardColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.acc_qr_code),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(FrostRadius.inner),
            )
        },
        confirmButton = {
            DialogAction(label = stringResource(R.string.action_close), onClick = onDismiss)
        },
        containerColor = card.container,
        textContentColor = card.content,
        shape = FrostRadius.card,
        tonalElevation = 0.dp,
    )
}

/**
 * A list of options in a dialog.
 *
 * When [showRadio] is true the options are single-choice and [selectedOption] is marked with a
 * radio; otherwise the list is a plain menu of actions. Either way one row is one focusable node:
 * the radio is decorative, so TalkBack announces the option and its selection once.
 */
@Composable
fun <T> SelectListDialog(
    options: List<T>,
    optionText: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    selectedOption: T? = null,
    showRadio: Boolean = false
) {
    val card = frostCardColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    color = card.content,
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(options) { option ->
                    val isSelected = option == selectedOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FrostRadius.tile)
                            .then(
                                if (showRadio) Modifier.selectable(
                                    selected = isSelected,
                                    onClick = { onSelected(option) },
                                    role = Role.RadioButton
                                ) else Modifier.clickable(role = Role.Button) { onSelected(option) }
                            )
                            .padding(vertical = FrostSpacing.m, horizontal = FrostSpacing.s),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showRadio) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.outline,
                                ),
                            )
                            Spacer(modifier = Modifier.width(FrostSpacing.m))
                        }
                        Text(
                            text = optionText(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = card.content,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            DialogAction(label = stringResource(R.string.action_cancel), onClick = onDismiss)
        },
        containerColor = card.container,
        textContentColor = card.content,
        titleContentColor = card.content,
        shape = FrostRadius.card,
        tonalElevation = 0.dp,
    )
}
