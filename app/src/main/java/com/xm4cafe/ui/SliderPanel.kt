// app/src/main/java/com/coffeehouse/ui/SliderPanel.kt
package com.coffeehouse.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coffeehouse.model.Preset

/**
 * Fine-tune slider panel (spec §8.2).
 *
 * Hidden via AnimatedVisibility when OFF is the active preset so the user
 * has a single visual cue that "nothing is happening" — the entire fine-tune
 * section drops out rather than just greying out.
 *
 * Each slider's display value is formatted right next to the label so the
 * user gets immediate feedback without watching a numeric ticker move.
 */
@Composable
fun SliderPanel(
    activePreset: Preset,
    roomSize: Float,
    width: Float,
    air: Float,
    warmth: Float,
    onRoomSizeChange: (Float) -> Unit,
    onWidthChange: (Float) -> Unit,
    onAirChange: (Float) -> Unit,
    onWarmthChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = activePreset != Preset.OFF,
        enter = fadeIn() + slideInVertically(),
        exit  = fadeOut() + slideOutVertically(),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LabelledSlider(
                label = "Room Size",
                value = roomSize,
                valueRange = 0f..100f,
                onValueChange = onRoomSizeChange,
                displayValue = when {
                    roomSize <= 33f -> "Small"
                    roomSize <= 66f -> "Medium"
                    else            -> "Large"
                },
            )
            LabelledSlider(
                label = "Width",
                value = width,
                valueRange = 0f..1000f,
                onValueChange = onWidthChange,
                displayValue = width.toInt().toString(),
            )
            LabelledSlider(
                label = "Air",
                value = air,
                valueRange = -600f..0f,
                onValueChange = onAirChange,
                displayValue = "%.1f dB".format(air / 100f),
            )
            LabelledSlider(
                label = "Warmth",
                value = warmth,
                valueRange = 0f..500f,
                onValueChange = onWarmthChange,
                displayValue = warmth.toInt().toString(),
            )
        }
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    displayValue: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text  = displayValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
