package com.mulocoolbot.gymtracker.ui.tutorial

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.TutorialScrimFallback
import com.mulocoolbot.gymtracker.ui.theme.TutorialScrimWithBlur
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import kotlin.math.roundToInt

@Composable
fun TutorialOverlay(
    uiState: TutorialUiState,
    targetBounds: SnapshotStateMap<TutorialTargetId, Rect>,
    backdropLayer: GraphicsLayer?,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit
) {
    val step = uiState.currentStep
    val density = LocalDensity.current
    val safeDrawingInsets = WindowInsets.safeDrawing
    val topSafeInsetPx = safeDrawingInsets.getTop(density).toFloat()
    val bottomSafeInsetPx = safeDrawingInsets.getBottom(density).toFloat()

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayOffsetInRoot by remember { mutableStateOf(Offset.Zero) }
    var cardHeightPx by remember { mutableStateOf(with(density) { 178.dp.toPx() }) }
    var lastValidRect by remember { mutableStateOf<Rect?>(null) }

    val rawTarget = targetBounds[step.targetId]
    val resolvedTarget = if (rawTarget != null && rawTarget.width > 0f && rawTarget.height > 0f) {
        rawTarget.translate(-overlayOffsetInRoot.x, -overlayOffsetInRoot.y)
    } else {
        null
    }
    if (resolvedTarget != null) {
        lastValidRect = resolvedTarget
    }
    val activeTargetRect = resolvedTarget ?: lastValidRect

    val padPx = with(density) { 6.dp.toPx() }
    val maxCornerPx = with(density) { 28.dp.toPx() }
    val connectorGapPx = with(density) { 20.dp.toPx() }
    val edgeMarginPx = with(density) { 14.dp.toPx() }
    val cardHorizontalMarginPx = with(density) { 20.dp.toPx() }
    val cardAnchorInsetPx = with(density) { 28.dp.toPx() }
    val minConnectorGapPx = with(density) { 4.dp.toPx() }

    val targetLayout = computeTutorialOverlayLayout(
        containerWidth = containerSize.width.toFloat(),
        containerHeight = containerSize.height.toFloat(),
        targetRect = activeTargetRect,
        cardHeightPx = cardHeightPx,
        topSafeInsetPx = topSafeInsetPx,
        bottomSafeInsetPx = bottomSafeInsetPx,
        padPx = padPx,
        maxCornerPx = maxCornerPx,
        connectorGapPx = connectorGapPx,
        edgeMarginPx = edgeMarginPx,
        cardHorizontalMarginPx = cardHorizontalMarginPx,
        cardAnchorInsetPx = cardAnchorInsetPx,
        minConnectorGapPx = minConnectorGapPx
    )

    val animatedSpotlight by animateRectAsState(
        targetValue = targetLayout.spotlightRect,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "TutorialSpotlightRect"
    )

    val animatedCardTop by animateFloatAsState(
        targetValue = targetLayout.cardTopPx,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "TutorialCardTop"
    )

    val scrimColor = if (backdropLayer != null) TutorialScrimWithBlur else TutorialScrimFallback

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                containerSize = coordinates.size
                overlayOffsetInRoot = coordinates.positionInRoot()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    if (targetLayout.hasSpotlight && animatedSpotlight.width > 2f && animatedSpotlight.height > 2f) {
                        val corner = minOf(maxCornerPx, animatedSpotlight.width / 2f, animatedSpotlight.height / 2f)
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = animatedSpotlight.topLeft,
                            size = animatedSpotlight.size,
                            cornerRadius = CornerRadius(corner, corner),
                            blendMode = BlendMode.Clear
                        )
                    }
                }
        ) {
            if (backdropLayer != null) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 10.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                ) {
                    if (backdropLayer.size.width > 0 && backdropLayer.size.height > 0) {
                        drawLayer(backdropLayer)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimColor)
            )
        }

        if (targetLayout.hasSpotlight && animatedSpotlight.width > 2f && animatedSpotlight.height > 2f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val corner = minOf(maxCornerPx, animatedSpotlight.width / 2f, animatedSpotlight.height / 2f)
                drawRoundRect(
                    color = VoltAccent,
                    topLeft = animatedSpotlight.topLeft,
                    size = animatedSpotlight.size,
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 2.dp.toPx())
                )

                val cardLeft = cardHorizontalMarginPx
                val cardRight = (size.width - cardHorizontalMarginPx).coerceAtLeast(cardLeft + 1f)
                val anchorX = animatedSpotlight.center.x.coerceIn(
                    (cardLeft + cardAnchorInsetPx).coerceAtMost(cardRight),
                    (cardRight - cardAnchorInsetPx).coerceAtLeast(cardLeft)
                )

                val ringAnchor = if (targetLayout.placeCardBelow) {
                    Offset(animatedSpotlight.center.x, animatedSpotlight.bottom)
                } else {
                    Offset(animatedSpotlight.center.x, animatedSpotlight.top)
                }

                val cardAnchor = if (targetLayout.placeCardBelow) {
                    Offset(anchorX, animatedCardTop)
                } else {
                    Offset(anchorX, animatedCardTop + cardHeightPx)
                }

                val signedGap = if (targetLayout.placeCardBelow) {
                    cardAnchor.y - ringAnchor.y
                } else {
                    ringAnchor.y - cardAnchor.y
                }

                if (signedGap >= minConnectorGapPx) {
                    drawLine(
                        color = VoltAccent,
                        start = cardAnchor,
                        end = ringAnchor,
                        strokeWidth = 1.75.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = VoltAccent,
                        radius = 3.5.dp.toPx(),
                        center = ringAnchor
                    )
                    drawCircle(
                        color = VoltAccent,
                        radius = 2.5.dp.toPx(),
                        center = cardAnchor
                    )
                }
            }
        }

        TutorialExplanationCard(
            uiState = uiState,
            onSkip = onSkip,
            onNext = onNext,
            onDone = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .offset { IntOffset(0, animatedCardTop.roundToInt()) }
                .onSizeChanged { measured ->
                    if (measured.height > 0) {
                        cardHeightPx = measured.height.toFloat()
                    }
                }
        )
    }
}

@Composable
private fun TutorialExplanationCard(
    uiState: TutorialUiState,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step = uiState.currentStep

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.stepNumber >= initialState.stepNumber
                    val slideInOffset = { width: Int -> if (forward) width / 8 else -width / 8 }
                    val slideOutOffset = { width: Int -> if (forward) -width / 8 else width / 8 }
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220), slideInOffset))
                        .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150), slideOutOffset))
                },
                label = "TutorialCardContent"
            ) { currentStep ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentStep.tab.label.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = TextMuted
                        )

                        Text(
                            text = currentStep.indicatorText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = VoltAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentStep.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = currentStep.description,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (uiState.showDoneButton) {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoltAccent,
                        contentColor = OnVolt
                    )
                ) {
                    Text(
                        text = "Done",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnVolt
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.showSkipButton) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
                        ) {
                            Text(
                                text = "Skip",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted
                            )
                        }
                    }

                    if (uiState.showNextButton) {
                        Button(
                            onClick = onNext,
                            modifier = Modifier.height(44.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VoltAccent,
                                contentColor = OnVolt
                            )
                        ) {
                            Text(
                                text = "Next",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnVolt
                            )
                        }
                    }
                }
            }
        }
    }
}
