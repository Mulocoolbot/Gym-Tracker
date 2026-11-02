package com.mulocoolbot.gymtracker.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mulocoolbot.gymtracker.data.local.ExerciseHistoryEntity
import com.mulocoolbot.gymtracker.timer.RestTimerController
import com.mulocoolbot.gymtracker.timer.RestTimerState
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricLarge
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricMedium
import com.mulocoolbot.gymtracker.ui.theme.MonoTimerDisplay
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import java.util.Locale

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
    onSkipOrDismissRest: () -> Unit
) {
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val requestNotificationPermissionIfNeeded = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionState = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    if (!uiState.isWorkoutActive) {
        IdleWorkoutContent(
            completedSets = uiState.todayCompletedSets,
            onStartWorkout = {
                requestNotificationPermissionIfNeeded()
                onStartWorkout()
            }
        )
    } else {
        ActiveWorkoutContent(
            uiState = uiState,
            onStopWorkout = onStopWorkout,
            onExerciseNameChange = onExerciseNameChange,
            onWeightChange = onWeightChange,
            onRepsChange = onRepsChange,
            onSelectSetType = onSelectSetType,
            onAddSet = {
                requestNotificationPermissionIfNeeded()
                onAddSet()
            },
            onSkipOrDismissRest = onSkipOrDismissRest
        )
    }
}

@Composable
private fun IdleWorkoutContent(
    completedSets: List<DraftWorkoutSet>,
    onStartWorkout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Workout",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onStartWorkout,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoltAccent,
                contentColor = OnVolt
            )
        ) {
            Text(
                text = "Start Workout",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(completedSets) { _, setItem ->
                LoggedSetRowCard(setItem = setItem)
            }
        }
    }
}

@Composable
private fun ActiveWorkoutContent(
    uiState: WorkoutUiState,
    onStopWorkout: () -> Unit,
    onExerciseNameChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onSelectSetType: (String) -> Unit,
    onAddSet: () -> Unit,
    onSkipOrDismissRest: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ElapsedSessionHeaderCard(
                elapsedSeconds = uiState.elapsedSeconds,
                onStopWorkout = onStopWorkout
            )
        }

        val restState = uiState.restTimerState
        if (restState.isRunning || restState.alarmRinging) {
            item {
                RestTimerActiveCard(
                    restState = restState,
                    onSkipOrDismiss = onSkipOrDismissRest
                )
            }
        }

        item {
            SetEntryFormCard(
                exerciseInput = uiState.currentExerciseInput,
                weightInput = uiState.weightInput,
                repsInput = uiState.repsInput,
                selectedSetType = uiState.selectedSetType,
                nextNormalSetNumber = uiState.nextNormalSetNumber,
                exerciseHistory = uiState.exerciseHistory,
                onExerciseNameChange = onExerciseNameChange,
                onWeightChange = onWeightChange,
                onRepsChange = onRepsChange,
                onSelectSetType = onSelectSetType,
                onAddSet = onAddSet
            )
        }

        itemsIndexed(uiState.activeSessionSets) { _, setItem ->
            LoggedSetRowCard(setItem = setItem)
        }
    }
}

@Composable
private fun ElapsedSessionHeaderCard(
    elapsedSeconds: Long,
    onStopWorkout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = WorkoutViewModel.formatElapsedHHMMSS(elapsedSeconds),
                style = MonoMetricLarge,
                color = TextPrimary
            )

            Button(
                onClick = onStopWorkout,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed,
                    contentColor = TextPrimary
                )
            ) {
                Text(
                    text = "Stop",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RestTimerActiveCard(
    restState: RestTimerState,
    onSkipOrDismiss: () -> Unit
) {
    val timeText = if (restState.alarmRinging) {
        "00:00"
    } else {
        RestTimerController.formatMMSS(restState.remainingSeconds)
    }
    val actionLabel = if (restState.alarmRinging) "Dismiss" else "Skip"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, VoltAccent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = timeText,
                style = MonoTimerDisplay,
                color = VoltAccent
            )

            Button(
                onClick = onSkipOrDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltAccent,
                    contentColor = OnVolt
                )
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SetEntryFormCard(
    exerciseInput: String,
    weightInput: String,
    repsInput: String,
    selectedSetType: String,
    nextNormalSetNumber: Int,
    exerciseHistory: List<ExerciseHistoryEntity>,
    onExerciseNameChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onSelectSetType: (String) -> Unit,
    onAddSet: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            OutlinedTextField(
                value = exerciseInput,
                onValueChange = onExerciseNameChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "Exercise name", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = workoutTextFieldColors()
            )

            if (exerciseHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    exerciseHistory.forEach { historyItem ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(InputSurface)
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .clickable { onExerciseNameChange(historyItem.normalizedName) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = historyItem.normalizedName,
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Weight (kg)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = onWeightChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = "0.0", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp),
                        colors = workoutTextFieldColors()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reps",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = repsInput,
                        onValueChange = onRepsChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = "0", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp),
                        colors = workoutTextFieldColors()
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SetTypeSelectorRow(
                selectedSetType = selectedSetType,
                nextNormalSetNumber = nextNormalSetNumber,
                onSelectSetType = onSelectSetType
            )

            Spacer(modifier = Modifier.height(16.dp))

            val canAddSet = exerciseInput.isNotBlank() &&
                (weightInput.toDoubleOrNull() ?: 0.0) > 0.0 &&
                (repsInput.toIntOrNull() ?: 0) > 0

            Button(
                onClick = onAddSet,
                enabled = canAddSet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltAccent,
                    contentColor = OnVolt,
                    disabledContainerColor = InputSurface,
                    disabledContentColor = TextMuted
                )
            ) {
                Text(
                    text = "Add Set",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
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
        WorkoutUiState.SET_TYPE_WARMUP to "Warmup",
        WorkoutUiState.SET_TYPE_NORMAL to "Set $nextNormalSetNumber",
        WorkoutUiState.SET_TYPE_FINAL to "Final Set"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (typeKey, label) ->
            val isSelected = selectedSetType == typeKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) VoltAccent else InputSurface)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) VoltAccent else CardBorder,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectSetType(typeKey) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) OnVolt else TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LoggedSetRowCard(setItem: DraftWorkoutSet) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
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
            Column {
                Text(
                    text = setItem.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = setItem.setLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Text(
                text = "${formatWeightValue(setItem.weightKg)} kg × ${setItem.reps}",
                style = MonoMetricMedium,
                color = VoltAccent
            )
        }
    }
}

@Composable
private fun workoutTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = InputSurface,
    unfocusedContainerColor = InputSurface,
    focusedBorderColor = VoltAccent,
    unfocusedBorderColor = CardBorder,
    cursorColor = VoltAccent
)

private fun formatWeightValue(weightKg: Double): String {
    return if (weightKg % 1.0 == 0.0) {
        weightKg.toInt().toString()
    } else {
        "%.1f".format(Locale.US, weightKg)
    }
}

