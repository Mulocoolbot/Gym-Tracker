package com.mulocoolbot.gymtracker.ui.plan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.ui.components.CalendarPlanIcon
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

private val SHORT_DAY_NAMES = listOf(
    "Sun",
    "Mon",
    "Tue",
    "Wed",
    "Thu",
    "Fri",
    "Sat"
)

private val QUICK_SELECT_PRESETS = listOf(
    "Pull Day",
    "Push Day",
    "Legs",
    "Upper Body",
    "Lower Body",
    "Full Body"
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
        when (uiState.subPage) {
            WorkoutPlanSubPage.EMPTY -> EmptyWorkoutPlanContent(
                onStartCreateOrEditPlan = onOpenWizard
            )

            WorkoutPlanSubPage.SELECT_DAYS -> SelectDaysSubPageContent(
                selectedDays = uiState.selectedDays,
                onToggleDay = onToggleDay,
                onRequestCancel = onRequestCancel,
                onGoToInputPlanPage = onNextStep
            )

            WorkoutPlanSubPage.INPUT_PLAN -> InputPlanSubPageContent(
                selectedDays = uiState.selectedDays.sorted(),
                draftPlanNames = uiState.draftPlanNames,
                onUpdateDraftName = onUpdateDraftName,
                onGoToEditDaysPage = onBackToSelectDays,
                onRequestCancel = onRequestCancel,
                onSaveWorkoutPlan = onSaveSchedule
            )

            WorkoutPlanSubPage.SAVED -> SavedWorkoutPlanContent(
                savedDays = uiState.savedDays,
                onStartCreateOrEditPlan = onOpenWizard
            )
        }

        if (uiState.showDiscardDialog) {
            DiscardChangesDialog(
                onKeepEditing = onDismissDiscardDialog,
                onDiscard = onConfirmDiscard
            )
        }
    }
}

@Composable
private fun EmptyWorkoutPlanContent(
    onStartCreateOrEditPlan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        WorkoutPlanMainHeader(onStartCreateOrEditPlan = onStartCreateOrEditPlan)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(InputSurface)
                            .border(1.dp, CardBorder, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CalendarPlanIcon,
                            contentDescription = "No Workout Plan",
                            tint = VoltAccent,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "No Workout Plan",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "You haven't set up a weekly training schedule yet.",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onStartCreateOrEditPlan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoltAccent,
                            contentColor = OnVolt
                        )
                    ) {
                        Text(
                            text = "+ Create Workout Plan",
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
private fun SavedWorkoutPlanContent(
    savedDays: List<WorkoutPlanEntity>,
    onStartCreateOrEditPlan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        WorkoutPlanMainHeader(onStartCreateOrEditPlan = onStartCreateOrEditPlan)

        Spacer(modifier = Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(savedDays, key = { it.dayOfWeek }) { dayPlan ->
                SavedDayPlanCard(dayPlan = dayPlan)
            }
        }
    }
}

@Composable
private fun WorkoutPlanMainHeader(
    onStartCreateOrEditPlan: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Workout Plan",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(VoltAccent)
                .clickable(onClick = onStartCreateOrEditPlan),
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
private fun SavedDayPlanCard(dayPlan: WorkoutPlanEntity) {
    val isActive = !dayPlan.isRestDay
    val badgeBg = if (isActive) VoltAccent else InputSurface
    val badgeTextColor = if (isActive) OnVolt else TextMuted
    val subtitleText = if (isActive) dayPlan.planName else "Rest Day"
    val subtitleColor = if (isActive) VoltAccent else TextMuted
    val statusText = if (isActive) "Active" else "Rest"
    val statusBg = if (isActive) Color(0x22D4FF00) else InputSurface
    val statusTextColor = if (isActive) VoltAccent else TextMuted

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) VoltAccent.copy(alpha = 0.45f) else CardBorder
        )
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
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = SHORT_DAY_NAMES[dayPlan.dayOfWeek],
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor
                    )
                }

                Column {
                    Text(
                        text = FULL_DAY_NAMES[dayPlan.dayOfWeek],
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleText,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = subtitleColor
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(statusBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusTextColor
                )
            }
        }
    }
}

