package com.noxxx.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A Material 3 filled card with an optional leading icon in the title row. */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

/**
 * Whole row toggles the switch, as Material 3 list controls do. "On" is always the stricter
 * setting, so while settings are locked a switch can be turned on but not off.
 */
@Composable
fun ToggleRow(label: String, description: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val lockedOn = checked && !LocalSettingsEditable.current
    val canChange = enabled && !lockedOn
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = canChange, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        if (lockedOn && enabled) LockedGlyph()
        Switch(checked = checked, onCheckedChange = null, enabled = canChange)
    }
}

@Composable
fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = if (ok) "Done" else "Needs attention",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** An icon in a tonal circle, used for page headers and the block screen. */
@Composable
fun IconBadge(
    icon: ImageVector,
    size: Dp = 56.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(shape = CircleShape, color = container, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/**
 * Text field + add button, followed by removable chips. [addLoosens] marks lists where adding an
 * entry weakens protection (the allow list); otherwise removing does. That direction is disabled
 * while settings are locked.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditableList(
    items: Set<String>,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Uri,
    addLoosens: Boolean = false,
    normalize: (String) -> String?,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val editable = LocalSettingsEditable.current
    val canAdd = editable || !addLoosens
    val canRemove = editable || addLoosens
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    fun submit() {
        val v = normalize(input)
        if (v == null) error = true else { onAdd(v); input = ""; error = false }
    }
    if (!canAdd) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LockedGlyph()
            Hint("Adding here is locked. Request an unlock to change it.")
        }
    } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = false },
            modifier = Modifier.weight(1f),
            singleLine = true,
            isError = error,
            placeholder = { Text(placeholder) },
            supportingText = if (error) ({ Text("That doesn't look right") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        FilledTonalIconButton(onClick = { submit() }) { Icon(Icons.Filled.Add, contentDescription = "Add") }
    }
    if (items.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.sorted().forEach { item ->
                InputChip(
                    selected = false,
                    onClick = { if (canRemove) onRemove(item) },
                    label = { Text(item) },
                    trailingIcon = if (canRemove) ({
                        Icon(Icons.Filled.Close, contentDescription = "Remove $item", Modifier.size(InputChipDefaults.IconSize))
                    }) else null,
                )
            }
        }
    }
}

@Composable
fun PasswordAction(label: String, button: String, numeric: Boolean = false, onSubmit: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onSubmit(value); value = "" }) { Text(button) }
    }
}
