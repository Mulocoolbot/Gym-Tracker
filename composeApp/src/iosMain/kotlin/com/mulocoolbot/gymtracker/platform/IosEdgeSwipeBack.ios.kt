package com.mulocoolbot.gymtracker.platform

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

actual fun Modifier.iosEdgeSwipeBack(
    enabled: Boolean,
    onBack: () -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    val density = LocalDensity.current
    val edgeWidthPx = with(density) { 28.dp.toPx() }
    val triggerDistancePx = with(density) { 72.dp.toPx() }
    val latestOnBack = rememberUpdatedState(onBack)

    pointerInput(edgeWidthPx, triggerDistancePx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x > edgeWidthPx) return@awaitEachGesture

            val startPos = down.position
            var triggered = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val dx = change.position.x - startPos.x
                val dy = abs(change.position.y - startPos.y)
                if (!triggered && dx >= triggerDistancePx && dx > dy * 1.5f) {
                    triggered = true
                    change.consume()
                    UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply {
                        prepare()
                        impactOccurred()
                    }
                    latestOnBack.value()
                    break
                }
            }
        }
    }
}
