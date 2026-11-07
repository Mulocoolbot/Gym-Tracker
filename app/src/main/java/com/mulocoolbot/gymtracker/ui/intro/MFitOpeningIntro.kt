package com.mulocoolbot.gymtracker.ui.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val IntroBackground = Color(0xFF0B0C0E)
private val IntroVolt = Color(0xFFCCFF00)
private val IntroWhite = Color(0xFFFFFFFF)

data class CrackBranch(
    val normalizedPoints: List<Offset>,
    val strokeWidthDp: Float
)

data class SparkParticle(
    val startTimeMs: Long,
    val initialVelocityX: Float,
    val initialVelocityY: Float,
    val radiusDp: Float
)

object MFitOpeningIntroTimeline {
    const val TOTAL_DURATION_MS = 2850L
    const val INNER_DROP_START_MS = 120L
    const val INNER_IMPACT_MS = 420L
    const val OUTER_DROP_START_MS = 340L
    const val OUTER_IMPACT_MS = 640L
    const val ROD_START_MS = 1080L
    const val METALLIC_LOCK_MS = 1220L
    const val ROD_END_MS = 1300L
    const val CRACK_FADE_START_MS = 1650L
    const val CRACK_FADE_END_MS = 2100L
    const val LEVITATE_START_MS = 1700L
    const val LEVITATE_END_MS = 2150L
    const val TEXT_REVEAL_START_MS = 1750L
    const val TEXT_REVEAL_END_MS = 2250L

    fun computeGravityDropProgress(
        elapsedMs: Long,
        startMs: Long,
        impactMs: Long
    ): Float {
        if (elapsedMs <= startMs) return 0f
        if (elapsedMs >= impactMs) return 1f
        val linear = (elapsedMs - startMs).toFloat() / (impactMs - startMs).toFloat()
        return (linear * linear).coerceIn(0f, 1f)
    }

    fun computeImpactSquash(
        elapsedMs: Long,
        impactMs: Long
    ): Float {
        val dt = elapsedMs - impactMs
        if (dt < 0L || dt > 220L) return 0f
        val normalized = dt.toFloat() / 220f
        return (sin(normalized * PI) * (1f - normalized) * 0.22f).toFloat().coerceIn(0f, 0.22f)
    }

    fun computeRodProgress(elapsedMs: Long): Float {
        if (elapsedMs <= ROD_START_MS) return 0f
        if (elapsedMs >= ROD_END_MS) return 1f
        val t = (elapsedMs - ROD_START_MS).toFloat() / (ROD_END_MS - ROD_START_MS).toFloat()
        return (1f - (1f - t).pow(3)).coerceIn(0f, 1f)
    }

    fun computeLevitationProgress(elapsedMs: Long): Float {
        if (elapsedMs <= LEVITATE_START_MS) return 0f
        if (elapsedMs >= LEVITATE_END_MS) return 1f
        val t = (elapsedMs - LEVITATE_START_MS).toFloat() / (LEVITATE_END_MS - LEVITATE_START_MS).toFloat()
        return (t * t * (3f - 2f * t)).coerceIn(0f, 1f)
    }

    fun computeTextRevealProgress(elapsedMs: Long): Float {
        if (elapsedMs <= TEXT_REVEAL_START_MS) return 0f
        if (elapsedMs >= TEXT_REVEAL_END_MS) return 1f
        val t = (elapsedMs - TEXT_REVEAL_START_MS).toFloat() / (TEXT_REVEAL_END_MS - TEXT_REVEAL_START_MS).toFloat()
        return (1f - (1f - t).pow(3)).coerceIn(0f, 1f)
    }

    fun computeCrackAlpha(elapsedMs: Long): Float {
        if (elapsedMs < INNER_IMPACT_MS) return 0f
        if (elapsedMs <= CRACK_FADE_START_MS) return 1f
        if (elapsedMs >= CRACK_FADE_END_MS) return 0f
        val t = (elapsedMs - CRACK_FADE_START_MS).toFloat() /
            (CRACK_FADE_END_MS - CRACK_FADE_START_MS).toFloat()
        return (1f - t).coerceIn(0f, 1f)
    }

