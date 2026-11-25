package com.mulocoolbot.gymtracker.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.mulocoolbot.gymtracker.data.auth.AuthSession
import com.mulocoolbot.gymtracker.ui.navigation.BottomNavTab

enum class TutorialTargetId {
    HOME_STREAK,
    HOME_HERO_PLAN,
    PLAN_CREATE_AREA,
    WORKOUT_START_AREA,
    STATS_OVERVIEW_AREA
}

data class TutorialStep(
    val stepNumber: Int,
    val targetId: TutorialTargetId,
    val tab: BottomNavTab,
    val title: String,
    val description: String,
    val totalSteps: Int = 5
) {
    val indicatorText: String
        get() = "$stepNumber/$totalSteps"

    val isLastStep: Boolean
        get() = stepNumber == totalSteps
}

data class TutorialUiState(
    val isActive: Boolean = false,
    val stepIndex: Int = 0,
    val steps: List<TutorialStep> = MFitTutorialSteps
) {
    val currentStep: TutorialStep
        get() = steps[stepIndex.coerceIn(0, steps.lastIndex)]

    val showSkipButton: Boolean
        get() = isActive && !currentStep.isLastStep

    val showNextButton: Boolean
        get() = isActive && !currentStep.isLastStep

    val showDoneButton: Boolean
        get() = isActive && currentStep.isLastStep
}

class TutorialController(
    private val steps: List<TutorialStep> = MFitTutorialSteps
) {
    var stepIndex by mutableStateOf(0)
        private set

    var isDismissedLocally by mutableStateOf(false)
        private set

    fun resolveUiState(session: AuthSession): TutorialUiState {
        val active = session.shouldShowTutorial &&
            !session.hasCompletedTutorial &&
            !isDismissedLocally
        return TutorialUiState(
            isActive = active,
            stepIndex = stepIndex.coerceIn(0, steps.lastIndex),
            steps = steps
        )
    }

    fun onNext(): BottomNavTab {
        if (stepIndex < steps.lastIndex) {
            stepIndex += 1
        }
        return steps[stepIndex.coerceIn(0, steps.lastIndex)].tab
    }

    fun onSkip(
        onPersistCompleted: () -> Unit,
        onNavigateHome: () -> Unit
    ) {
        finish(onPersistCompleted = onPersistCompleted, onNavigateHome = onNavigateHome)
    }

    fun onDone(
        onPersistCompleted: () -> Unit,
        onNavigateHome: () -> Unit
    ) {
        finish(onPersistCompleted = onPersistCompleted, onNavigateHome = onNavigateHome)
    }

    private fun finish(
        onPersistCompleted: () -> Unit,
        onNavigateHome: () -> Unit
    ) {
        if (isDismissedLocally) return
        isDismissedLocally = true
        stepIndex = 0
        onPersistCompleted()
        onNavigateHome()
    }
}

internal data class TutorialOverlayLayout(
    val spotlightRect: Rect,
    val hasSpotlight: Boolean,
    val placeCardBelow: Boolean,
    val cardTopPx: Float,
    val cardLeftPx: Float,
    val cardRightPx: Float,
    val showConnector: Boolean,
    val cardAnchor: Offset,
    val ringAnchor: Offset,
    val cornerRadiusPx: Float
)

