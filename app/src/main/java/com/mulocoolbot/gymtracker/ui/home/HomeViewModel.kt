package com.mulocoolbot.gymtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WeekDayItem(
    val dayOfWeek: Int,
    val shortName: String,
    val dateNumber: String,
    val isRestDay: Boolean
)

data class HomeUiState(
    val selectedDayOfWeek: Int = 0,
    val formattedHeaderDate: String = "",
    val weekDays: List<WeekDayItem> = emptyList(),
    val currentStreak: Int = 0,
    val todayCalories: Int = 0,
    val selectedDayPlan: WorkoutPlanEntity = WorkoutPlanEntity(
        dayOfWeek = 0,
        isRestDay = true,
        planName = "Rest Day"
    )
)

class HomeViewModel(
    repository: GymRepository,
    private val currentDate: LocalDate = LocalDate.now()
) : ViewModel() {

    private val todayDayOfWeek: Int = currentDate.dayOfWeek.value % 7
    private val selectedDayFlow = MutableStateFlow(todayDayOfWeek)

    val uiState: StateFlow<HomeUiState> = combine(
        selectedDayFlow,
        repository.userProfile,
        repository.weeklyPlan,
        repository.observeTodayCalories(currentDate.toEpochDay())
    ) { selectedDay, profile, plans, calories ->
        val planByDay = plans.associateBy { it.dayOfWeek }
        val weekStartSunday = currentDate.minusDays(todayDayOfWeek.toLong())
        val selectedDate = weekStartSunday.plusDays(selectedDay.toLong())

        val weekDays = (0..6).map { dayIndex ->
            val date = weekStartSunday.plusDays(dayIndex.toLong())
            val dayPlan = planByDay[dayIndex]
            WeekDayItem(
                dayOfWeek = dayIndex,
                shortName = SHORT_DAY_NAMES[dayIndex],
                dateNumber = "%02d".format(Locale.US, date.dayOfMonth),
                isRestDay = dayPlan?.isRestDay ?: true
            )
        }

        val activePlan = planByDay[selectedDay] ?: WorkoutPlanEntity(
            dayOfWeek = selectedDay,
            isRestDay = true,
            planName = "Rest Day"
        )

        HomeUiState(
            selectedDayOfWeek = selectedDay,
            formattedHeaderDate = selectedDate.format(HEADER_DATE_FORMATTER),
            weekDays = weekDays,
            currentStreak = profile?.currentStreak ?: 0,
            todayCalories = calories,
            selectedDayPlan = activePlan
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(selectedDayOfWeek = todayDayOfWeek)
    )

    fun selectDay(dayOfWeek: Int) {
        if (dayOfWeek in 0..6) {
            selectedDayFlow.value = dayOfWeek
        }
    }

    companion object {
        private val SHORT_DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        private val HEADER_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH)

        fun factory(repository: GymRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository) as T
                }
            }
        }
    }
}