    fun computeCameraShakeOffset(elapsedMs: Long): Offset {
        var dx = 0f
        var dy = 0f

        fun accumulateImpulse(triggerMs: Long, amplitudeX: Float, amplitudeY: Float, durationMs: Long) {
            val dt = elapsedMs - triggerMs
            if (dt in 0L..durationMs) {
                val decay = exp(-dt.toFloat() / (durationMs.toFloat() * 0.32f))
                val waveX = sin(dt.toFloat() * 0.14f)
                val waveY = cos(dt.toFloat() * 0.19f)
                dx += waveX * amplitudeX * decay
                dy += waveY * amplitudeY * decay
            }
        }

        accumulateImpulse(INNER_IMPACT_MS, amplitudeX = 6f, amplitudeY = 9f, durationMs = 240L)
        accumulateImpulse(OUTER_IMPACT_MS, amplitudeX = 9f, amplitudeY = 12f, durationMs = 280L)
        accumulateImpulse(METALLIC_LOCK_MS, amplitudeX = 5f, amplitudeY = 3.5f, durationMs = 180L)

        return Offset(dx, dy)
    }

    fun buildDeterministicCracks(): List<CrackBranch> {
        val random = Random(108)
        val anglesDeg = listOf(-168f, -142f, -112f, -68f, -36f, -12f, 14f, 38f, 72f, 118f, 148f, 172f)
        return anglesDeg.mapIndexed { idx, baseAngleDeg ->
            val segments = if (idx % 2 == 0) 5 else 4
            val baseRad = Math.toRadians(baseAngleDeg.toDouble())
            val points = mutableListOf(Offset.Zero)
            var currentX = 0f
            var currentY = 0f

            for (step in 1..segments) {
                val stepDist = (18f + step * 11f) + random.nextFloat() * 8f
                val jitterRad = (random.nextFloat() - 0.5f) * 0.42f
                val angle = baseRad + jitterRad
                currentX += (cos(angle) * stepDist).toFloat()
                currentY += (sin(angle) * stepDist * 0.46).toFloat()
                points.add(Offset(currentX, currentY))
            }

            CrackBranch(
                normalizedPoints = points,
                strokeWidthDp = if (idx % 3 == 0) 2.2f else 1.5f
            )
        }
    }

    fun buildDeterministicSparks(): List<SparkParticle> {
        val random = Random(77)
        val sparks = mutableListOf<SparkParticle>()

        repeat(14) { i ->
            val startMs = if (i < 7) INNER_IMPACT_MS else OUTER_IMPACT_MS
            val side = if (i % 2 == 0) -1f else 1f
            val vx = side * (65f + random.nextFloat() * 120f)
            val vy = -(95f + random.nextFloat() * 145f)
            val radius = 1.8f + random.nextFloat() * 1.6f
            sparks.add(
                SparkParticle(
                    startTimeMs = startMs,
                    initialVelocityX = vx,
                    initialVelocityY = vy,
                    radiusDp = radius
                )
            )
        }
        return sparks
    }
}

