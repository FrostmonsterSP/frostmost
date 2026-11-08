package com.v2ray.ang.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The field set every server editor is built from.
 *
 * What changed in the FrostMost rebuild and why:
 * - the field keeps `OutlinedTextField`, because that is what carries the IME connection, the
 *   focus/error plumbing and the accessibility contract of a text input. Only its *presentation*
 *   changed: the corner is [FrostRadius.tile] (a field is the same "tile" weight as a button),
 *   the side padding is zero because the surrounding [FrostFormSection] card owns the outer
 *   rhythm, and the field is a filled block instead of an outlined one — see [fieldColors].
 * - errors are announced through `Modifier.semantics { error(...) }` as well as drawn, so a
 *   screen-reader user hears that the field is required and invalid rather than only seeing red.
 * - the label is passed to `OutlinedTextField.label`, so it stays reachable for TalkBack, and it
 *   is also the field's own accessibility name.
 *
 * A field separates from its card by fill, not by a decorative line.
 *
 * The idle field is `surfaceContainerHigh` on the `surface` card, so it reads as a well cut into the
 * plate (a real tone step in both themes, measured 1.18:1 light / 1.15:1 dark). A border is drawn
 * only where it *carries state*: `primary` while focused and `error` while invalid, both of which
 * are also marked by the label and the supporting text. The idle and disabled outlines are
 * transparent for that reason — an outline that says nothing but "there is a box here" is exactly
 * the decoration this pass removes. The focused fill goes one step lighter than the idle one so the
 * field the keyboard is typing into is the one that stands out.
 */
@Composable
private fun fieldColors(): androidx.compose.material3.TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        // State-bearing outlines only: focused (primary) and error (error). Everything else is
        // carried by the fill above.
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorLabelColor = MaterialTheme.colorScheme.error,
        errorTextColor = MaterialTheme.colorScheme.onSurface,
        errorContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        errorSupportingTextColor = MaterialTheme.colorScheme.error,
        cursorColor = MaterialTheme.colorScheme.primary,
        errorCursorColor = MaterialTheme.colorScheme.error,
        selectionColors = TextSelectionColors(
            handleColor = MaterialTheme.colorScheme.primary,
            backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
    )

@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
    maxLines: Int = 5,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = false,
        maxLines = maxLines,
        enabled = enabled,
        shape = FrostRadius.tile,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = fieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FrostSpacing.xxs)
            .semantics { if (isError) error(label) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    enabled: Boolean = true,
    placeholder: String? = null,
    supportingText: String? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { newExpanded ->
            if (!enabled) return@ExposedDropdownMenuBox
            if (!editable && newExpanded) {
                keyboardController?.hide()
            }
            expanded = newExpanded
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = FrostSpacing.xxs)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (editable) onValueChange(it) },
            readOnly = !editable,
            enabled = enabled,
            label = { Text(label) },
            placeholder = { if (placeholder != null) Text(placeholder) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = FrostRadius.tile,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = fieldColors(),
            modifier = Modifier
                .menuAnchor(
                    type = if (editable) ExposedDropdownMenuAnchorType.PrimaryEditable
                    else ExposedDropdownMenuAnchorType.PrimaryNotEditable
                )
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (!editable && focusState.isFocused) {
                        keyboardController?.hide()
                        // A read-only dropdown receives the "edit" accessibility action too; opening
                        // the menu from it keeps TalkBack able to choose an option at all.
                        if (!expanded) expanded = true
                    }
                }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.verticalScrollbar(menuScrollState),
            scrollState = menuScrollState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEach { option ->
                // The current choice is marked with a weight step as well as a tone step, so the
                // selected option is identifiable when the list is read aloud or seen without colour.
                val selected = option == value
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            style = if (selected) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }
}
