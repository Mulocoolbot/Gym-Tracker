package com.mulocoolbot.gymtracker.ui.stats

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mulocoolbot.gymtracker.ui.components.StatsBarIcon
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.SuccessGreen
import java.util.Locale
import kotlin.math.hypot

private val StatsCardBg = Color(0xFF14161B)
private val StatsInnerBg = Color(0xFF1A1D24)
private val StatsBorder = Color(0xFF232730)
private val StatsVolt = Color(0xFFCCFF00)
private val StatsMedVolt = Color(0xFF7A9900)
private val StatsZeroMuscle = Color(0xFF232429)
private val StatsWhite = Color(0xFFFFFFFF)
private val StatsMuted = Color(0xFF8C92A4)

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onSelectExercise: (String) -> Unit,
    onToggleExerciseDropdown: () -> Unit = {},
    onDismissExerciseDropdown: () -> Unit = {},
    onSelectChartPoint: (Int) -> Unit = {},
    onSwitchHeatmapOrientation: (HeatmapOrientation) -> Unit = {},
    onSelectMuscleRegion: (String) -> Unit = {},
    onNavigateToLogWorkout: () -> Unit = {}
) {
    if (!uiState.hasStatisticsData) {
        EmptyStatisticsContent(
            onNavigateToLogWorkout = onNavigateToLogWorkout
        )
    } else {
        PopulatedStatisticsContent(
            uiState = uiState,
            onToggleExerciseDropdown = onToggleExerciseDropdown,
            onDismissExerciseDropdown = onDismissExerciseDropdown,
            onSelectExercise = onSelectExercise,
            onSelectChartPoint = onSelectChartPoint,
            onSwitchHeatmapOrientation = onSwitchHeatmapOrientation,
            onSelectMuscleRegion = onSelectMuscleRegion
        )
    }
}

@Composable
private fun EmptyStatisticsContent(
    onNavigateToLogWorkout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Statistics",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = StatsWhite
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = StatsCardBg),
                border = BorderStroke(1.dp, StatsBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(StatsInnerBg)
                            .border(1.dp, StatsBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = StatsBarIcon,
                            contentDescription = "No Statistics Yet",
                            tint = StatsVolt,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "No Statistics Yet",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatsWhite
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Complete your first workout session to unlock e1RM strength charts and your 2D muscle heatmap.",
                        fontSize = 13.sp,
                        color = StatsMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onNavigateToLogWorkout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatsVolt,
                            contentColor = OnVolt
                        )
                    ) {
                        Text(
                            text = "+ Log Workout",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnVolt
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PopulatedStatisticsContent(
    uiState: StatsUiState,
    onToggleExerciseDropdown: () -> Unit,
    onDismissExerciseDropdown: () -> Unit,
    onSelectExercise: (String) -> Unit,
    onSelectChartPoint: (Int) -> Unit,
    onSwitchHeatmapOrientation: (HeatmapOrientation) -> Unit,
    onSelectMuscleRegion: (String) -> Unit
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
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = StatsWhite
        )

        Spacer(modifier = Modifier.height(20.dp))

        StrengthProgressBentoCard(
            uiState = uiState,
            onToggleExerciseDropdown = onToggleExerciseDropdown,
            onDismissExerciseDropdown = onDismissExerciseDropdown,
            onSelectExercise = onSelectExercise,
            onSelectChartPoint = onSelectChartPoint
        )

        Spacer(modifier = Modifier.height(20.dp))

        MuscleHeatmapBentoCard(
            uiState = uiState,
            onSwitchHeatmapOrientation = onSwitchHeatmapOrientation,
            onSelectMuscleRegion = onSelectMuscleRegion
        )
    }
}