@Composable
fun MFitOpeningIntro(
    onOpeningFinished: () -> Unit
) {
    val context = LocalContext.current
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var triggeredInnerBoom by remember { mutableStateOf(false) }
    var triggeredOuterBoom by remember { mutableStateOf(false) }
    var triggeredMetallicLock by remember { mutableStateOf(false) }

    val cracks = remember { MFitOpeningIntroTimeline.buildDeterministicCracks() }
    val sparks = remember { MFitOpeningIntroTimeline.buildDeterministicSparks() }

    LaunchedEffect(Unit) {
        val startFrameMs = withFrameMillis { it }
        while (elapsedMs < MFitOpeningIntroTimeline.TOTAL_DURATION_MS) {
            val frameMs = withFrameMillis { it }
            elapsedMs = (frameMs - startFrameMs).coerceAtLeast(0L)

            if (!triggeredInnerBoom && elapsedMs >= MFitOpeningIntroTimeline.INNER_IMPACT_MS) {
                triggeredInnerBoom = true
                OpeningSoundEffects.playHeavyBoom(context, 0.95f)
            }
            if (!triggeredOuterBoom && elapsedMs >= MFitOpeningIntroTimeline.OUTER_IMPACT_MS) {
                triggeredOuterBoom = true
                OpeningSoundEffects.playHeavyBoom(context, 1.15f)
            }
            if (!triggeredMetallicLock && elapsedMs >= MFitOpeningIntroTimeline.METALLIC_LOCK_MS) {
                triggeredMetallicLock = true
                OpeningSoundEffects.playMetallicLock(context)
            }
        }
        onOpeningFinished()
    }

    val textReveal = MFitOpeningIntroTimeline.computeTextRevealProgress(elapsedMs)
    val textSlideY = ((1f - textReveal) * 20f).roundToInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IntroBackground),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(IntroBackground)
        ) {
            val shakeOffset = MFitOpeningIntroTimeline.computeCameraShakeOffset(elapsedMs)
            val shakePx = Offset(shakeOffset.x.dp.toPx(), shakeOffset.y.dp.toPx())

            withTransform({
                translate(left = shakePx.x, top = shakePx.y)
            }) {
                val centerX = size.width / 2f
                val baseCenterY = size.height * 0.45f
                val levitationOffsetPx = MFitOpeningIntroTimeline.computeLevitationProgress(elapsedMs) * 34.dp.toPx()
                val barbellCenterY = baseCenterY - levitationOffsetPx
                val floorImpactY = baseCenterY + 43.dp.toPx()

                drawAmbientGlow(
                    elapsedMs = elapsedMs,
                    center = Offset(centerX, barbellCenterY)
                )

                drawShockwaveAndFloorCracks(
                    elapsedMs = elapsedMs,
                    impactCenter = Offset(centerX, floorImpactY),
                    cracks = cracks,
                    sparks = sparks
                )

                drawCenterConnectingShaft(
                    elapsedMs = elapsedMs,
                    centerX = centerX,
                    barbellCenterY = barbellCenterY
                )

                drawBarbellPlates(
                    elapsedMs = elapsedMs,
                    centerX = centerX,
                    baseCenterY = baseCenterY,
                    levitationOffsetPx = levitationOffsetPx
                )
            }
        }

        if (textReveal > 0f) {
            Column(
                modifier = Modifier
                    .offset { IntOffset(x = 0, y = 78.dp.roundToPx() + textSlideY.dp.roundToPx()) }
                    .alpha(textReveal),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "MFIT",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp,
                    color = IntroWhite
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "See the Progress",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = IntroVolt
                )
            }
        }
    }
}

