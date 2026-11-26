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
    PLAN_SELECT_DAYS_AREA,
    PLAN_INPUT_AREA,
    PLAN_SAVE_BUTTON,
    WORKOUT_START_AREA,
    WORKOUT_EXERCISE_MUSCLE_AREA,
    WORKOUT_SET_ENTRY_AREA,
    WORKOUT_REST_TIMER_AREA,
    WORKOUT_FINISH_BUTTON,
    STATS_OVERVIEW_AREA
}

data class TutorialStep(
    val stepNumber: Int,
    val targetId: TutorialTargetId,
    val tab: BottomNavTab,
    val title: String,
    val description: String,
    val totalSteps: Int = 10,
    val secondaryTargetId: TutorialTargetId? = null
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

internal data class TutorialConnectorGeometry(
    val showConnector: Boolean,
    val cardAnchor: Offset,
    val ringAnchor: Offset
)

internal fun computeTutorialConnector(
    spotlightRect: Rect,
    hasSpotlight: Boolean,
    placeCardBelow: Boolean,
    cardTopPx: Float,
    cardHeightPx: Float,
    cardLeftPx: Float,
    cardRightPx: Float,
    cardAnchorInsetPx: Float,
    minConnectorGapPx: Float
): TutorialConnectorGeometry {
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
    return TutorialConnectorGeometry(
        showConnector = hasSpotlight && signedGap >= minConnectorGapPx,
        cardAnchor = cardAnchor,
        ringAnchor = ringAnchor
    )
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
    val minSpotlightTop = if (hasContainer && containerHeight > topSafeInsetPx + padPx) {
        topSafeInsetPx.coerceAtLeast(padPx)
    } else {
        padPx
    }
    val maxSpotlightBottom = (containerHeight - bottomSafeInsetPx.coerceAtLeast(padPx))
        .coerceAtLeast(minSpotlightTop)

    var spotlightRect = if (hasContainer && targetRect != null && targetRect.width > 0f && targetRect.height > 0f) {
        Rect(
            left = (targetRect.left - padPx).coerceIn(padPx, (containerWidth - padPx).coerceAtLeast(padPx)),
            top = (targetRect.top - padPx).coerceIn(minSpotlightTop, maxSpotlightBottom),
            right = (targetRect.right + padPx).coerceIn(padPx, (containerWidth - padPx).coerceAtLeast(padPx)),
            bottom = (targetRect.bottom + padPx).coerceIn(minSpotlightTop, maxSpotlightBottom)
        )
    } else {
        Rect.Zero
    }

    val hasSpotlight = spotlightRect.width > 2f && spotlightRect.height > 2f
    val minCardTop = topSafeInsetPx + edgeMarginPx
    val maxCardTop = (containerHeight - bottomSafeInsetPx - edgeMarginPx - cardHeightPx)
        .coerceAtLeast(minCardTop)

    val spaceBelow = (containerHeight - bottomSafeInsetPx - edgeMarginPx) - spotlightRect.bottom
    val spaceAbove = spotlightRect.top - (topSafeInsetPx + edgeMarginPx)

    val placeCardBelow = if (hasSpotlight && hasContainer) {
        spaceBelow >= cardHeightPx + connectorGapPx || spaceBelow >= spaceAbove
    } else {
        true
    }

    val cardTopPx = if (hasSpotlight && hasContainer) {
        if (placeCardBelow) {
            if (spaceBelow >= cardHeightPx + minConnectorGapPx) {
                val effectiveGap = (spaceBelow - cardHeightPx).coerceIn(minConnectorGapPx, connectorGapPx)
                (spotlightRect.bottom + effectiveGap).coerceIn(minCardTop, maxCardTop)
            } else {
                val trimmedBottom = (maxCardTop - minConnectorGapPx).coerceAtLeast(spotlightRect.top + 36f)
                spotlightRect = spotlightRect.copy(bottom = minOf(spotlightRect.bottom, trimmedBottom))
                maxCardTop
            }
        } else {
            if (spaceAbove >= cardHeightPx + minConnectorGapPx) {
                val effectiveGap = (spaceAbove - cardHeightPx).coerceIn(minConnectorGapPx, connectorGapPx)
                (spotlightRect.top - effectiveGap - cardHeightPx).coerceIn(minCardTop, maxCardTop)
            } else {
                val trimmedTop = (minCardTop + cardHeightPx + minConnectorGapPx).coerceAtMost(spotlightRect.bottom - 36f)
                spotlightRect = spotlightRect.copy(top = maxOf(spotlightRect.top, trimmedTop))
                minCardTop
            }
        }
    } else {
        maxCardTop
    }

    val finalHasSpotlight = spotlightRect.width > 2f && spotlightRect.height > 2f
    val cornerRadiusPx = if (finalHasSpotlight) {
        minOf(maxCornerPx, spotlightRect.width / 2f, spotlightRect.height / 2f)
    } else {
        maxCornerPx
    }

    val cardLeftPx = cardHorizontalMarginPx
    val cardRightPx = (containerWidth - cardHorizontalMarginPx).coerceAtLeast(cardLeftPx + 1f)
    val connector = computeTutorialConnector(
        spotlightRect = spotlightRect,
        hasSpotlight = finalHasSpotlight,
        placeCardBelow = placeCardBelow,
        cardTopPx = cardTopPx,
        cardHeightPx = cardHeightPx,
        cardLeftPx = cardLeftPx,
        cardRightPx = cardRightPx,
        cardAnchorInsetPx = cardAnchorInsetPx,
        minConnectorGapPx = minConnectorGapPx
    )

    return TutorialOverlayLayout(
        spotlightRect = spotlightRect,
        hasSpotlight = finalHasSpotlight,
        placeCardBelow = placeCardBelow,
        cardTopPx = cardTopPx,
        cardLeftPx = cardLeftPx,
        cardRightPx = cardRightPx,
        showConnector = connector.showConnector,
        cardAnchor = connector.cardAnchor,
        ringAnchor = connector.ringAnchor,
        cornerRadiusPx = cornerRadiusPx
    )
}

internal fun computeSecondarySpotlightRect(
    containerWidth: Float,
    containerHeight: Float,
    targetRect: Rect?,
    cardRect: Rect,
    topSafeInsetPx: Float,
    bottomSafeInsetPx: Float,
    padPx: Float
): Rect? {
    if (containerWidth <= 0f || containerHeight <= 0f || targetRect == null) return null
    if (targetRect.width <= 0f || targetRect.height <= 0f) return null

    val minSpotlightTop = if (containerHeight > topSafeInsetPx + padPx) {
        topSafeInsetPx.coerceAtLeast(padPx)
    } else {
        padPx
    }
    val maxSpotlightBottom = (containerHeight - bottomSafeInsetPx.coerceAtLeast(padPx))
        .coerceAtLeast(minSpotlightTop)

    val rect = Rect(
        left = (targetRect.left - padPx).coerceIn(padPx, (containerWidth - padPx).coerceAtLeast(padPx)),
        top = (targetRect.top - padPx).coerceIn(minSpotlightTop, maxSpotlightBottom),
        right = (targetRect.right + padPx).coerceIn(padPx, (containerWidth - padPx).coerceAtLeast(padPx)),
        bottom = (targetRect.bottom + padPx).coerceIn(minSpotlightTop, maxSpotlightBottom)
    )
    if (rect.width <= 2f || rect.height <= 2f || rect.overlaps(cardRect)) return null
    return rect
}

val MFitTutorialSteps: List<TutorialStep> = listOf(
    TutorialStep(
        stepNumber = 1,
        targetId = TutorialTargetId.HOME_STREAK,
        tab = BottomNavTab.HOME,
        title = "Schedule-Aware Streak",
        description = "Tracks how many scheduled training days you complete in a row. Your planned rest days never break this streak."
    ),
    TutorialStep(
        stepNumber = 2,
        targetId = TutorialTargetId.HOME_HERO_PLAN,
        tab = BottomNavTab.HOME,
        title = "Today's Workout Plan",
        description = "Shows the split scheduled for today, like Push Day. Tap Start Workout here whenever you are ready to train."
    ),
    TutorialStep(
        stepNumber = 3,
        targetId = TutorialTargetId.PLAN_CREATE_AREA,
        tab = BottomNavTab.PLAN,
        title = "Create Your Weekly Plan",
        description = "Tap + Create Workout Plan to build your weekly routine. Setting a schedule keeps Home and your streak in sync."
    ),
    TutorialStep(
        stepNumber = 4,
        targetId = TutorialTargetId.PLAN_SELECT_DAYS_AREA,
        tab = BottomNavTab.PLAN,
        title = "Pick Your Training Days",
        description = "Tap the days you plan to work out each week, such as Monday, Wednesday, and Friday. Unselected days become rest days."
    ),
    TutorialStep(
        stepNumber = 5,
        targetId = TutorialTargetId.PLAN_INPUT_AREA,
        tab = BottomNavTab.PLAN,
        title = "Name Each Day & Save",
        description = "Type a workout name or tap a quick preset like Push Day, Pull Day, or Legs. When every day is set, tap Save to store your schedule.",
        secondaryTargetId = TutorialTargetId.PLAN_SAVE_BUTTON
    ),
    TutorialStep(
        stepNumber = 6,
        targetId = TutorialTargetId.WORKOUT_START_AREA,
        tab = BottomNavTab.WORKOUT,
        title = "Start a Workout Session",
        description = "Tap + Log Workout on the Workout tab to open an active session and begin tracking your sets."
    ),
    TutorialStep(
        stepNumber = 7,
        targetId = TutorialTargetId.WORKOUT_EXERCISE_MUSCLE_AREA,
        tab = BottomNavTab.WORKOUT,
        title = "Exercise Name & Target Muscle",
        description = "Enter the movement you are doing, such as Bench Press, then pick its muscle tag like CHEST so your stats stay accurate."
    ),
    TutorialStep(
        stepNumber = 8,
        targetId = TutorialTargetId.WORKOUT_SET_ENTRY_AREA,
        tab = BottomNavTab.WORKOUT,
        title = "Weight, Reps & Add Set",
        description = "Use the - and + steppers to set load and reps, choose Warmup, Normal, or Final Set, then tap Add Set to log it below."
    ),
    TutorialStep(
        stepNumber = 9,
        targetId = TutorialTargetId.WORKOUT_REST_TIMER_AREA,
        tab = BottomNavTab.WORKOUT,
        title = "Rest Timer & Finish Workout",
        description = "Logging a set starts the rest timer with -15s, +15s, and Skip controls. When your session is complete, tap Finish at the top right to save.",
        secondaryTargetId = TutorialTargetId.WORKOUT_FINISH_BUTTON
    ),
    TutorialStep(
        stepNumber = 10,
        targetId = TutorialTargetId.STATS_OVERVIEW_AREA,
        tab = BottomNavTab.STATS,
        title = "Strength Chart & Muscle Heatmap",
        description = "Review your estimated 1RM progression per exercise and see which muscles you trained this week on the 2D heatmap."
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