internal fun computeTutorialOverlayLayout(
    containerWidth: Float,
    containerHeight: Float,
    targetRect: Rect?,
    cardHeightPx: Float,
    topSafeInsetPx: Float,
    bottomSafeInsetPx: Float,
    padPx: Float,
    maxCornerPx: Float,
    connectorGapPx: Float,
    edgeMarginPx: Float,
    cardHorizontalMarginPx: Float,
    cardAnchorInsetPx: Float,
    minConnectorGapPx: Float
): TutorialOverlayLayout {
    val hasContainer = containerWidth > 0f && containerHeight > 0f
    val spotlightRect = if (hasContainer && targetRect != null && targetRect.width > 0f && targetRect.height > 0f) {
        Rect(
            left = (targetRect.left - padPx).coerceIn(padPx, (containerWidth - padPx).coerceAtLeast(padPx)),
            top = (targetRect.top - padPx).coerceIn(padPx, (containerHeight - padPx).coerceAtLeast(padPx)),
            right = (targetRect.right + padPx).coerceIn(padPx, (containerWidth - padPx).coerceAtLeast(padPx)),
            bottom = (targetRect.bottom + padPx).coerceIn(padPx, (containerHeight - padPx).coerceAtLeast(padPx))
        )
    } else {
        Rect.Zero
    }

    val hasSpotlight = spotlightRect.width > 2f && spotlightRect.height > 2f
    val cornerRadiusPx = if (hasSpotlight) {
        minOf(maxCornerPx, spotlightRect.width / 2f, spotlightRect.height / 2f)
    } else {
        maxCornerPx
    }

    val minCardTop = topSafeInsetPx + edgeMarginPx
    val maxCardTop = (containerHeight - bottomSafeInsetPx - edgeMarginPx - cardHeightPx)
        .coerceAtLeast(minCardTop)

    val placeCardBelow = if (hasSpotlight && hasContainer) {
        val spaceBelow = (containerHeight - bottomSafeInsetPx - edgeMarginPx) - spotlightRect.bottom
        val spaceAbove = spotlightRect.top - (topSafeInsetPx + edgeMarginPx)
        spaceBelow >= cardHeightPx + connectorGapPx || spaceBelow >= spaceAbove
    } else {
        true
    }

    val cardTopPx = if (hasSpotlight && hasContainer) {
        if (placeCardBelow) {
            (spotlightRect.bottom + connectorGapPx).coerceIn(minCardTop, maxCardTop)
        } else {
            (spotlightRect.top - connectorGapPx - cardHeightPx).coerceIn(minCardTop, maxCardTop)
        }
    } else {
        maxCardTop
    }

    val cardLeftPx = cardHorizontalMarginPx
    val cardRightPx = (containerWidth - cardHorizontalMarginPx).coerceAtLeast(cardLeftPx + 1f)
    val anchorX = spotlightRect.center.x.coerceIn(
        (cardLeftPx + cardAnchorInsetPx).coerceAtMost(cardRightPx),
        (cardRightPx - cardAnchorInsetPx).coerceAtLeast(cardLeftPx)
    )

    val ringAnchor = if (placeCardBelow) {
        Offset(spotlightRect.center.x, spotlightRect.bottom)
    } else {
        Offset(spotlightRect.center.x, spotlightRect.top)
    }

    val cardAnchor = if (placeCardBelow) {
        Offset(anchorX, cardTopPx)
    } else {
        Offset(anchorX, cardTopPx + cardHeightPx)
    }

    val signedGap = if (placeCardBelow) {
        cardAnchor.y - ringAnchor.y
    } else {
        ringAnchor.y - cardAnchor.y
    }

    return TutorialOverlayLayout(
        spotlightRect = spotlightRect,
        hasSpotlight = hasSpotlight,
        placeCardBelow = placeCardBelow,
        cardTopPx = cardTopPx,
        cardLeftPx = cardLeftPx,
        cardRightPx = cardRightPx,
        showConnector = hasSpotlight && signedGap >= minConnectorGapPx,
        cardAnchor = cardAnchor,
        ringAnchor = ringAnchor,
        cornerRadiusPx = cornerRadiusPx
    )
}

val MFitTutorialSteps: List<TutorialStep> = listOf(
    TutorialStep(
        stepNumber = 1,
        targetId = TutorialTargetId.HOME_STREAK,
        tab = BottomNavTab.HOME,
        title = "Schedule-Aware Streak",
        description = "Tracks your consistency against your weekly plan. Scheduled rest days protect your streak so you only need to train on active days."
    ),
    TutorialStep(
        stepNumber = 2,
        targetId = TutorialTargetId.HOME_HERO_PLAN,
        tab = BottomNavTab.HOME,
        title = "Today's Workout Plan",
        description = "See your scheduled split for today right on Home and start your workout session in a single tap."
    ),
    TutorialStep(
        stepNumber = 3,
        targetId = TutorialTargetId.PLAN_CREATE_AREA,
        tab = BottomNavTab.PLAN,
        title = "Weekly Workout Plan",
        description = "Choose your active training days and name each split here. Your weekly schedule keeps Home and your streak in sync."
    ),
    TutorialStep(
        stepNumber = 4,
        targetId = TutorialTargetId.WORKOUT_START_AREA,
        tab = BottomNavTab.WORKOUT,
        title = "Log Sets & Rest Timer",
        description = "Start a session to record exercises, weights, and reps. Each logged set automatically triggers your rest timer."
    ),
    TutorialStep(
        stepNumber = 5,
        targetId = TutorialTargetId.STATS_OVERVIEW_AREA,
        tab = BottomNavTab.STATS,
        title = "Strength Stats & Muscle Heatmap",
        description = "Monitor your strength progression with Epley e1RM charts and review weekly training volume on the 2D Muscle Heatmap."
    )
)

val LocalTutorialTargets = staticCompositionLocalOf<SnapshotStateMap<TutorialTargetId, Rect>?> { null }

@Composable
fun Modifier.tutorialTarget(id: TutorialTargetId): Modifier {
    val registry = LocalTutorialTargets.current ?: return this
    return this.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val bounds = coordinates.boundsInRoot()
            if (bounds.width > 0f && bounds.height > 0f) {
                registry[id] = bounds
            }
        }
    }
}
