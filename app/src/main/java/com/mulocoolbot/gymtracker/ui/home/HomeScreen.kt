package com.mulocoolbot.gymtracker.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.MonoMetricLarge
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSelectDay: (Int) -> Unit,
    onStartWorkout: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = uiState.formattedHeaderDate,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(20.dp))

        WeekDayStrip(
            weekDays = uiState.weekDays,
            selectedDayOfWeek = uiState.selectedDayOfWeek,
            onSelectDay = onSelectDay
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricStatCard(
                label = "Streak",
                valueText = "${uiState.currentStreak} days",
                modifier = Modifier.weight(1f)
            )
            MetricStatCard(
                label = "Calories",
                valueText = "${uiState.todayCalories} kcal",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        WorkoutPlanCard(
            isRestDay = uiState.selectedDayPlan.isRestDay,
            planName = if (uiState.selectedDayPlan.isRestDay) {
                "Rest Day"
            } else {
                uiState.selectedDayPlan.planName
            },
            onStartWorkout = { onStartWorkout(uiState.selectedDayPlan.planName) }
        )
    }
}

@Composable
private fun WeekDayStrip(
    weekDays: List<WeekDayItem>,
    selectedDayOfWeek: Int,
    onSelectDay: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        weekDays.forEach { day ->
            val isSelected = day.dayOfWeek == selectedDayOfWeek
            val containerColor = if (isSelected) VoltAccent else CardSurface
            val primaryTextColor = if (isSelected) OnVolt else TextPrimary
            val secondaryTextColor = if (isSelected) OnVolt else TextMuted
            val dotColor = if (isSelected) OnVolt else VoltAccent

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) VoltAccent else CardBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectDay(day.dayOfWeek) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.shortName,
                    style = MaterialTheme.typography.labelMedium,
                    color = secondaryTextColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = day.dateNumber,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (!day.isRestDay) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                } else {
                    Spacer(modifier = Modifier.height(5.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricStatCard(
    label: String,
    valueText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = valueText,
                style = MonoMetricLarge,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun WorkoutPlanCard(
    isRestDay: Boolean,
    planName: String,
    onStartWorkout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Workout Plan",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = planName,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )

            if (!isRestDay) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onStartWorkout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
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
            }
        }
    }
}