@Composable
private fun StrengthProgressBentoCard(
    uiState: StatsUiState,
    onToggleExerciseDropdown: () -> Unit,
    onDismissExerciseDropdown: () -> Unit,
    onSelectExercise: (String) -> Unit,
    onSelectChartPoint: (Int) -> Unit
) {
    val activeOption = uiState.selectedExerciseOption
    val points = activeOption?.points.orEmpty()
    val safeIndex = if (points.isEmpty()) 0 else uiState.selectedPointIndex.coerceIn(0, points.lastIndex)
    val activePoint = points.getOrNull(safeIndex)
    val isPositive = activeOption?.isDeltaPositive ?: true
    val deltaBadgeColor = if (isPositive) SuccessGreen else DangerRed
    val deltaText = activeOption?.deltaKgText ?: "+0 kg"

    var dropdownAnchorWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = StatsCardBg),
        border = BorderStroke(1.dp, StatsBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STRENGTH PROGRESS (e1RM)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = StatsMuted
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(deltaBadgeColor.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = deltaText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = deltaBadgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { dropdownAnchorWidthPx = it.width }
            ) {
                val dropdownBorderColor = if (uiState.isExerciseDropdownOpen) StatsVolt else StatsBorder

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StatsInnerBg)
                        .border(1.dp, dropdownBorderColor, RoundedCornerShape(16.dp))
                        .clickable(onClick = onToggleExerciseDropdown)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatsVolt)
                        )

                        Text(
                            text = activeOption?.exerciseName ?: uiState.selectedExerciseName.ifEmpty { "Select Exercise" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatsWhite
                        )

                        if (activeOption != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StatsCardBg)
                                    .border(1.dp, StatsBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = activeOption.muscleGroup,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatsVolt
                                )
                            }
                        }
                    }

                    ChevronIndicator(isExpanded = uiState.isExerciseDropdownOpen)
                }

                val menuWidth = with(density) { dropdownAnchorWidthPx.toDp() }
                DropdownMenu(
                    expanded = uiState.isExerciseDropdownOpen,
                    onDismissRequest = onDismissExerciseDropdown,
                    modifier = Modifier
                        .let { if (dropdownAnchorWidthPx > 0) it.width(menuWidth) else it }
                        .background(StatsInnerBg)
                        .border(1.dp, StatsVolt, RoundedCornerShape(16.dp))
                ) {
                    uiState.exerciseOptions.forEach { option ->
                        val isSelected = option.exerciseName == uiState.selectedExerciseName
                        DropdownMenuItem(
                            onClick = { onSelectExercise(option.exerciseName) },
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = option.exerciseName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) StatsVolt else StatsWhite
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${option.muscleGroup} · Best ${formatStatNumber(option.bestE1rm)} kg",
                                            fontSize = 11.sp,
                                            color = StatsMuted
                                        )
                                    }

                                    if (isSelected) {
                                        Canvas(modifier = Modifier.size(14.dp)) {
                                            val checkPath = Path().apply {
                                                moveTo(size.width * 0.15f, size.height * 0.52f)
                                                lineTo(size.width * 0.42f, size.height * 0.78f)
                                                lineTo(size.width * 0.85f, size.height * 0.24f)
                                            }
                                            drawPath(
                                                path = checkPath,
                                                color = StatsVolt,
                                                style = Stroke(
                                                    width = 2.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (activePoint != null) {
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(StatsInnerBg)
                        .border(1.dp, StatsBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${activePoint.dateLabel.uppercase(Locale.US)} · SELECTED SET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = StatsMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activePoint.setSummary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatsWhite
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ESTIMATED 1RM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = StatsMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatStatNumber(activePoint.e1rm)} kg",
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = StatsVolt
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppBackground)
                    .border(1.dp, StatsBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                InteractiveE1rmLineChart(
                    points = points,
                    selectedPointIndex = safeIndex,
                    onSelectPoint = onSelectChartPoint
                )
            }
        }
    }
}

@Composable
private fun ChevronIndicator(isExpanded: Boolean) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val path = Path().apply {
            if (isExpanded) {
                moveTo(size.width * 0.2f, size.height * 0.65f)
                lineTo(size.width * 0.5f, size.height * 0.35f)
                lineTo(size.width * 0.8f, size.height * 0.65f)
            } else {
                moveTo(size.width * 0.2f, size.height * 0.35f)
                lineTo(size.width * 0.5f, size.height * 0.65f)
                lineTo(size.width * 0.8f, size.height * 0.35f)
            }
        }
        drawPath(
            path = path,
            color = StatsMuted,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun InteractiveE1rmLineChart(
    points: List<ChartPointUi>,
    selectedPointIndex: Int,
    onSelectPoint: (Int) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val horizontalPadPx = with(density) { 28.dp.toPx() }
    val topPadPx = with(density) { 28.dp.toPx() }
    val bottomPadPx = with(density) { 26.dp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(points, canvasSize) {
                if (points.isEmpty()) return@pointerInput
                detectTapGestures { tapOffset ->
                    val nodeOffsets = computeChartNodeOffsets(
                        points = points,
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        horizontalPadding = horizontalPadPx,
                        topPadding = topPadPx,
                        bottomPadding = bottomPadPx
                    )
                    if (nodeOffsets.isNotEmpty()) {
                        val nearestIndex = nodeOffsets.indices.minByOrNull { idx ->
                            val pt = nodeOffsets[idx]
                            hypot(tapOffset.x - pt.x, tapOffset.y - pt.y)
                        } ?: 0
                        onSelectPoint(nearestIndex)
                    }
                }
            }
    ) {
        val chartWidth = (size.width - horizontalPadPx * 2f).coerceAtLeast(1f)
        val chartHeight = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        for (lineIndex in 0..3) {
            val y = topPadPx + (chartHeight / 3f) * lineIndex
            drawLine(
                color = StatsBorder,
                start = Offset(horizontalPadPx, y),
                end = Offset(size.width - horizontalPadPx, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        }

        if (points.isEmpty()) return@Canvas

        val offsets = computeChartNodeOffsets(
            points = points,
            width = size.width,
            height = size.height,
            horizontalPadding = horizontalPadPx,
            topPadding = topPadPx,
            bottomPadding = bottomPadPx
        )

        if (offsets.size >= 2) {
            val fillPath = Path().apply {
                moveTo(offsets.first().x, topPadPx + chartHeight)
                offsets.forEach { pt -> lineTo(pt.x, pt.y) }
                lineTo(offsets.last().x, topPadPx + chartHeight)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(StatsVolt.copy(alpha = 0.28f), Color.Transparent),
                    startY = topPadPx,
                    endY = topPadPx + chartHeight
                )
            )

            val linePath = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { pt -> lineTo(pt.x, pt.y) }
            }
            drawPath(
                path = linePath,
                color = StatsVolt,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        val valueLabelPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#FFFFFF")
            textAlign = Paint.Align.CENTER
            textSize = 11.dp.toPx()
            typeface = Typeface.MONOSPACE
        }

        val axisLabelPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#8C92A4")
            textAlign = Paint.Align.CENTER
            textSize = 10.dp.toPx()
            typeface = Typeface.MONOSPACE
        }

        offsets.forEachIndexed { index, pointOffset ->
            val isSelected = index == selectedPointIndex
            if (isSelected) {
                drawCircle(
                    color = StatsVolt.copy(alpha = 0.28f),
                    radius = 11.dp.toPx(),
                    center = pointOffset
                )
                drawCircle(
                    color = AppBackground,
                    radius = 6.5.dp.toPx(),
                    center = pointOffset
                )
                drawCircle(
                    color = StatsVolt,
                    radius = 5.dp.toPx(),
                    center = pointOffset
                )
            } else {
                drawCircle(
                    color = AppBackground,
                    radius = 5.5.dp.toPx(),
                    center = pointOffset
                )
                drawCircle(
                    color = StatsVolt,
                    radius = 3.5.dp.toPx(),
                    center = pointOffset
                )
            }

            val point = points[index]
            val nodeLabel = "${formatStatNumber(point.e1rm)}"
            drawContext.canvas.nativeCanvas.drawText(
                nodeLabel,
                pointOffset.x,
                (pointOffset.y - 12.dp.toPx()).coerceAtLeast(14.dp.toPx()),
                valueLabelPaint
            )

            drawContext.canvas.nativeCanvas.drawText(
                point.dateLabel,
                pointOffset.x,
                size.height - 6.dp.toPx(),
                axisLabelPaint
            )
        }
    }
}

private fun computeChartNodeOffsets(
    points: List<ChartPointUi>,
    width: Float,
    height: Float,
    horizontalPadding: Float,
    topPadding: Float,
    bottomPadding: Float
): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val chartWidth = (width - horizontalPadding * 2f).coerceAtLeast(1f)
    val chartHeight = (height - topPadding - bottomPadding).coerceAtLeast(1f)
    val minE1rm = points.minOf { it.e1rm }
    val maxE1rm = points.maxOf { it.e1rm }
    val range = (maxE1rm - minE1rm).takeIf { it > 0.0 } ?: 10.0

    return points.mapIndexed { index, point ->
        val x = if (points.size == 1) {
            width / 2f
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
}

@Composable
private fun MuscleHeatmapBentoCard(
    uiState: StatsUiState,
    onSwitchHeatmapOrientation: (HeatmapOrientation) -> Unit,
    onSelectMuscleRegion: (String) -> Unit
) {
    val selectedMuscle = uiState.muscleVolumes[uiState.selectedMuscleId]
        ?: MuscleVolumeUi(
            id = uiState.selectedMuscleId,
            displayName = uiState.selectedMuscleId,
            orientation = uiState.heatmapOrientation,
            weeklySets = 0
        )

    val selectedVolumeColor = volumeIntensityColor(selectedMuscle.weeklySets)
    val intensityLabel = when {
        selectedMuscle.weeklySets >= 8 -> "High Volume"
        selectedMuscle.weeklySets >= 1 -> "Moderate"
        else -> "Rested"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = StatsCardBg),
        border = BorderStroke(1.dp, StatsBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MUSCLE HEATMAP",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = StatsMuted
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppBackground)
                        .border(1.dp, StatsBorder, RoundedCornerShape(50))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HeatmapOrientationTabButton(
                        label = "Front",
                        isSelected = uiState.heatmapOrientation == HeatmapOrientation.FRONT,
                        onClick = { onSwitchHeatmapOrientation(HeatmapOrientation.FRONT) }
                    )
                    HeatmapOrientationTabButton(
                        label = "Back",
                        isSelected = uiState.heatmapOrientation == HeatmapOrientation.BACK,
                        onClick = { onSwitchHeatmapOrientation(HeatmapOrientation.BACK) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StatsInnerBg)
                    .border(1.dp, Color(0x66CCFF00), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(selectedVolumeColor)
                    )

                    Column {
                        Text(
                            text = selectedMuscle.displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatsWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${selectedMuscle.weeklySets} Sets This Week",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatsVolt
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(StatsCardBg)
                        .border(1.dp, StatsBorder, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = intensityLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedMuscle.weeklySets > 0) StatsVolt else StatsMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppBackground)
                    .border(1.dp, StatsBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDotItem(label = "High", color = StatsVolt)
                    LegendDotItem(label = "Med", color = StatsMedVolt)
                    LegendDotItem(label = "0", color = StatsZeroMuscle)
                }

                InteractiveAnatomicalCanvas(
                    orientation = uiState.heatmapOrientation,
                    selectedMuscleId = uiState.selectedMuscleId,
                    muscleVolumes = uiState.muscleVolumes,
                    onSelectMuscleRegion = onSelectMuscleRegion
                )
            }
        }
    }
}

@Composable
private fun HeatmapOrientationTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) StatsVolt else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) OnVolt else StatsMuted
        )
    }
}

