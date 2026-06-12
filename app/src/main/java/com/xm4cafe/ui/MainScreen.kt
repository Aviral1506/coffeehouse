// app/src/main/java/com/coffeehouse/ui/MainScreen.kt
package com.coffeehouse.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coffeehouse.model.CafeSettings
import com.coffeehouse.model.Preset
import com.coffeehouse.viewmodel.MainViewModel

/**
 * Compose root for the single-screen UI (spec §8).
 *
 * Layout, top to bottom:
 *   1. StatusBar              — XM4 + service state
 *   2. Title block            — "XM4 Cafe Mode" / "Sony WH-1000XM4"
 *   3. Master Effects switch
 *   4. PresetSelector         — Cafe / Living Room / My Room / Off
 *   5. Fine Tune SliderPanel  — collapses when OFF is selected
 *   6. Save-as-Custom button  — only when a non-OFF preset is active (Phase 5)
 *   7. Custom preset chips    — LazyRow, long-press to delete (Phase 5)
 *   8. Save dialog            — modal AlertDialog, name input (Phase 5)
 *
 * Edge-to-edge insets handled at the outermost Column via safeDrawingPadding
 * (Constraint A).
 */
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState        by viewModel.uiState.collectAsState()
    val roomSize       by viewModel.roomSize.collectAsState()
    val width          by viewModel.width.collectAsState()
    val air            by viewModel.air.collectAsState()
    val warmth         by viewModel.warmth.collectAsState()
    val customPresets  by viewModel.customPresets.collectAsState()
    val showSaveDialog by viewModel.showSaveDialog.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        StatusBar(uiState = uiState)

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Coffeehouse",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = "Audio Enhancer",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = "Effects",
                style = MaterialTheme.typography.titleMedium,
            )
            Switch(
                checked = uiState.effectsEnabled,
                onCheckedChange = { viewModel.toggleEffects(it) },
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text  = "Preset",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        PresetSelector(
            activePreset     = uiState.activePreset,
            onPresetSelected = { viewModel.selectPreset(it) },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text  = "Fine Tune",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SliderPanel(
            activePreset     = uiState.activePreset,
            roomSize         = roomSize,
            width            = width,
            air              = air,
            warmth           = warmth,
            onRoomSizeChange = viewModel::onRoomSizeChange,
            onWidthChange    = viewModel::onWidthChange,
            onAirChange      = viewModel::onAirChange,
            onWarmthChange   = viewModel::onWarmthChange,
        )

        // "Save as Custom" button — only visible when a non-OFF preset is
        // active. Saving while OFF would just freeze a silent state, so the
        // entry point is hidden in that case.
        if (uiState.activePreset != Preset.OFF) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.requestSaveCustomPreset() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save as Custom")
            }
        }

        // Custom preset chips — header + horizontally-scrolling row, only
        // rendered when at least one custom preset exists so the empty state
        // takes no vertical space.
        if (customPresets.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Custom",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            CustomPresetRow(
                presets  = customPresets,
                onSelect = { viewModel.selectCustomPreset(it) },
                onDelete = { viewModel.deleteCustomPreset(it) },
            )
        }

        // Save dialog — Material3 AlertDialog (Phase 5 Note C).
        if (showSaveDialog) {
            SavePresetDialog(
                onConfirm = { viewModel.saveCustomPreset(it) },
                onDismiss = { viewModel.dismissSaveDialog() },
            )
        }
    }
}

/**
 * Horizontally-scrolling row of custom preset chips. Tap selects, long-press
 * deletes (no confirmation — the spec deliberately keeps this terse to match
 * the chip metaphor; mis-deletes can be re-saved easily).
 *
 * combinedClickable is in androidx.compose.foundation and is currently
 * marked @ExperimentalFoundationApi — opted in at the composable level.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomPresetRow(
    presets: Map<String, CafeSettings>,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val names = presets.keys.toList()
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(names) { name ->
            Box(
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .combinedClickable(
                        onClick = { onSelect(name) },
                        onLongClick = { onDelete(name) },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * Modal dialog for naming a new custom preset. "Save" is disabled until the
 * text field has at least one non-whitespace character — matches the
 * blank-check in [MainViewModel.saveCustomPreset] so the visible state and
 * actual behaviour can't disagree.
 */
@Composable
private fun SavePresetDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
