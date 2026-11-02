package com.mulocoolbot.gymtracker.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.local.WorkoutPlanEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutPlanUiState(
    val weeklyPlan: List<WorkoutPlanEntity> = defaultWeeklyPlan(),
    val isWizardOpen: Boolean = false,
    val wizardStep: Int = 1,
    val selectedDays: Set<Int> = emptySet(),
    val draftPlanNames: Map<Int, String> = emptyMap(),
    val showDiscardDialog: Boolean = false
)

private data class WizardState(
    val isOpen: Boolean = false,
    val step: Int = 1,
    val selectedDays: Set<Int> = emptySet(),
    val draftPlanNames: Map<Int, String> = emptyMap(),
    val showDiscardDialog: Boolean = false
)

class WorkoutPlanViewModel(
    private val repository: GymRepository
) : ViewModel() {

    private val wizardState = MutableStateFlow(WizardState())

    val uiState: StateFlow<WorkoutPlanUiState> = combine(
        repository.weeklyPlan,
        wizardState
    ) { storedPlans, wizard ->
        val normalizedPlans = if (storedPlans.size == 7) {
            storedPlans.sortedBy { it.dayOfWeek }
        } else {
            val map = storedPlans.associateBy { it.dayOfWeek }
            (0..6).map { day ->
                map[day] ?: WorkoutPlanEntity(
                    dayOfWeek = day,
                    isRestDay = true,
                    planName = "Rest Day"
                )
            }
        }

        WorkoutPlanUiState(
            weeklyPlan = normalizedPlans,
            isWizardOpen = wizard.isOpen,
            wizardStep = wizard.step,
            selectedDays = wizard.selectedDays,
            draftPlanNames = wizard.draftPlanNames,
            showDiscardDialog = wizard.showDiscardDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutPlanUiState()
    )

    fun openWizard() {
        val currentActive = uiState.value.weeklyPlan.filter { !it.isRestDay }
        val initialSelected = currentActive.map { it.dayOfWeek }.toSet()
        val initialNames = currentActive.associate { it.dayOfWeek to it.planName }
        wizardState.value = WizardState(
            isOpen = true,
            step = 1,
            selectedDays = initialSelected,
            draftPlanNames = initialNames,
            showDiscardDialog = false
        )
    }

    fun toggleDaySelection(dayOfWeek: Int) {
        if (dayOfWeek !in 0..6) return
        wizardState.update { current ->
            val updatedDays = if (dayOfWeek in current.selectedDays) {
                current.selectedDays - dayOfWeek
            } else {
                current.selectedDays + dayOfWeek
            }
            current.copy(selectedDays = updatedDays)
        }
    }

    fun goToNextStep() {
        wizardState.update { current ->
            if (current.selectedDays.isEmpty()) current else current.copy(step = 2)
        }
    }

    fun proceedToScheduleStep() {
        goToNextStep()
    }

    fun goToPreviousStep() {
        wizardState.update { it.copy(step = 1) }
    }

    fun returnToSelectDaysStep() {
        goToPreviousStep()
    }

    fun updateDraftPlanName(dayOfWeek: Int, name: String) {
        wizardState.update { current ->
            current.copy(draftPlanNames = current.draftPlanNames + (dayOfWeek to name))
        }
    }

    fun requestCancelWizard() {
        wizardState.update { it.copy(showDiscardDialog = true) }
    }

    fun dismissDiscardDialog() {
        wizardState.update { it.copy(showDiscardDialog = false) }
    }

    fun confirmDiscardWizard() {
        wizardState.value = WizardState()
    }

    fun saveWorkoutPlan() {
        val current = wizardState.value
        val selectedWithNames = current.selectedDays.associateWith { day ->
            current.draftPlanNames[day]?.trim().orEmpty().ifEmpty { "Workout" }
        }
        viewModelScope.launch {
            repository.saveWeeklyWorkoutPlan(selectedWithNames)
            wizardState.value = WizardState()
        }
    }

    fun saveSchedule() {
        saveWorkoutPlan()
    }

    companion object {
        fun factory(repository: GymRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WorkoutPlanViewModel(repository) as T
                }
            }
        }
    }
}

private fun defaultWeeklyPlan(): List<WorkoutPlanEntity> {
    return (0..6).map { day ->
        WorkoutPlanEntity(
            dayOfWeek = day,
            isRestDay = true,
            planName = "Rest Day"
        )
    }
}

