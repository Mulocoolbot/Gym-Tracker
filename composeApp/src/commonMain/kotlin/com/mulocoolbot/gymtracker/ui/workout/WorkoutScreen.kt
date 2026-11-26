package com.mulocoolbot.gymtracker.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.data.local.WorkoutSetEntity
import com.mulocoolbot.gymtracker.platform.rememberNotificationPermissionRequester
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.RestTimerState
import com.mulocoolbot.gymtracker.ui.components.ArrowRightIcon
import com.mulocoolbot.gymtracker.ui.components.DismissKeyboardOnScroll
import com.mulocoolbot.gymtracker.ui.components.DumbbellIcon
import com.mulocoolbot.gymtracker.ui.components.dismissKeyboardOnTap
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import com.mulocoolbot.gymtracker.ui.tutorial.TutorialTargetId
import com.mulocoolbot.gymtracker.ui.tutorial.tutorialTarget
import com.mulocoolbot.gymtracker.util.formatCompactNumber
import com.mulocoolbot.gymtracker.util.parseDecimalInput

private val MUSCLE_GROUP_TAGS = listOf(
    "LATS",
    "TRAPS",
    "BICEPS",
    "CHEST",
    "SHOULDERS",
    "QUADS",
    "HAMSTRINGS",
    "ABS",
    "CALVES",
    "TRICEPS"
)

@Composable
fun WorkoutScreen(
    uiState: WorkoutUiState,
    onStartWorkout: () -> Unit,
    onStopWorkout: () -> Unit,
    onExerciseNameChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onSelectSetType: (String) -> Unit,
    onAddSet: () -> Unit,
    onSkipOrDismissRest: () -> Unit,
    onSelectExerciseSuggestion: (ExerciseHistoryEntity) -> Unit = { onExerciseNameChange(it.normalizedName) },
    onSelectMuscleGroup: (String) -> Unit = {},
    onStepWeight: (Double) -> Unit = {},
    onStepReps: (Int) -> Unit = {},
    onDeleteSessionSet: (Long) -> Unit = {},
    onAdjustRestTimer: (Int) -> Unit = {},
    onRequestFinishWorkout: () -> Unit = onStopWorkout,
    onDismissFinishDialog: () -> Unit = {},
    onConfirmFinishWorkout: () -> Unit = onStopWorkout
) {
    val requestNotificationPermissionIfNeeded = rememberNotificationPermissionRequester()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        if (!uiState.isWorkoutActive) {
            IdleWorkoutContent(
                todayPlanName = uiState.todayPlanName,
                completedSets = uiState.todayCompletedSets,
                onStartWorkout = {
                    requestNotificationPermissionIfNeeded()
                    onStartWorkout()
                }
            )
        } else {
            ActiveWorkoutContent(
                uiState = uiState,
                onRequestFinishWorkout = onRequestFinishWorkout,
                onExerciseNameChange = onExerciseNameChange,
                onSelectExerciseSuggestion = onSelectExerciseSuggestion,
                onSelectMuscleGroup = onSelectMuscleGroup,
                onWeightChange = onWeightChange,
                onStepWeight = onStepWeight,
                onRepsChange = onRepsChange,
                onStepReps = onStepReps,
                onSelectSetType = onSelectSetType,
                onAddSet = {
                    requestNotificationPermissionIfNeeded()
                    onAddSet()
                },
                onDeleteSessionSet = onDeleteSessionSet
            )
        }

        val restState = uiState.restTimerState
        if (restState.isRunning || restState.alarmRinging) {
            FloatingRestTimerOverlay(
                restState = restState,
                onAdjustTimer = onAdjustRestTimer,
                onSkipOrDismiss = onSkipOrDismissRest,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        if (uiState.showFinishDialog) {
            FinishWorkoutModal(
                elapsedSeconds = uiState.elapsedSeconds,
                setsCount = uiState.activeSessionSets.size,
                caloriesBurned = uiState.liveCaloriesBurned,
                onKeepLogging = onDismissFinishDialog,
                onFinishAndSave = onConfirmFinishWorkout
            )
        }
    }
}

@Composable
private fun IdleWorkoutContent(
    todayPlanName: String,
    completedSets: List<WorkoutSetEntity>,
    onStartWorkout: () -> Unit
) {
    if (completedSets.isEmpty()) {
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
                IdleWorkoutHeaderRow(onStartWorkout = onStartWorkout)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyWorkoutTodayCard(onStartWorkout = onStartWorkout)
                }

                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                IdleWorkoutHeaderRow(onStartWorkout = onStartWorkout)
            }

            item {
                TodayWorkoutPlanHeroCard(
                    todayPlanName = todayPlanName,
                    onStartWorkout = onStartWorkout
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TODAY'S COMPLETED SETS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "${completedSets.size} Sets",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VoltAccent
                    )
                }
            }

            items(
                items = completedSets,
                key = { "${it.sessionId}_${it.setId}_${it.timestampMillis}" }
            ) { setEntity ->
                CompletedSetCard(setEntity = setEntity)
            }
        }
    }
}