@Composable
private fun LegendDotItem(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = StatsMuted
        )
    }
}

@Composable
private fun InteractiveAnatomicalCanvas(
    orientation: HeatmapOrientation,
    selectedMuscleId: String,
    muscleVolumes: Map<String, MuscleVolumeUi>,
    onSelectMuscleRegion: (String) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(orientation) {
                detectTapGestures { tapOffset ->
                    val regions = buildAnatomicalRegions(
                        orientation = orientation,
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )
                    val directHit = regions.firstOrNull { (_, rect) ->
                        rect.contains(tapOffset)
                    }?.first
                    if (directHit != null) {
                        onSelectMuscleRegion(directHit)
                    } else {
                        val nearest = regions.minByOrNull { (_, rect) ->
                            hypot(tapOffset.x - rect.center.x, tapOffset.y - rect.center.y)
                        }
                        if (nearest != null && hypot(tapOffset.x - nearest.second.center.x, tapOffset.y - nearest.second.center.y) <= size.height * 0.18f) {
                            onSelectMuscleRegion(nearest.first)
                        }
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val h = size.height

        drawCircle(
            color = StatsZeroMuscle,
            radius = h * 0.052f,
            center = Offset(cx, h * 0.065f)
        )

        val regions = buildAnatomicalRegions(
            orientation = orientation,
            width = size.width,
            height = size.height
        )

        regions.forEach { (muscleId, rect) ->
            val sets = muscleVolumes[muscleId]?.weeklySets ?: 0
            val fillColor = volumeIntensityColor(sets)
            val isSelected = muscleId == selectedMuscleId
            drawMuscleRegionRect(
                rect = rect,
                fillColor = fillColor,
                isSelected = isSelected
            )
        }
    }
}

private fun DrawScope.drawMuscleRegionRect(
    rect: Rect,
    fillColor: Color,
    isSelected: Boolean
) {
    val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())
    drawRoundRect(
        color = fillColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = corner
    )
    if (isSelected) {
        drawRoundRect(
            color = StatsWhite,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = corner,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

private fun buildAnatomicalRegions(
    orientation: HeatmapOrientation,
    width: Float,
    height: Float
): List<Pair<String, Rect>> {
    val cx = width / 2f
    val h = height

    return if (orientation == HeatmapOrientation.FRONT) {
        listOf(
            "SHOULDERS" to Rect(cx - h * 0.175f, h * 0.14f, cx - h * 0.105f, h * 0.24f),
            "SHOULDERS" to Rect(cx + h * 0.105f, h * 0.14f, cx + h * 0.175f, h * 0.24f),
            "CHEST" to Rect(cx - h * 0.095f, h * 0.14f, cx - h * 0.006f, h * 0.26f),
            "CHEST" to Rect(cx + h * 0.006f, h * 0.14f, cx + h * 0.095f, h * 0.26f),
            "BICEPS" to Rect(cx - h * 0.18f, h * 0.255f, cx - h * 0.115f, h * 0.42f),
            "BICEPS" to Rect(cx + h * 0.115f, h * 0.255f, cx + h * 0.18f, h * 0.42f),
            "ABS" to Rect(cx - h * 0.08f, h * 0.275f, cx + h * 0.08f, h * 0.475f),
            "QUADS" to Rect(cx - h * 0.095f, h * 0.495f, cx - h * 0.01f, h * 0.82f),
            "QUADS" to Rect(cx + h * 0.01f, h * 0.495f, cx + h * 0.095f, h * 0.82f)
        )
    } else {
        listOf(
            "TRAPS" to Rect(cx - h * 0.09f, h * 0.125f, cx + h * 0.09f, h * 0.19f),
            "REAR_SHOULDERS" to Rect(cx - h * 0.175f, h * 0.14f, cx - h * 0.10f, h * 0.23f),
            "REAR_SHOULDERS" to Rect(cx + h * 0.10f, h * 0.14f, cx + h * 0.175f, h * 0.23f),
            "LATS" to Rect(cx - h * 0.095f, h * 0.20f, cx - h * 0.006f, h * 0.35f),
            "LATS" to Rect(cx + h * 0.006f, h * 0.20f, cx + h * 0.095f, h * 0.35f),
            "TRICEPS" to Rect(cx - h * 0.18f, h * 0.245f, cx - h * 0.115f, h * 0.41f),
            "TRICEPS" to Rect(cx + h * 0.115f, h * 0.245f, cx + h * 0.18f, h * 0.41f),
            "LOWER_BACK" to Rect(cx - h * 0.075f, h * 0.36f, cx + h * 0.075f, h * 0.44f),
            "GLUTES" to Rect(cx - h * 0.09f, h * 0.45f, cx - h * 0.006f, h * 0.545f),
            "GLUTES" to Rect(cx + h * 0.006f, h * 0.45f, cx + h * 0.09f, h * 0.545f),
            "HAMSTRINGS" to Rect(cx - h * 0.09f, h * 0.555f, cx - h * 0.01f, h * 0.745f),
            "HAMSTRINGS" to Rect(cx + h * 0.01f, h * 0.555f, cx + h * 0.09f, h * 0.745f),
            "CALVES" to Rect(cx - h * 0.085f, h * 0.755f, cx - h * 0.015f, h * 0.93f),
            "CALVES" to Rect(cx + h * 0.015f, h * 0.755f, cx + h * 0.085f, h * 0.93f)
        )
    }
}

private fun volumeIntensityColor(weeklySets: Int): Color = when {
    weeklySets >= 8 -> StatsVolt
    weeklySets >= 1 -> StatsMedVolt
    else -> StatsZeroMuscle
}

private fun formatStatNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.1f".format(Locale.US, value)
    }
}
