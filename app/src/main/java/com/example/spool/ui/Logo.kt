package com.example.spool.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.spool.ui.theme.Amber
import com.example.spool.ui.theme.Ember
import com.example.spool.ui.theme.Lavender

@Composable
fun LogoMark(modifier: Modifier = Modifier, markSize: Dp = 32.dp) {
    Canvas(modifier.size(markSize)) {
        val side = this.size.minDimension
        val u = side / 64f
        val c = center
        val brush = Brush.linearGradient(
            colors = listOf(Amber, Ember, Lavender),
            start = Offset.Zero,
            end = Offset(side, side)
        )

        // Open ring, gap on the right
        drawArc(
            brush = brush,
            startAngle = 35f,
            sweepAngle = 290f,
            useCenter = false,
            topLeft = Offset(c.x - 26 * u, c.y - 26 * u),
            size = Size(52 * u, 52 * u),
            style = Stroke(width = 6 * u, cap = StrokeCap.Round)
        )

        // Play triangle sliding out through the gap
        val triangle = Path().apply {
            moveTo(c.x - 8 * u, c.y - 16 * u)
            lineTo(c.x - 8 * u, c.y + 16 * u)
            lineTo(c.x + 18 * u, c.y)
            close()
        }
        drawPath(triangle, brush, style = Fill)
        drawPath(triangle, brush, style = Stroke(width = 3 * u, join = StrokeJoin.Round))
    }
}