package com.mulocoolbot.gymtracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.util.formatHeaderDate
import com.mulocoolbot.gymtracker.util.formatTwoDigits
import com.mulocoolbot.gymtracker.util.todayLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class WeekDayItem(
    val dayOfWeek: Int,
    val shortName: String,
    val dayNumber: String,
    val isRestDay: Boolean
) {
    val dateNumber: String
        get() = dayNumber
}

data class HomeUiState(
    val formattedHeaderDate: String = "",
    val selectedDayOfWeek: Int = 0,
    val weekDays: List<WeekDayItem> = emptyList(),
    val currentStreak: Int = 0,
    val todayCalories: Int = 0,
    val calorieProgress: Float = if (todayCalories > 0) {
        (todayCalories / 600f).coerceIn(0.08f, 1f)
    } else {
        0f
    },
    val selectedDayPlan: WorkoutPlanEntity = WorkoutPlanEntity(
        dayOfWeek = 0,
        isRestDay = true,
        planName = "Rest Day"
    )
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel internal constructor(
    private val repository: GymRepository,
    private val currentDateProvider: () -> LocalDate
) : ViewModel() {

    constructor(repository: GymRepository) : this(
        repository = repository,
        currentDateProvider = { todayLocalDate() }
    )

    constructor(
        repository: GymRepository,
        currentDate: LocalDate
    ) : this(
        repository = repository,
        currentDateProvider = { currentDate }
    )

    private val currentDateFlow = MutableStateFlow(currentDateProvider())
    private val selectedDayFlow = MutableStateFlow(currentDateFlow.value.dayOfWeek.isoDayNumber % 7)

    val uiState: StateFlow<HomeUiState> = combine(
        selectedDayFlow,
        currentDateFlow,
        repository.userProfile,
        repository.weeklyPlan,
        currentDateFlow.flatMapLatest { date ->
            repository.observeTodayCalories(date.toEpochDays().toLong())
        }
    ) { selectedDay, activeDate, profile, plans, calories ->
        val todayDayOfWeek = activeDate.dayOfWeek.isoDayNumber % 7
        val planByDay = plans.associateBy { it.dayOfWeek }
        val weekStartSunday = activeDate.minus(todayDayOfWeek, DateTimeUnit.DAY)
        val selectedDate = weekStartSunday.plus(selectedDay, DateTimeUnit.DAY)

        val weekDays = (0..6).map { dayIndex ->
            val date = weekStartSunday.plus(dayIndex, DateTimeUnit.DAY)
            val dayPlan = planByDay[dayIndex]
            WeekDayItem(
                dayOfWeek = dayIndex,
                shortName = SHORT_DAY_NAMES[dayIndex],
                dayNumber = formatTwoDigits(date.dayOfMonth),
                isRestDay = dayPlan?.isRestDay ?: true
            )
        }

        val activePlan = planByDay[selectedDay] ?: WorkoutPlanEntity(
            dayOfWeek = selectedDay,
            isRestDay = true,
            planName = "Rest Day"
        )

        val progress = if (calories > 0) {
            (calories / 600f).coerceIn(0.08f, 1f)
        } else {
            0f
        }

        HomeUiState(
            formattedHeaderDate = formatHeaderDate(selectedDate),
            selectedDayOfWeek = selectedDay,
            weekDays = weekDays,
            currentStreak = profile?.currentStreak ?: 0,
            todayCalories = calories,
            calorieProgress = progress,
            selectedDayPlan = activePlan
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(selectedDayOfWeek = selectedDayFlow.value)
    )

    fun refreshDateIfNeeded() {
        val latest = currentDateProvider()
        if (currentDateFlow.value != latest) {
            val previousToday = currentDateFlow.value.dayOfWeek.isoDayNumber % 7
            currentDateFlow.value = latest
            if (selectedDayFlow.value == previousToday) {
                selectedDayFlow.value = latest.dayOfWeek.isoDayNumber % 7
            }
        }
    }

    fun selectDay(dayOfWeek: Int) {
        if (dayOfWeek in 0..6) {
            selectedDayFlow.value = dayOfWeek
        }
    }

    companion object {
        private val SHORT_DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        fun factory(repository: GymRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(repository) }
        }
    }
}
