package com.mulocoolbot.gymtracker.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mulocoolbot.gymtracker.ui.components.StatsBarIcon
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.SuccessGreen
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialTargetId
import com.mulocoolbot.gymtracker.ui.tutorial.tutorialTarget
import com.mulocoolbot.gymtracker.util.formatCompactNumber
import kotlin.math.ceil
import kotlin.math.hypot

@Composable
fun StatsScreen(
    uiState: StatsUiState,
    onSelectExercise: (String) -> Unit,
    onToggleExerciseDropdown: () -> Unit = {},
    onDismissExerciseDropdown: () -> Unit = {},
    onSelectChartPoint: (Int) -> Unit = {},
    onSwitchHeatmapOrientation: (HeatmapOrientation) -> Unit = {},
    onSelectMuscleRegion: (String) -> Unit = {},
    onNavigateToLogWorkout: () -> Unit = {},
    isTutorialPreview: Boolean = false
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
            onSelectMuscleRegion = onSelectMuscleRegion,
            isTutorialPreview = isTutorialPreview
        )
    }
}

@Composable
private fun EmptyStatisticsContent(
    onNavigateToLogWorkout: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Statistics",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .tutorialTarget(TutorialTargetId.STATS_OVERVIEW_AREA),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(InputSurface)
                                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = StatsBarIcon,
                                    contentDescription = "No Statistics Yet",
                                    tint = VoltAccent,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "No Statistics Yet",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Complete your first workout session to unlock e1RM strength charts and your 2D muscle heatmap.",
                                fontSize = 13.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onNavigateToLogWorkout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoltAccent,
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

            Spacer(modifier = Modifier.height(1.dp))
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
    onSelectMuscleRegion: (String) -> Unit,
    isTutorialPreview: Boolean = false
) {
    val outerVerticalPadding = if (isTutorialPreview) 12.dp else 24.dp
    val sectionGap = if (isTutorialPreview) 10.dp else 20.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = outerVerticalPadding)
    ) {
        Text(
            text = "Statistics",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(sectionGap))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .tutorialTarget(TutorialTargetId.STATS_OVERVIEW_AREA)
        ) {
            StrengthProgressBentoCard(
                uiState = uiState,
                onToggleExerciseDropdown = onToggleExerciseDropdown,
                onDismissExerciseDropdown = onDismissExerciseDropdown,
                onSelectExercise = onSelectExercise,
                onSelectChartPoint = onSelectChartPoint,
                isTutorialPreview = isTutorialPreview
            )

            Spacer(modifier = Modifier.height(sectionGap))

            MuscleHeatmapBentoCard(
                uiState = uiState,
                onSwitchHeatmapOrientation = onSwitchHeatmapOrientation,
                onSelectMuscleRegion = onSelectMuscleRegion,
                isTutorialPreview = isTutorialPreview
            )
        }
    }
}

