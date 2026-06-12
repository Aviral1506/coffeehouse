// app/src/main/java/com/coffeehouse/ui/StatusBar.kt
package com.coffeehouse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coffeehouse.viewmodel.UiState

/**
 * Top-of-screen status row (spec §8.1, §8.3).
 *
 * Left: Bluetooth glyph + XM4 device name (or "No headphones").
 * Right: green dot when service is bound, grey dot otherwise.
 *
 * The Bluetooth glyph is drawn inline via Canvas rather than imported from
 * material-icons-extended, which is not listed as a project dependency.
 * The shape is the iconic Bluetooth bowtie/runic-B silhouette built from
 * four line segments — small enough that the geometry is obvious at a
 * glance and there is nothing to mis-render across themes.
 */
@Composable
fun StatusBar(uiState: UiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ---- Left: bluetooth status ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            BluetoothGlyph(
                connected = uiState.xm4Connected,
                tint = if (uiState.xm4Connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Gray
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = if (uiState.xm4Connected) {
                    uiState.xm4DeviceName.ifBlank { "WH-1000XM4" }
                } else {
                    "No headphones"
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // ---- Right: service status ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dotColor = if (uiState.serviceConnected) {
                Color(0xFF1A6B3A) // green
            } else {
                Color(0xFF888888) // grey
            }
            Canvas(modifier = Modifier.size(10.dp)) {
                drawCircle(color = dotColor, radius = size.minDimension / 2f)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = if (uiState.serviceConnected) "Active" else "Off",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Minimal Bluetooth glyph drawn as four strokes:
 *   vertical center line + two diagonals that meet on the spine at the
 *   1/3 and 2/3 marks. Matches the universal Bluetooth pictogram closely
 *   enough that no caption is needed.
 */
@Composable
private fun BluetoothGlyph(
    @Suppress("UNUSED_PARAMETER") connected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val topY = h * 0.10f
        val botY = h * 0.90f
        val midY1 = h * 0.35f
        val midY2 = h * 0.65f
        val rightX = w * 0.78f
        val leftX  = w * 0.22f
        val strokeWidth = w * 0.10f

        // Bluetooth pictogram — five line segments:
        //   top-tip → mid-right ↘ center ↙ mid-left → bottom-tip, plus
        //   the vertical spine from top-tip to bottom-tip.
        drawLine(tint, Offset(cx, topY),       Offset(rightX, midY1),     strokeWidth = strokeWidth)
        drawLine(tint, Offset(rightX, midY1),  Offset(leftX, midY2),      strokeWidth = strokeWidth)
        drawLine(tint, Offset(leftX, midY2),   Offset(rightX, h * 0.65f), strokeWidth = strokeWidth)
        drawLine(tint, Offset(rightX, h * 0.65f), Offset(cx, botY),       strokeWidth = strokeWidth)
        drawLine(tint, Offset(cx, topY),       Offset(cx, botY),          strokeWidth = strokeWidth * 0.8f)
    }
}