@Composable
private fun IdleWorkoutHeaderRow(
    onStartWorkout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Workout",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(VoltAccent)
                .clickable(onClick = onStartWorkout),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = OnVolt
            )
        }
    }
}

@Composable
private fun EmptyWorkoutTodayCard(
    onStartWorkout: () -> Unit
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
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(InputSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = DumbbellIcon,
                    contentDescription = "No Workout Recorded Today",
                    tint = VoltAccent,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No Workout Recorded Today",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Start logging your sets to track your daily volume and rest timer.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onStartWorkout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .tutorialTarget(TutorialTargetId.WORKOUT_START_AREA),
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

@Composable
private fun TodayWorkoutPlanHeroCard(
    todayPlanName: String,
    onStartWorkout: () -> Unit
) {
    val cardShape = RoundedCornerShape(28.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .drawWithContent {
                drawContent()
                val lineColor = VoltAccent.copy(alpha = 0.15f)
                val strokeWidth = 2.dp.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(size.width * 0.62f, 0f),
                    end = Offset(size.width, size.height * 0.56f),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = lineColor,
                    start = Offset(size.width * 0.74f, 0f),
                    end = Offset(size.width, size.height * 0.38f),
                    strokeWidth = strokeWidth
                )
            },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CardBorder)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "TODAY'S WORKOUT PLAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = VoltAccent
                )
            }

            Text(
                text = todayPlanName,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 20.dp, bottom = 28.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .tutorialTarget(TutorialTargetId.WORKOUT_START_AREA)
                    .clip(RoundedCornerShape(20.dp))
                    .background(VoltAccent)
                    .clickable(onClick = onStartWorkout)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start Workout",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnVolt
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(OnVolt.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ArrowRightIcon,
                        contentDescription = "Start Workout",
                        tint = OnVolt,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedSetCard(setEntity: WorkoutSetEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(InputSurface)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = setEntity.setLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VoltAccent
                    )
                }

                Text(
                    text = setEntity.exerciseName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            Text(
                text = "${formatCompactNumber(setEntity.weightKg)} kg × ${setEntity.reps}",
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = VoltAccent
            )
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    uiState: WorkoutUiState,
    onRequestFinishWorkout: () -> Unit,
    onExerciseNameChange: (String) -> Unit,
    onSelectExerciseSuggestion: (ExerciseHistoryEntity) -> Unit,
    onSelectMuscleGroup: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onStepWeight: (Double) -> Unit,
    onRepsChange: (String) -> Unit,
    onStepReps: (Int) -> Unit,
    onSelectSetType: (String) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSessionSet: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DismissKeyboardOnScroll(listState.isScrollInProgress)

    val bottomPadding = if (uiState.restTimerState.isRunning || uiState.restTimerState.alarmRinging) {
        130.dp
    } else {
        24.dp
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .imePadding()
            .dismissKeyboardOnTap(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ActiveWorkoutHeaderRow(
                elapsedSeconds = uiState.elapsedSeconds,
                liveCaloriesBurned = uiState.liveCaloriesBurned,
                onRequestFinishWorkout = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onRequestFinishWorkout()
                }
            )
        }

        item {
            ActiveSetEntryFormCard(
                exerciseInput = uiState.exerciseInput,
                selectedMuscleGroup = uiState.selectedMuscleGroup,
                weightInput = uiState.weightInput,
                repsInput = uiState.repsInput,
                selectedSetType = uiState.selectedSetType,
                nextNormalSetNumber = uiState.nextNormalSetNumber,
                exerciseSuggestions = uiState.exerciseSuggestions,
                onExerciseNameChange = onExerciseNameChange,
                onSelectExerciseSuggestion = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSelectExerciseSuggestion(it)
                },
                onSelectMuscleGroup = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSelectMuscleGroup(it)
                },
                onWeightChange = onWeightChange,
                onStepWeight = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onStepWeight(it)
                },
                onRepsChange = onRepsChange,
                onStepReps = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onStepReps(it)
                },
                onSelectSetType = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSelectSetType(it)
                },
                onAddSet = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onAddSet()
                }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CURRENT SESSION SETS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = TextMuted
                )
                Text(
                    text = "${uiState.activeSessionSets.size} Sets",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = VoltAccent
                )
            }
        }

        items(
            items = uiState.activeSessionSets.asReversed(),
            key = { "${it.id}_${it.timestampMillis}" }
        ) { setItem ->
            ActiveSessionSetCard(
                setItem = setItem,
                onDelete = { onDeleteSessionSet(setItem.id) }
            )
        }
    }
}

