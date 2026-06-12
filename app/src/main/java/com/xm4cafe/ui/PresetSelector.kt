// app/src/main/java/com/coffeehouse/ui/PresetSelector.kt
package com.coffeehouse.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coffeehouse.model.Preset

/**
 * Three-button segmented control: Cafe / Living Room / My Room (spec §8.1).
 * OFF is controlled via the master Effects toggle at the top.
 *
 * Selected preset uses filled Button, others use OutlinedButton. The outer
 * corners are rounded so the three controls read as one segmented bar.
 * Tapping a button fires a KEYBOARD_TAP haptic so the feedback matches the
 * audio-state change the user is about to hear.
 */
@Composable
fun PresetSelector(
    activePreset: Preset,
    onPresetSelected: (Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val presets = listOf(Preset.CAFE, Preset.LIVING_ROOM, Preset.MY_ROOM)
    val labels  = mapOf(
        Preset.CAFE        to "Cafe",
        Preset.LIVING_ROOM to "Living Room",
        Preset.MY_ROOM     to "My Room",
    )

    Row(modifier = modifier.fillMaxWidth()) {
        presets.forEachIndexed { index, preset ->
            val isSelected = preset == activePreset
            val onClick: () -> Unit = {
                onPresetSelected(preset)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            val shape = segmentedButtonShape(index, presets.lastIndex)
            val contentPad = PaddingValues(4.dp)

            if (isSelected) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    shape = shape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = contentPad,
                ) {
                    Text(
                        text  = labels.getValue(preset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    shape = shape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = contentPad,
                ) {
                    Text(
                        text  = labels.getValue(preset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private fun segmentedButtonShape(
    index: Int,
    lastIndex: Int,
    radius: Dp = 8.dp,
) = when (index) {
    0 -> RoundedCornerShape(
        topStart = radius,
        bottomStart = radius,
        topEnd = 0.dp,
        bottomEnd = 0.dp,
    )
    lastIndex -> RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = radius,
        bottomEnd = radius,
    )
    else -> RoundedCornerShape(0.dp)
}
