package com.mulocoolbot.gymtracker.ui.stats

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricMedium
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.SuccessGreen
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import java.util.Locale

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onSelectExercise: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Statistics",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )

        if (uiState.exercises.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            ExerciseSelectorRow(
                exercises = uiState.exercises,
                selectedExercise = uiState.selectedExercise,
                onSelectExercise = onSelectExercise
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        ProgressionChartCard(
            exerciseName = uiState.selectedExercise.ifEmpty { "Progression" },
            percentageChange = uiState.percentageChange,
            points = uiState.progressionPoints
        )

        Spacer(modifier = Modifier.height(20.dp))

        MuscleHeatmapCard(trainedMuscles = uiState.trainedMuscles)
    }
}

@Composable
private fun ExerciseSelectorRow(
    exercises: List<String>,
    selectedExercise: String,
    onSelectExercise: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        exercises.forEach { exercise ->
            val isSelected = exercise == selectedExercise
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) VoltAccent else CardSurface)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) VoltAccent else CardBorder,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelectExercise(exercise) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = exercise,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) OnVolt else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ProgressionChartCard(
    exerciseName: String,
    percentageChange: Double,
    points: List<ProgressionPoint>
) {
    val badgeColor = if (percentageChange >= 0.0) SuccessGreen else DangerRed
    val formattedPercentage = if (percentageChange >= 0.0) {
        "+%.1f%%".format(Locale.US, percentageChange)
    } else {
        "%.1f%%".format(Locale.US, percentageChange)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = formattedPercentage,
                        style = MonoMetricMedium,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppBackground)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                ProgressionLineChartCanvas(points = points)
            }
        }
    }
}

@Composable
private fun ProgressionLineChartCanvas(points: List<ProgressionPoint>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = 28.dp.toPx()
        val topPadding = 28.dp.toPx()
        val bottomPadding = 26.dp.toPx()
        val chartWidth = (size.width - horizontalPadding * 2f).coerceAtLeast(1f)
        val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        for (lineIndex in 0..3) {
            val y = topPadding + (chartHeight / 3f) * lineIndex
            drawLine(
                color = CardBorder,
                start = Offset(horizontalPadding, y),
                end = Offset(size.width - horizontalPadding, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        if (points.isEmpty()) return@Canvas

        val minE1rm = points.minOf { it.e1rm }
        val maxE1rm = points.maxOf { it.e1rm }
        val range = (maxE1rm - minE1rm).takeIf { it > 0.0 } ?: 10.0

        val offsets = points.mapIndexed { index, point ->
            val x = if (points.size == 1) {
                size.width / 2f
            } else {
                horizontalPadding + (index.toFloat() / (points.size - 1).toFloat()) * chartWidth
            }
            val normalizedY = if (maxE1rm == minE1rm) {
                0.5f
            } else {
                ((point.e1rm - minE1rm) / range).toFloat()
            }
            val y = topPadding + chartHeight * (1f - normalizedY)
            Offset(x, y)
        }

        if (offsets.size >= 2) {
            val fillPath = Path().apply {
                moveTo(offsets.first().x, topPadding + chartHeight)
                offsets.forEach { pt -> lineTo(pt.x, pt.y) }
                lineTo(offsets.last().x, topPadding + chartHeight)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(VoltAccent.copy(alpha = 0.32f), Color.Transparent),
                    startY = topPadding,
                    endY = topPadding + chartHeight
                )
            )

            val linePath = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
            }
            drawPath(
                path = linePath,
                color = VoltAccent,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        val valueLabelPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#F5F7FA")
            textAlign = Paint.Align.CENTER
            textSize = 11.dp.toPx()
            typeface = Typeface.MONOSPACE
        }

        val axisLabelPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#8E95A5")
            textAlign = Paint.Align.CENTER
            textSize = 11.dp.toPx()
            typeface = Typeface.MONOSPACE
        }

        offsets.forEachIndexed { index, pointOffset ->
            drawCircle(
                color = AppBackground,
                radius = 6.dp.toPx(),
                center = pointOffset
            )
            drawCircle(
                color = VoltAccent,
                radius = 4.dp.toPx(),
                center = pointOffset
            )

            val point = points[index]
            val weightText = if (point.weightKg % 1.0 == 0.0) {
                point.weightKg.toInt().toString()
            } else {
                "%.1f".format(Locale.US, point.weightKg)
            }
            val nodeLabel = "${weightText}×${point.reps}"

            drawContext.canvas.nativeCanvas.drawText(
                nodeLabel,
                pointOffset.x,
                (pointOffset.y - 10.dp.toPx()).coerceAtLeast(14.dp.toPx()),
                valueLabelPaint
            )

            drawContext.canvas.nativeCanvas.drawText(
                point.weekLabel,
                pointOffset.x,
                size.height - 6.dp.toPx(),
                axisLabelPaint
            )
        }
    }
}

