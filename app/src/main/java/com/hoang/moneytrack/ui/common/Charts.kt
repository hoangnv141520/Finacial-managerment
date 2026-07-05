package com.hoang.moneytrack.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// ponytail: hand-rolled Canvas charts (~100 lines) instead of a chart library —
// three simple chart types don't justify a dependency. Swap to Vico if interactivity is needed.

/** Paired bar chart: income vs expense per bucket. */
@Composable
fun PairedBarChart(income: List<Long>, expense: List<Long>, incomeColor: Color, expenseColor: Color) {
    val max = (income + expense).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val n = income.size
        if (n == 0) return@Canvas
        val group = size.width / n
        val barW = (group * 0.3f).coerceAtMost(28f)
        for (i in 0 until n) {
            val x = i * group + group / 2
            val hIn = size.height * income[i] / max
            val hEx = size.height * expense[i] / max
            drawRoundRect(
                incomeColor,
                topLeft = Offset(x - barW - 2, size.height - hIn),
                size = Size(barW, hIn),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
            )
            drawRoundRect(
                expenseColor,
                topLeft = Offset(x + 2, size.height - hEx),
                size = Size(barW, hEx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f),
            )
        }
    }
}

@Composable
fun LineChart(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.fillMaxWidth().height(140.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max().coerceAtLeast(min + 1)
        val stepX = size.width / (values.size - 1)
        fun y(v: Long) = size.height - size.height * (v - min) / (max - min)
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        val path = Path().apply {
            moveTo(0f, y(values[0]))
            values.forEachIndexed { i, v -> if (i > 0) lineTo(i * stepX, y(v)) }
        }
        drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
    }
}

/** Donut chart; slices = (value, color). */
@Composable
fun DonutChart(slices: List<Pair<Long, Color>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.first }.coerceAtLeast(1L)
    Canvas(modifier.height(160.dp).fillMaxWidth()) {
        val stroke = 40f
        val d = minOf(size.width, size.height) - stroke
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        var start = -90f
        slices.forEach { (v, c) ->
            val sweep = 360f * v / total
            drawArc(c, start, sweep - 2f, false, topLeft = topLeft, size = Size(d, d), style = Stroke(stroke))
            start += sweep
        }
    }
}
