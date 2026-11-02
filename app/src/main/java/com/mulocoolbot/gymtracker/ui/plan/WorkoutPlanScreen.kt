package com.mulocoolbot.gymtracker.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.ui.components.SdCardSaveIcon
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent

private val FULL_DAY_NAMES = listOf(
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday"
)

private val QUICK_SELECT_PRESETS = listOf(
    "Pull Day",
    "Push Day",
    "Legs",
    "Upper",
    "Lower",
    "Cardio"
)

@Composable
fun WorkoutPlanScreen(
    uiState: WorkoutPlanUiState,
    onOpenWizard: () -> Unit,
    onToggleDay: (Int) -> Unit,
    onNextStep: () -> Unit,
    onBackToSelectDays: () -> Unit,
    onUpdateDraftName: (Int, String) -> Unit,
    onRequestCancel: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onSaveSchedule: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        when {
            !uiState.isWizardOpen -> WeeklyOverviewContent(
                weeklyPlan = uiState.weeklyPlan,
                onOpenWizard = onOpenWizard
            )
            uiState.wizardStep == 1 -> SelectDaysStepContent(
                selectedDays = uiState.selectedDays,
                onToggleDay = onToggleDay,
                onRequestCancel = onRequestCancel,
                onNextStep = onNextStep
            )
            else -> ScheduleStepContent(
                selectedDays = uiState.selectedDays.sorted(),
                draftPlanNames = uiState.draftPlanNames,
                onUpdateDraftName = onUpdateDraftName,
                onBackToSelectDays = onBackToSelectDays,
                onSaveSchedule = onSaveSchedule
            )
        }

        if (uiState.showDiscardDialog) {
            DiscardChangesDialog(
                onCancel = onDismissDiscardDialog,
                onDiscard = onConfirmDiscard
            )
        }
    }
}

@Composable
private fun WeeklyOverviewContent(
    weeklyPlan: List<WorkoutPlanEntity>,
    onOpenWizard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Workout Plan",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VoltAccent)
                    .clickable(onClick = onOpenWizard),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnVolt,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(weeklyPlan, key = { it.dayOfWeek }) { dayPlan ->
                DayOverviewCard(dayPlan = dayPlan)
            }
        }
    }
}

@Composable
private fun DayOverviewCard(dayPlan: WorkoutPlanEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(
            width = 1.dp,
            color = if (dayPlan.isRestDay) CardBorder else VoltAccent.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = FULL_DAY_NAMES[dayPlan.dayOfWeek],
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = if (dayPlan.isRestDay) "Rest Day" else dayPlan.planName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (dayPlan.isRestDay) TextMuted else VoltAccent,
                fontWeight = if (dayPlan.isRestDay) FontWeight.Normal else FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SelectDaysStepContent(
    selectedDays: Set<Int>,
    onToggleDay: (Int) -> Unit,
    onRequestCancel: () -> Unit,
    onNextStep: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onRequestCancel,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(text = "Cancel", color = TextMuted)
            }
            Text(
                text = "Select Days",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items((0..6).toList(), key = { it }) { dayIndex ->
                val isSelected = dayIndex in selectedDays
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDay(dayIndex) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) VoltAccent else CardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FULL_DAY_NAMES[dayIndex],
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) VoltAccent else InputSurface)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNextStep,
            enabled = selectedDays.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoltAccent,
                contentColor = OnVolt,
                disabledContainerColor = InputSurface,
                disabledContentColor = TextMuted
            )
        ) {
            Text(
                text = "Next",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScheduleStepContent(
    selectedDays: List<Int>,
    draftPlanNames: Map<Int, String>,
    onUpdateDraftName: (Int, String) -> Unit,
    onBackToSelectDays: () -> Unit,
    onSaveSchedule: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                onClick = onBackToSelectDays,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = "<",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            Text(
                text = "Schedule",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.align(Alignment.Center)
            )

            IconButton(
                onClick = onSaveSchedule,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(VoltAccent)
            ) {
                Icon(
                    imageVector = SdCardSaveIcon,
                    contentDescription = "Save",
                    tint = OnVolt,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(selectedDays, key = { it }) { dayIndex ->
                DayScheduleEditorCard(
                    dayName = FULL_DAY_NAMES[dayIndex],
                    currentValue = draftPlanNames[dayIndex].orEmpty(),
                    onValueChange = { onUpdateDraftName(dayIndex, it) }
                )
            }
        }
    }
}

@Composable
private fun DayScheduleEditorCard(
    dayName: String,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = dayName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = "Workout name", color = TextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_SELECT_PRESETS.forEach { preset ->
                    val isMatching = currentValue.trim().equals(preset, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isMatching) VoltAccent else InputSurface)
                            .clickable { onValueChange(preset) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isMatching) OnVolt else TextPrimary,
                            fontWeight = if (isMatching) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscardChangesDialog(
    onCancel: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        title = {
            Text(
                text = "Discard changes?",
                style = MaterialTheme.typography.titleLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(text = "Discard", color = DangerRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Cancel", color = TextMuted)
            }
        }
    )
}