@Composable
private fun StrengthProgressBentoCard(
    uiState: StatsUiState,
    onToggleExerciseDropdown: () -> Unit,
    onDismissExerciseDropdown: () -> Unit,
    onSelectExercise: (String) -> Unit,
    onSelectChartPoint: (Int) -> Unit,
    isTutorialPreview: Boolean = false
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

    val cardPadding = if (isTutorialPreview) 14.dp else 20.dp
    val innerGap = if (isTutorialPreview) 8.dp else 14.dp
    val selectedSetPadding = if (isTutorialPreview) 10.dp else 14.dp
    val chartHeight = if (isTutorialPreview) 116.dp else 220.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
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
                        color = TextMuted
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

                Spacer(modifier = Modifier.height(innerGap))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { dropdownAnchorWidthPx = it.width }
                ) {
                    val dropdownBorderColor = if (uiState.isExerciseDropdownOpen) VoltAccent else CardBorder

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(InputSurface)
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
                                    .background(VoltAccent)
                            )

                            Text(
                                text = activeOption?.exerciseName ?: uiState.selectedExerciseName.ifEmpty { "Select Exercise" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            if (activeOption != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardSurface)
                                        .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = activeOption.muscleGroup,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VoltAccent
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
                            .background(InputSurface)
                            .border(1.dp, VoltAccent, RoundedCornerShape(16.dp))
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
                                                color = if (isSelected) VoltAccent else TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${option.muscleGroup} · Best ${formatCompactNumber(option.bestE1rm)} kg",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextMuted
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
                                                    color = VoltAccent,
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
            }

            if (activePoint != null) {
                Spacer(modifier = Modifier.height(innerGap))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(InputSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(selectedSetPadding),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${activePoint.dateLabel.uppercase()} · SELECTED SET",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activePoint.setSummary,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ESTIMATED 1RM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatCompactNumber(activePoint.e1rm)} kg",
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = VoltAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(innerGap))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppBackground)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
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
            color = TextMuted,
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
    val textMeasurer = rememberTextMeasurer()
    val horizontalPadPx = with(density) { 28.dp.toPx() }
    val topPadPx = with(density) { 28.dp.toPx() }
    val bottomPadPx = with(density) { 26.dp.toPx() }

    val valueLabelStyle = remember {
        TextStyle(
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
    val axisLabelStyle = remember {
        TextStyle(
            color = TextMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Normal
        )
    }

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
                color = CardBorder,
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
                    colors = listOf(VoltAccent.copy(alpha = 0.28f), Color.Transparent),
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
                color = VoltAccent,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        val visibleLabelIndices = computeVisibleChartLabelIndices(
            pointCount = points.size,
            selectedIndex = selectedPointIndex,
            chartWidthPx = chartWidth,
            minSpacingPx = 44.dp.toPx()
        )

        offsets.forEachIndexed { index, pointOffset ->
            val isSelected = index == selectedPointIndex
            if (isSelected) {
                drawCircle(
                    color = VoltAccent.copy(alpha = 0.28f),
                    radius = 11.dp.toPx(),
                    center = pointOffset
                )
                drawCircle(
                    color = AppBackground,
                    radius = 6.5.dp.toPx(),
                    center = pointOffset
                )
                drawCircle(
                    color = VoltAccent,
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
                    color = VoltAccent,
                    radius = 3.5.dp.toPx(),
                    center = pointOffset
                )
            }

            val point = points[index]
            if (index in visibleLabelIndices || isSelected) {
                val valueLayout = textMeasurer.measure(
                    text = formatCompactNumber(point.e1rm),
                    style = valueLabelStyle
                )
                val valueX = (pointOffset.x - valueLayout.size.width / 2f)
                    .coerceIn(0f, (size.width - valueLayout.size.width).coerceAtLeast(0f))
                val valueY = (pointOffset.y - 10.dp.toPx() - valueLayout.size.height)
                    .coerceAtLeast(2.dp.toPx())
                drawText(
                    textLayoutResult = valueLayout,
                    topLeft = Offset(valueX, valueY)
                )
            }

            if (index in visibleLabelIndices) {
                val axisLayout = textMeasurer.measure(
                    text = point.dateLabel,
                    style = axisLabelStyle
                )
                val axisX = (pointOffset.x - axisLayout.size.width / 2f)
                    .coerceIn(0f, (size.width - axisLayout.size.width).coerceAtLeast(0f))
                val axisY = (size.height - axisLayout.size.height - 2.dp.toPx())
                    .coerceAtLeast(0f)
                drawText(
                    textLayoutResult = axisLayout,
                    topLeft = Offset(axisX, axisY)
                )
            }
        }
    }
}

internal fun computeVisibleChartLabelIndices(
    pointCount: Int,
    selectedIndex: Int,
    chartWidthPx: Float,
    minSpacingPx: Float
): Set<Int> {
    if (pointCount <= 0) return emptySet()
    if (pointCount <= 2) return (0 until pointCount).toSet()
    val safeChartWidth = chartWidthPx.coerceAtLeast(1f)
    val stepPx = safeChartWidth / (pointCount - 1).toFloat()
    val stride = if (stepPx >= minSpacingPx || minSpacingPx <= 0f) {
        1
    } else {
        ceil(minSpacingPx / stepPx).toInt().coerceAtLeast(1)
    }
    val visible = linkedSetOf(0, pointCount - 1)
    if (selectedIndex in 0 until pointCount) {
        visible.add(selectedIndex)
    }
    for (i in 0 until pointCount step stride) {
        visible.add(i)
    }
    return visible
}

internal fun computeChartNodeOffsets(
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
    onSelectMuscleRegion: (String) -> Unit,
    isTutorialPreview: Boolean = false
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

    val cardPadding = if (isTutorialPreview) 14.dp else 20.dp
    val innerGap = if (isTutorialPreview) 8.dp else 14.dp
    val selectedBoxPadding = if (isTutorialPreview) 10.dp else 14.dp
    val canvasBoxPadding = if (isTutorialPreview) 10.dp else 16.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding)
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
                    color = TextMuted
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppBackground)
                        .border(1.dp, CardBorder, RoundedCornerShape(50))
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

            Spacer(modifier = Modifier.height(innerGap))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(InputSurface)
                    .border(1.dp, VoltAccent.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
                    .padding(selectedBoxPadding),
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
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${selectedMuscle.weeklySets} Sets This Week",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = VoltAccent
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CardSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = intensityLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedMuscle.weeklySets > 0) VoltAccent else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(innerGap))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppBackground)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(canvasBoxPadding)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendDotItem(label = "High", color = VoltAccent)
                    Spacer(modifier = Modifier.width(10.dp))
                    LegendDotItem(label = "Med", color = VoltAccent.copy(alpha = 0.45f))
                    Spacer(modifier = Modifier.width(10.dp))
                    LegendDotItem(label = "0", color = CardBorder)
                }

                Spacer(modifier = Modifier.height(6.dp))

                InteractiveAnatomicalCanvas(
                    orientation = uiState.heatmapOrientation,
                    selectedMuscleId = uiState.selectedMuscleId,
                    muscleVolumes = uiState.muscleVolumes,
                    onSelectMuscleRegion = onSelectMuscleRegion,
                    isTutorialPreview = isTutorialPreview
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
            .defaultMinSize(minWidth = 56.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (isSelected) VoltAccent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) OnVolt else TextMuted
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
            color = TextMuted
        )
    }
}

