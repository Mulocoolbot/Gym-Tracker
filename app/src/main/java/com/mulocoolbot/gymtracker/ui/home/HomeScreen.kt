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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mulocoolbot.gymtracker.ui.components.ArrowRightIcon
import com.mulocoolbot.gymtracker.ui.components.BoltIcon
import com.mulocoolbot.gymtracker.ui.components.FlameIcon
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSelectDay: (Int) -> Unit,
    onStartWorkout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = uiState.formattedHeaderDate,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(22.dp))

        WeekDayCapsuleStrip(
            weekDays = uiState.weekDays,
            selectedDayOfWeek = uiState.selectedDayOfWeek,
            onSelectDay = onSelectDay
        )

        Spacer(modifier = Modifier.height(22.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StreakBentoCard(
                currentStreak = uiState.currentStreak,
                modifier = Modifier.weight(1f)
            )
            CaloriesBentoCard(
                todayCalories = uiState.todayCalories,
                calorieProgress = uiState.calorieProgress,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        HeroWorkoutPlanCard(
            isRestDay = uiState.selectedDayPlan.isRestDay,
            planName = if (uiState.selectedDayPlan.isRestDay) {
                "Rest Day"
            } else {
                uiState.selectedDayPlan.planName
            },
            onStartWorkout = onStartWorkout
        )
    }
}

@Composable
private fun WeekDayCapsuleStrip(
    weekDays: List<WeekDayItem>,
    selectedDayOfWeek: Int,
    onSelectDay: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weekDays.forEach { day ->
            val isSelected = day.dayOfWeek == selectedDayOfWeek
            val capsuleShape = RoundedCornerShape(22.dp)
            val backgroundColor = if (isSelected) VoltAccent else CardSurface
            val dayLabelColor = if (isSelected) OnVolt else TextMuted
            val dayNumberColor = if (isSelected) OnVolt else TextPrimary
            val showBottomDot = isSelected || !day.isRestDay
            val dotColor = if (isSelected) OnVolt else VoltAccent

            val cardModifier = Modifier
                .weight(1f)
                .height(88.dp)
                .clip(capsuleShape)
                .background(backgroundColor)
                .let { base ->
                    if (isSelected) {
                        base
                    } else {
                        base.border(1.dp, CardBorder, capsuleShape)
                    }
                }
                .clickable { onSelectDay(day.dayOfWeek) }
                .padding(vertical = 12.dp)

            Column(
                modifier = cardModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = day.shortName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = dayLabelColor
                )

                Text(
                    text = day.dayNumber,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = dayNumberColor
                )

                if (showBottomDot) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                } else {
                    Spacer(modifier = Modifier.size(5.dp))
                }
            }
        }
    }
}

@Composable
private fun StreakBentoCard(
    currentStreak: Int,
    modifier: Modifier = Modifier
) {
    val activeBars = when {
        currentStreak <= 0 -> 0
        currentStreak % 7 == 0 -> 7
        else -> currentStreak % 7
    }

    Card(
        modifier = modifier.height(164.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Streak",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
                Icon(
                    imageVector = FlameIcon,
                    contentDescription = "Streak",
                    tint = VoltAccent,
                    modifier = Modifier.size(36.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = currentStreak.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "days",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(7) { index ->
                    val barColor = if (index < activeBars) VoltAccent else CardBorder
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun CaloriesBentoCard(
    todayCalories: Int,
    calorieProgress: Float,
    modifier: Modifier = Modifier
) {
    val clampedProgress = calorieProgress.coerceIn(0f, 1f)

    Card(
        modifier = modifier.height(164.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calories",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(InputSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = BoltIcon,
                        contentDescription = "Calories",
                        tint = VoltAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = todayCalories.toString(),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "kcal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CardBorder)
            ) {
                if (clampedProgress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(clampedProgress)
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
private fun HeroWorkoutPlanCard(
    isRestDay: Boolean,
    planName: String,
    onStartWorkout: () -> Unit
) {
    val cardShape = RoundedCornerShape(28.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .drawWithContent {
                drawContent()
                val accentLineColor = Color(0x22D4FF00)
                val strokeWidth = 2.dp.toPx()
                drawLine(
                    color = accentLineColor,
                    start = Offset(size.width * 0.62f, 0f),
                    end = Offset(size.width, size.height * 0.58f),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = accentLineColor,
                    start = Offset(size.width * 0.74f, 0f),
                    end = Offset(size.width, size.height * 0.40f),
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
                    .background(InputSurface)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "WORKOUT PLAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = VoltAccent
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = planName,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (!isRestDay) {
                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
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
                            .background(Color(0x260B0C0E)),
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
}