@Composable
private fun ActiveWorkoutHeaderRow(
    elapsedSeconds: Long,
    liveCaloriesBurned: Int,
    onRequestFinishWorkout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Workout",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(VoltAccent)
                )
                Text(
                    text = WorkoutViewModel.formatElapsedHHMMSS(elapsedSeconds),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = VoltAccent
                )
                Text(
                    text = "·",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = "$liveCaloriesBurned kcal",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }
        }

        Row(
            modifier = Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .clip(RoundedCornerShape(50))
                .background(CardSurface)
                .border(1.dp, DangerRed, RoundedCornerShape(50))
                .clickable(onClick = onRequestFinishWorkout)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(DangerRed)
            )
            Text(
                text = "Finish",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DangerRed
            )
        }
    }
}

@Composable
private fun ActiveSetEntryFormCard(
    exerciseInput: String,
    selectedMuscleGroup: String,
    weightInput: String,
    repsInput: String,
    selectedSetType: String,
    nextNormalSetNumber: Int,
    exerciseSuggestions: List<ExerciseHistoryEntity>,
    onExerciseNameChange: (String) -> Unit,
    onSelectExerciseSuggestion: (ExerciseHistoryEntity) -> Unit,
    onSelectMuscleGroup: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onStepWeight: (Double) -> Unit,
    onRepsChange: (String) -> Unit,
    onStepReps: (Int) -> Unit,
    onSelectSetType: (String) -> Unit,
    onAddSet: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = exerciseInput,
                onValueChange = onExerciseNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Exercise name",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                },
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardBorder)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = selectedMuscleGroup,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = VoltAccent
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = InputSurface,
                    unfocusedContainerColor = InputSurface,
                    focusedBorderColor = VoltAccent,
                    unfocusedBorderColor = CardBorder,
                    cursorColor = VoltAccent
                )
            )

            if (exerciseSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    exerciseSuggestions.forEach { item ->
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                .clip(RoundedCornerShape(50))
                                .background(InputSurface)
                                .border(1.dp, CardBorder, RoundedCornerShape(50))
                                .clickable { onSelectExerciseSuggestion(item) }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.normalizedName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MUSCLE_GROUP_TAGS.forEach { muscleTag ->
                    val isSelected = selectedMuscleGroup.equals(muscleTag, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) VoltAccent else InputSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) VoltAccent else CardBorder,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onSelectMuscleGroup(muscleTag) }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = muscleTag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) OnVolt else TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StepperNumericBox(
                    label = "Weight (kg)",
                    value = weightInput,
                    onValueChange = onWeightChange,
                    onDecrement = { onStepWeight(-2.5) },
                    onIncrement = { onStepWeight(2.5) },
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )

                StepperNumericBox(
                    label = "Reps",
                    value = repsInput,
                    onValueChange = onRepsChange,
                    onDecrement = { onStepReps(-1) },
                    onIncrement = { onStepReps(1) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            SetTypeSelectorRow(
                selectedSetType = selectedSetType,
                nextNormalSetNumber = nextNormalSetNumber,
                onSelectSetType = onSelectSetType
            )

            Spacer(modifier = Modifier.height(18.dp))

            val canAddSet = exerciseInput.isNotBlank() &&
                (parseDecimalInput(weightInput) ?: 0.0) > 0.0 &&
                (repsInput.toIntOrNull() ?: 0) > 0

            Button(
                onClick = onAddSet,
                enabled = canAddSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltAccent,
                    contentColor = OnVolt,
                    disabledContainerColor = InputSurface,
                    disabledContentColor = TextMuted
                )
            ) {
                Text(
                    text = "Add Set",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StepperNumericBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .clickable {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Done",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = VoltAccent
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(InputSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurface)
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = SolidColor(VoltAccent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurface)
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoltAccent
                )
            }
        }
    }
}

