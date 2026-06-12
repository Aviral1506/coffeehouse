// app/src/main/java/com/coffeehouse/ui/PresetSelector.kt
package com.coffeehouse.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.coffeehouse.model.Preset

/**
 * Four-button segmented control: Cafe / Living Room / My Room / Off (spec §8.1).
 *
 * Selected preset uses filled Button, others use OutlinedButton; all share
 * a zero-corner-radius shape so they read as a single horizontal segment.
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
    val presets = listOf(Preset.CAFE, Preset.LIVING_ROOM, Preset.MY_ROOM, Preset.OFF)
    val labels  = mapOf(
        Preset.CAFE        to "Cafe",
        Preset.LIVING_ROOM to "Living Room",
        Preset.MY_ROOM     to "My Room",
        Preset.OFF         to "Off",
    )

    Row(modifier = modifier.fillMaxWidth()) {
        presets.forEach { preset ->
            val isSelected = preset == activePreset
            val onClick: () -> Unit = {
                onPresetSelected(preset)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            val shape = RoundedCornerShape(0.dp)
            val contentPad = PaddingValues(4.dp)

            if (isSelected) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                    shape = shape,
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