@Composable
private fun SelectDaysSubPageContent(
    selectedDays: Set<Int>,
    onToggleDay: (Int) -> Unit,
    onRequestCancel: () -> Unit,
    onGoToInputPlanPage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WORKOUT PLAN · PAGE 1 OF 2",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = VoltAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select Days",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(50))
                    .clickable(onClick = onRequestCancel)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Choose your active training days",
                fontSize = 12.sp,
                color = TextMuted
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(InputSurface)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${selectedDays.size} Selected",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoltAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items((0..6).toList(), key = { it }) { dayIndex ->
                val isSelected = dayIndex in selectedDays
                SelectDayRowCard(
                    dayIndex = dayIndex,
                    isSelected = isSelected,
                    onClick = { onToggleDay(dayIndex) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onRequestCancel),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            Button(
                onClick = onGoToInputPlanPage,
                enabled = selectedDays.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltAccent,
                    contentColor = OnVolt,
                    disabledContainerColor = InputSurface,
                    disabledContentColor = TextMuted
                )
            ) {
                Text(
                    text = "Next",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SelectDayRowCard(
    dayIndex: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    val borderColor = if (isSelected) VoltAccent else CardBorder
    val badgeBg = if (isSelected) VoltAccent else InputSurface
    val badgeText = if (isSelected) OnVolt else TextMuted
    val subtitleText = if (isSelected) "Training Day" else "Rest Day"
    val subtitleColor = if (isSelected) VoltAccent else TextMuted

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(borderWidth, borderColor)
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
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = SHORT_DAY_NAMES[dayIndex],
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeText
                    )
                }

                Column {
                    Text(
                        text = FULL_DAY_NAMES[dayIndex],
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitleText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = subtitleColor
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(VoltAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        val checkPath = Path().apply {
                            moveTo(size.width * 0.18f, size.height * 0.52f)
                            lineTo(size.width * 0.42f, size.height * 0.76f)
                            lineTo(size.width * 0.84f, size.height * 0.26f)
                        }
                        drawPath(
                            path = checkPath,
                            color = OnVolt,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, CardBorder, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun InputPlanSubPageContent(
    selectedDays: List<Int>,
    draftPlanNames: Map<Int, String>,
    onUpdateDraftName: (Int, String) -> Unit,
    onGoToEditDaysPage: () -> Unit,
    onRequestCancel: () -> Unit,
    onSaveWorkoutPlan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WORKOUT PLAN · PAGE 2 OF 2",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = VoltAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Set Workout Plan",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CardSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(50))
                        .clickable(onClick = onRequestCancel)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VoltAccent)
                        .clickable(onClick = onSaveWorkoutPlan)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = SdCardSaveIcon,
                        contentDescription = "Save",
                        tint = OnVolt,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Save",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnVolt
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Name each selected training day",
                fontSize = 12.sp,
                color = TextMuted
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(InputSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(50))
                    .clickable(onClick = onGoToEditDaysPage)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Edit Days",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VoltAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(selectedDays, key = { it }) { dayIndex ->
                DayPlanInputCard(
                    dayIndex = dayIndex,
                    currentValue = draftPlanNames[dayIndex].orEmpty(),
                    onValueChange = { onUpdateDraftName(dayIndex, it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onGoToEditDaysPage),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Edit Days",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onRequestCancel),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onSaveWorkoutPlan,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoltAccent,
                contentColor = OnVolt
            )
        ) {
            Icon(
                imageVector = SdCardSaveIcon,
                contentDescription = "Save Workout Plan",
                tint = OnVolt,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save Workout Plan",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = OnVolt
            )
        }
    }
}

@Composable
private fun DayPlanInputCard(
    dayIndex: Int,
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(VoltAccent)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = SHORT_DAY_NAMES[dayIndex],
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnVolt
                    )
                }

                Text(
                    text = FULL_DAY_NAMES[dayIndex],
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = currentValue,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Workout name (e.g., Pull Day)",
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                },
                singleLine = true,
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_SELECT_PRESETS.forEach { preset ->
                    val isSelectedPreset = currentValue.trim().equals(preset, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelectedPreset) VoltAccent else InputSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSelectedPreset) VoltAccent else CardBorder,
                                shape = RoundedCornerShape(50)
                            )
                            .clickable { onValueChange(preset) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = preset,
                            fontSize = 12.sp,
                            fontWeight = if (isSelectedPreset) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelectedPreset) OnVolt else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscardChangesDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit
) {
    Dialog(onDismissRequest = onKeepEditing) {
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
                    text = "Discard changes?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your unsaved workout plan selections will be discarded.",
                    fontSize = 14.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(InputSurface)
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .clickable(onClick = onKeepEditing),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keep Editing",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DangerRed)
                            .clickable(onClick = onDiscard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Discard",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