@Composable
private fun SetTypeSelectorRow(
    selectedSetType: String,
    nextNormalSetNumber: Int,
    onSelectSetType: (String) -> Unit
) {
    val options = listOf(
        Triple(WorkoutUiState.SET_TYPE_WARMUP, "Warmup", "1:00"),
        Triple(WorkoutUiState.SET_TYPE_NORMAL, "Set $nextNormalSetNumber", "2:00"),
        Triple(WorkoutUiState.SET_TYPE_FINAL, "Final Set", "3:00")
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (typeKey, title, durationLabel) ->
            val isSelected = selectedSetType == typeKey
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) VoltAccent else InputSurface)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) VoltAccent else CardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectSetType(typeKey) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) OnVolt else TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = durationLabel,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) OnVolt.copy(alpha = 0.75f) else TextMuted
                )
            }
        }
    }
}

@Composable
private fun ActiveSessionSetCard(
    setItem: DraftWorkoutSet,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(InputSurface)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = setItem.setLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VoltAccent
                    )
                }

                Column {
                    Text(
                        text = setItem.exerciseName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = setItem.muscleGroup,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${formatCompactNumber(setItem.weightKg)} kg × ${setItem.reps}",
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = VoltAccent
                )

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(InputSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val stroke = 2.dp.toPx()
                            drawLine(
                                color = TextMuted,
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = TextMuted,
                                start = Offset(size.width, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingRestTimerOverlay(
    restState: RestTimerState,
    onAdjustTimer: (Int) -> Unit,
    onSkipOrDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeText = if (restState.alarmRinging) {
        "00:00"
    } else {
        RestTimerController.formatMMSS(restState.remainingSeconds)
    }
    val actionLabel = if (restState.alarmRinging) "Dismiss" else "Skip"
    val progress = if (restState.totalSeconds > 0 && !restState.alarmRinging) {
        (restState.remainingSeconds.toFloat() / restState.totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.5.dp, VoltAccent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = restState.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = timeText,
                        fontSize = 28.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VoltAccent
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputSurface)
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .clickable { onAdjustTimer(-15) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "-15s",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputSurface)
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .clickable { onAdjustTimer(15) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+15s",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(VoltAccent)
                            .clickable(onClick = onSkipOrDismiss)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actionLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnVolt
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CardBorder)
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(VoltAccent)
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishWorkoutModal(
    elapsedSeconds: Long,
    setsCount: Int,
    caloriesBurned: Int,
    onKeepLogging: () -> Unit,
    onFinishAndSave: () -> Unit
) {
    Dialog(onDismissRequest = onKeepLogging) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Finish Workout?",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FinishMetricSummaryBox(
                        label = "Duration",
                        value = WorkoutViewModel.formatElapsedHHMMSS(elapsedSeconds),
                        modifier = Modifier.weight(1f)
                    )
                    FinishMetricSummaryBox(
                        label = "Sets",
                        value = setsCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    FinishMetricSummaryBox(
                        label = "Calories",
                        value = "$caloriesBurned kcal",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(InputSurface)
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .clickable(onClick = onKeepLogging),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keep Logging",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(VoltAccent)
                            .clickable(onClick = onFinishAndSave),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Finish & Save",
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

@Composable
private fun FinishMetricSummaryBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InputSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}