@Composable
private fun MuscleHeatmapCard(trainedMuscles: Set<MuscleRegion>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Muscle Heatmap",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppBackground)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Front",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        drawFrontSilhouette(trainedMuscles)
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        drawBackSilhouette(trainedMuscles)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawFrontSilhouette(trainedMuscles: Set<MuscleRegion>) {
    val cx = size.width / 2f
    val h = size.height
    val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

    drawCircle(
        color = CardBorder,
        radius = h * 0.055f,
        center = Offset(cx, h * 0.07f)
    )

    val shoulderColor = muscleColor(MuscleRegion.SHOULDERS in trainedMuscles)
    drawRoundRect(
        color = shoulderColor,
        topLeft = Offset(cx - h * 0.16f, h * 0.15f),
        size = Size(h * 0.06f, h * 0.09f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = shoulderColor,
        topLeft = Offset(cx + h * 0.10f, h * 0.15f),
        size = Size(h * 0.06f, h * 0.09f),
        cornerRadius = corner
    )

    val chestColor = muscleColor(MuscleRegion.CHEST in trainedMuscles)
    drawRoundRect(
        color = chestColor,
        topLeft = Offset(cx - h * 0.095f, h * 0.15f),
        size = Size(h * 0.09f, h * 0.11f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = chestColor,
        topLeft = Offset(cx + h * 0.005f, h * 0.15f),
        size = Size(h * 0.09f, h * 0.11f),
        cornerRadius = corner
    )

    val bicepColor = muscleColor(MuscleRegion.BICEPS in trainedMuscles)
    drawRoundRect(
        color = bicepColor,
        topLeft = Offset(cx - h * 0.17f, h * 0.25f),
        size = Size(h * 0.055f, h * 0.16f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = bicepColor,
        topLeft = Offset(cx + h * 0.115f, h * 0.25f),
        size = Size(h * 0.055f, h * 0.16f),
        cornerRadius = corner
    )

    val absColor = muscleColor(MuscleRegion.ABS in trainedMuscles)
    drawRoundRect(
        color = absColor,
        topLeft = Offset(cx - h * 0.075f, h * 0.27f),
        size = Size(h * 0.15f, h * 0.19f),
        cornerRadius = corner
    )

    val quadsColor = muscleColor(MuscleRegion.QUADS in trainedMuscles)
    drawRoundRect(
        color = quadsColor,
        topLeft = Offset(cx - h * 0.09f, h * 0.48f),
        size = Size(h * 0.08f, h * 0.22f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = quadsColor,
        topLeft = Offset(cx + h * 0.01f, h * 0.48f),
        size = Size(h * 0.08f, h * 0.22f),
        cornerRadius = corner
    )

    val calvesColor = muscleColor(MuscleRegion.CALVES in trainedMuscles)
    drawRoundRect(
        color = calvesColor,
        topLeft = Offset(cx - h * 0.085f, h * 0.72f),
        size = Size(h * 0.07f, h * 0.20f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = calvesColor,
        topLeft = Offset(cx + h * 0.015f, h * 0.72f),
        size = Size(h * 0.07f, h * 0.20f),
        cornerRadius = corner
    )
}

private fun DrawScope.drawBackSilhouette(trainedMuscles: Set<MuscleRegion>) {
    val cx = size.width / 2f
    val h = size.height
    val corner = CornerRadius(4.dp.toPx(), 4.dp.toPx())

    drawCircle(
        color = CardBorder,
        radius = h * 0.055f,
        center = Offset(cx, h * 0.07f)
    )

    val shoulderColor = muscleColor(MuscleRegion.SHOULDERS in trainedMuscles)
    drawRoundRect(
        color = shoulderColor,
        topLeft = Offset(cx - h * 0.16f, h * 0.15f),
        size = Size(h * 0.06f, h * 0.09f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = shoulderColor,
        topLeft = Offset(cx + h * 0.10f, h * 0.15f),
        size = Size(h * 0.06f, h * 0.09f),
        cornerRadius = corner
    )

    val latsColor = muscleColor(MuscleRegion.LATS in trainedMuscles)
    drawRoundRect(
        color = latsColor,
        topLeft = Offset(cx - h * 0.095f, h * 0.15f),
        size = Size(h * 0.19f, h * 0.30f),
        cornerRadius = corner
    )

    val tricepColor = muscleColor(MuscleRegion.TRICEPS in trainedMuscles)
    drawRoundRect(
        color = tricepColor,
        topLeft = Offset(cx - h * 0.17f, h * 0.25f),
        size = Size(h * 0.055f, h * 0.16f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = tricepColor,
        topLeft = Offset(cx + h * 0.115f, h * 0.25f),
        size = Size(h * 0.055f, h * 0.16f),
        cornerRadius = corner
    )

    val hamstringColor = muscleColor(MuscleRegion.HAMSTRINGS in trainedMuscles)
    drawRoundRect(
        color = hamstringColor,
        topLeft = Offset(cx - h * 0.09f, h * 0.48f),
        size = Size(h * 0.08f, h * 0.22f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = hamstringColor,
        topLeft = Offset(cx + h * 0.01f, h * 0.48f),
        size = Size(h * 0.08f, h * 0.22f),
        cornerRadius = corner
    )

    val calvesColor = muscleColor(MuscleRegion.CALVES in trainedMuscles)
    drawRoundRect(
        color = calvesColor,
        topLeft = Offset(cx - h * 0.085f, h * 0.72f),
        size = Size(h * 0.07f, h * 0.20f),
        cornerRadius = corner
    )
    drawRoundRect(
        color = calvesColor,
        topLeft = Offset(cx + h * 0.015f, h * 0.72f),
        size = Size(h * 0.07f, h * 0.20f),
        cornerRadius = corner
    )
}

private fun muscleColor(isTrained: Boolean): Color {
    return if (isTrained) VoltAccent else CardBorder
}