@Composable
private fun InteractiveAnatomicalCanvas(
    orientation: HeatmapOrientation,
    selectedMuscleId: String,
    muscleVolumes: Map<String, MuscleVolumeUi>,
    onSelectMuscleRegion: (String) -> Unit,
    isTutorialPreview: Boolean = false
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTutorialPreview) 144.dp else 228.dp)
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
        val scale = minOf(size.height, size.width / 0.48f)
        val topOffset = (size.height - scale) / 2f

        drawCircle(
            color = CardBorder,
            radius = scale * 0.052f,
            center = Offset(cx, topOffset + scale * 0.065f)
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
            color = TextPrimary,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = corner,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

internal fun buildAnatomicalRegions(
    orientation: HeatmapOrientation,
    width: Float,
    height: Float
): List<Pair<String, Rect>> {
    val cx = width / 2f
    val s = minOf(height, width / 0.48f)
    val top = (height - s) / 2f

    return if (orientation == HeatmapOrientation.FRONT) {
        listOf(
            "SHOULDERS" to Rect(cx - s * 0.175f, top + s * 0.14f, cx - s * 0.105f, top + s * 0.24f),
            "SHOULDERS" to Rect(cx + s * 0.105f, top + s * 0.14f, cx + s * 0.175f, top + s * 0.24f),
            "CHEST" to Rect(cx - s * 0.095f, top + s * 0.14f, cx - s * 0.006f, top + s * 0.26f),
            "CHEST" to Rect(cx + s * 0.006f, top + s * 0.14f, cx + s * 0.095f, top + s * 0.26f),
            "BICEPS" to Rect(cx - s * 0.18f, top + s * 0.255f, cx - s * 0.115f, top + s * 0.42f),
            "BICEPS" to Rect(cx + s * 0.115f, top + s * 0.255f, cx + s * 0.18f, top + s * 0.42f),
            "ABS" to Rect(cx - s * 0.08f, top + s * 0.275f, cx + s * 0.08f, top + s * 0.475f),
            "QUADS" to Rect(cx - s * 0.095f, top + s * 0.495f, cx - s * 0.01f, top + s * 0.82f),
            "QUADS" to Rect(cx + s * 0.01f, top + s * 0.495f, cx + s * 0.095f, top + s * 0.82f)
        )
    } else {
        listOf(
            "TRAPS" to Rect(cx - s * 0.09f, top + s * 0.125f, cx + s * 0.09f, top + s * 0.19f),
            "REAR_SHOULDERS" to Rect(cx - s * 0.175f, top + s * 0.14f, cx - s * 0.10f, top + s * 0.23f),
            "REAR_SHOULDERS" to Rect(cx + s * 0.10f, top + s * 0.14f, cx + s * 0.175f, top + s * 0.23f),
            "LATS" to Rect(cx - s * 0.095f, top + s * 0.20f, cx - s * 0.006f, top + s * 0.35f),
            "LATS" to Rect(cx + s * 0.006f, top + s * 0.20f, cx + s * 0.095f, top + s * 0.35f),
            "TRICEPS" to Rect(cx - s * 0.18f, top + s * 0.245f, cx - s * 0.115f, top + s * 0.41f),
            "TRICEPS" to Rect(cx + s * 0.115f, top + s * 0.245f, cx + s * 0.18f, top + s * 0.41f),
            "LOWER_BACK" to Rect(cx - s * 0.075f, top + s * 0.36f, cx + s * 0.075f, top + s * 0.44f),
            "GLUTES" to Rect(cx - s * 0.09f, top + s * 0.45f, cx - s * 0.006f, top + s * 0.545f),
            "GLUTES" to Rect(cx + s * 0.006f, top + s * 0.45f, cx + s * 0.09f, top + s * 0.545f),
            "HAMSTRINGS" to Rect(cx - s * 0.09f, top + s * 0.555f, cx - s * 0.01f, top + s * 0.745f),
            "HAMSTRINGS" to Rect(cx + s * 0.01f, top + s * 0.555f, cx + s * 0.09f, top + s * 0.745f),
            "CALVES" to Rect(cx - s * 0.085f, top + s * 0.755f, cx - s * 0.015f, top + s * 0.93f),
            "CALVES" to Rect(cx + s * 0.015f, top + s * 0.755f, cx + s * 0.085f, top + s * 0.93f)
        )
    }
}

private fun volumeIntensityColor(weeklySets: Int): Color = when {
    weeklySets >= 8 -> VoltAccent
    weeklySets >= 1 -> VoltAccent.copy(alpha = 0.45f)
    else -> CardBorder
}