private fun DrawScope.drawAmbientGlow(
    elapsedMs: Long,
    center: Offset
) {
    if (elapsedMs < MFitOpeningIntroTimeline.INNER_DROP_START_MS) return
    val baseAlpha = when {
        elapsedMs < MFitOpeningIntroTimeline.INNER_IMPACT_MS -> 0.08f
        elapsedMs < MFitOpeningIntroTimeline.METALLIC_LOCK_MS -> 0.16f
        else -> 0.22f
    }
    val radius = 145.dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(IntroVolt.copy(alpha = baseAlpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawShockwaveAndFloorCracks(
    elapsedMs: Long,
    impactCenter: Offset,
    cracks: List<CrackBranch>,
    sparks: List<SparkParticle>
) {
    val crackAlpha = MFitOpeningIntroTimeline.computeCrackAlpha(elapsedMs)
    if (crackAlpha <= 0f) return

    val innerImpactDt = (elapsedMs - MFitOpeningIntroTimeline.INNER_IMPACT_MS).coerceAtLeast(0L)
    val shockwaveProgress = (innerImpactDt.toFloat() / 520f).coerceIn(0f, 1f)
    if (shockwaveProgress < 1f) {
        val rx = (28f + shockwaveProgress * 110f).dp.toPx()
        val ry = rx * 0.28f
        val ringAlpha = (1f - shockwaveProgress) * 0.55f * crackAlpha
        drawOval(
            color = IntroVolt.copy(alpha = ringAlpha),
            topLeft = Offset(impactCenter.x - rx, impactCenter.y - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 2.dp.toPx())
        )
    }

    if (elapsedMs >= MFitOpeningIntroTimeline.OUTER_IMPACT_MS) {
        val outerDt = (elapsedMs - MFitOpeningIntroTimeline.OUTER_IMPACT_MS).coerceAtLeast(0L)
        val outerWaveProgress = (outerDt.toFloat() / 560f).coerceIn(0f, 1f)
        if (outerWaveProgress < 1f) {
            val rx = (38f + outerWaveProgress * 135f).dp.toPx()
            val ry = rx * 0.28f
            val ringAlpha = (1f - outerWaveProgress) * 0.65f * crackAlpha
            drawOval(
                color = IntroVolt.copy(alpha = ringAlpha),
                topLeft = Offset(impactCenter.x - rx, impactCenter.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = Stroke(width = 2.2.dp.toPx())
            )
        }
    }

    val crackGrowth = ((elapsedMs - MFitOpeningIntroTimeline.INNER_IMPACT_MS).toFloat() / 320f).coerceIn(0f, 1f)
    cracks.forEach { branch ->
        val visibleSegments = (branch.normalizedPoints.size * crackGrowth).toInt().coerceAtLeast(2)
        val path = Path().apply {
            val firstPt = branch.normalizedPoints.first()
            moveTo(
                impactCenter.x + firstPt.x.dp.toPx(),
                impactCenter.y + firstPt.y.dp.toPx()
            )
            for (i in 1 until visibleSegments.coerceAtMost(branch.normalizedPoints.size)) {
                val pt = branch.normalizedPoints[i]
                lineTo(
                    impactCenter.x + pt.x.dp.toPx(),
                    impactCenter.y + pt.y.dp.toPx()
                )
            }
        }
        drawPath(
            path = path,
            color = IntroVolt.copy(alpha = 0.72f * crackAlpha),
            style = Stroke(
                width = branch.strokeWidthDp.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Bevel
            )
        )
    }

    val gravityDpPerSec2 = 340f
    sparks.forEach { spark ->
        val dtMs = elapsedMs - spark.startTimeMs
        if (dtMs in 0L..540L) {
            val tSec = dtMs.toFloat() / 1000f
            val sx = impactCenter.x + (spark.initialVelocityX * tSec).dp.toPx()
            val sy = impactCenter.y +
                (spark.initialVelocityY * tSec + 0.5f * gravityDpPerSec2 * tSec * tSec).dp.toPx()
            val sparkAlpha = (1f - dtMs.toFloat() / 540f).coerceIn(0f, 1f) * crackAlpha
            drawCircle(
                color = IntroVolt.copy(alpha = sparkAlpha),
                radius = spark.radiusDp.dp.toPx(),
                center = Offset(sx, sy)
            )
        }
    }
}

private fun DrawScope.drawCenterConnectingShaft(
    elapsedMs: Long,
    centerX: Float,
    barbellCenterY: Float
) {
    val rodProgress = MFitOpeningIntroTimeline.computeRodProgress(elapsedMs)
    if (rodProgress <= 0f) return

    val fullWidthPx = 118.dp.toPx()
    val currentWidthPx = fullWidthPx * rodProgress
    val heightPx = 12.dp.toPx()
    val cornerPx = 6.dp.toPx()

    drawRoundRect(
        color = IntroWhite,
        topLeft = Offset(centerX - currentWidthPx / 2f, barbellCenterY - heightPx / 2f),
        size = Size(currentWidthPx, heightPx),
        cornerRadius = CornerRadius(cornerPx, cornerPx)
    )
}

private fun DrawScope.drawBarbellPlates(
    elapsedMs: Long,
    centerX: Float,
    baseCenterY: Float,
    levitationOffsetPx: Float
) {
    val startDropCenterY = -120.dp.toPx()

    val innerDropProgress = MFitOpeningIntroTimeline.computeGravityDropProgress(
        elapsedMs = elapsedMs,
        startMs = MFitOpeningIntroTimeline.INNER_DROP_START_MS,
        impactMs = MFitOpeningIntroTimeline.INNER_IMPACT_MS
    )
    if (elapsedMs >= MFitOpeningIntroTimeline.INNER_DROP_START_MS) {
        val innerSquash = MFitOpeningIntroTimeline.computeImpactSquash(
            elapsedMs = elapsedMs,
            impactMs = MFitOpeningIntroTimeline.INNER_IMPACT_MS
        )
        val innerBaseWidth = 16.dp.toPx()
        val innerBaseHeight = 86.dp.toPx()
        val innerWidth = innerBaseWidth * (1f + innerSquash * 0.65f)
        val innerHeight = innerBaseHeight * (1f - innerSquash)
        val innerCorner = 8.dp.toPx()
        val innerCenterY = startDropCenterY +
            (baseCenterY - startDropCenterY) * innerDropProgress -
            levitationOffsetPx
        val innerOffsetFromCenter = 24.dp.toPx()

        drawPlateSymmetricPair(
            centerX = centerX,
            centerY = innerCenterY,
            horizontalCenterDistance = innerOffsetFromCenter,
            plateWidth = innerWidth,
            plateHeight = innerHeight,
            cornerRadius = innerCorner
        )
    }

    val outerDropProgress = MFitOpeningIntroTimeline.computeGravityDropProgress(
        elapsedMs = elapsedMs,
        startMs = MFitOpeningIntroTimeline.OUTER_DROP_START_MS,
        impactMs = MFitOpeningIntroTimeline.OUTER_IMPACT_MS
    )
    if (elapsedMs >= MFitOpeningIntroTimeline.OUTER_DROP_START_MS) {
        val outerSquash = MFitOpeningIntroTimeline.computeImpactSquash(
            elapsedMs = elapsedMs,
            impactMs = MFitOpeningIntroTimeline.OUTER_IMPACT_MS
        )
        val outerBaseWidth = 13.dp.toPx()
        val outerBaseHeight = 52.dp.toPx()
        val outerWidth = outerBaseWidth * (1f + outerSquash * 0.65f)
        val outerHeight = outerBaseHeight * (1f - outerSquash)
        val outerCorner = 6.5.dp.toPx()
        val outerCenterY = startDropCenterY +
            (baseCenterY - startDropCenterY) * outerDropProgress -
            levitationOffsetPx
        val outerOffsetFromCenter = 43.dp.toPx()

        drawPlateSymmetricPair(
            centerX = centerX,
            centerY = outerCenterY,
            horizontalCenterDistance = outerOffsetFromCenter,
            plateWidth = outerWidth,
            plateHeight = outerHeight,
            cornerRadius = outerCorner
        )
    }
}

private fun DrawScope.drawPlateSymmetricPair(
    centerX: Float,
    centerY: Float,
    horizontalCenterDistance: Float,
    plateWidth: Float,
    plateHeight: Float,
    cornerRadius: Float
) {
    val leftPlateX = centerX - horizontalCenterDistance - plateWidth / 2f
    val rightPlateX = centerX + horizontalCenterDistance - plateWidth / 2f
    val topY = centerY - plateHeight / 2f

    drawRoundRect(
        color = IntroVolt,
        topLeft = Offset(leftPlateX, topY),
        size = Size(plateWidth, plateHeight),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )

    drawRoundRect(
        color = IntroVolt,
        topLeft = Offset(rightPlateX, topY),
        size = Size(plateWidth, plateHeight),
        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
    )
}

