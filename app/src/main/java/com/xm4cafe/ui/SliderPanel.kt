// app/src/main/java/com/coffeehouse/ui/SliderPanel.kt
package com.coffeehouse.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
                valueRange = -1400f..0f,
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
        ThickSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThickSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val start = valueRange.start
    val end = valueRange.endInclusive
    val fraction = ((value - start) / (end - start)).coerceIn(0f, 1f)

    fun updateFromX(x: Float) {
        val safeWidth = widthPx.coerceAtLeast(1).toFloat()
        val nextFraction = (x / safeWidth).coerceIn(0f, 1f)
        onValueChange(start + (end - start) * nextFraction)
    }

    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val activeColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = modifier
            .height(32.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { offset -> updateFromX(offset.x) },
                    onDrag = { change, _ ->
                        updateFromX(change.position.x)
                        change.consume()
                    },
                )
            },
    ) {
        val trackHeight = 8.dp.toPx()
        val trackCorner = 2.dp.toPx()
        val thumbWidth = 10.dp.toPx()
        val thumbHeight = 24.dp.toPx()
        val centerY = size.height / 2f
        val activeWidth = size.width * fraction
        val thumbLeft = (activeWidth - thumbWidth / 2f)
            .coerceIn(0f, size.width - thumbWidth)

        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackCorner, trackCorner),
        )
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(activeWidth, trackHeight),
            cornerRadius = CornerRadius(trackCorner, trackCorner),
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbLeft, centerY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )
    }
}
