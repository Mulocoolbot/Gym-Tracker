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

enum class WorkoutPlanSubPage {
    EMPTY,
    SELECT_DAYS,
    INPUT_PLAN,
    SAVED
}

data class WorkoutPlanUiState(
    val subPage: WorkoutPlanSubPage = WorkoutPlanSubPage.EMPTY,
    val hasSavedPlan: Boolean = false,
    val savedDays: List<WorkoutPlanEntity> = defaultWeeklyPlan(),
    val selectedDays: Set<Int> = emptySet(),
    val draftPlanNames: Map<Int, String> = emptyMap(),
    val showDiscardDialog: Boolean = false
) {
    val weeklyPlan: List<WorkoutPlanEntity>
        get() = savedDays

    val isWizardOpen: Boolean
        get() = subPage == WorkoutPlanSubPage.SELECT_DAYS || subPage == WorkoutPlanSubPage.INPUT_PLAN

    val wizardStep: Int
        get() = if (subPage == WorkoutPlanSubPage.INPUT_PLAN) 2 else 1
}

private data class BuilderState(
    val activeBuilderPage: WorkoutPlanSubPage? = null,
    val selectedDays: Set<Int> = emptySet(),
    val draftPlanNames: Map<Int, String> = emptyMap(),
    val showDiscardDialog: Boolean = false
)

class WorkoutPlanViewModel(
    private val repository: GymRepository
) : ViewModel() {

    private val builderState = MutableStateFlow(BuilderState())

    val uiState: StateFlow<WorkoutPlanUiState> = combine(
        repository.weeklyPlan,
        builderState
    ) { planList, builder ->
        val planByDay = planList.associateBy { it.dayOfWeek }
        val normalizedPlans = (0..6).map { day ->
            planByDay[day] ?: WorkoutPlanEntity(
                dayOfWeek = day,
                isRestDay = true,
                planName = "Rest Day"
            )
        }
        val hasSavedPlan = normalizedPlans.any { !it.isRestDay }
        val resolvedSubPage = when (builder.activeBuilderPage) {
            WorkoutPlanSubPage.SELECT_DAYS,
            WorkoutPlanSubPage.INPUT_PLAN -> builder.activeBuilderPage
            else -> if (hasSavedPlan) WorkoutPlanSubPage.SAVED else WorkoutPlanSubPage.EMPTY
        }

        WorkoutPlanUiState(
            subPage = resolvedSubPage,
            hasSavedPlan = hasSavedPlan,
            savedDays = normalizedPlans,
            selectedDays = builder.selectedDays,
            draftPlanNames = builder.draftPlanNames,
            showDiscardDialog = builder.showDiscardDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkoutPlanUiState()
    )

    fun startCreateOrEditPlan() {
        val currentState = uiState.value
        val activeDays = if (currentState.hasSavedPlan) {
            currentState.savedDays.filter { !it.isRestDay }
        } else {
            emptyList()
        }
        builderState.value = BuilderState(
            activeBuilderPage = WorkoutPlanSubPage.SELECT_DAYS,
            selectedDays = activeDays.map { it.dayOfWeek }.toSet(),
            draftPlanNames = activeDays.associate { it.dayOfWeek to it.planName },
            showDiscardDialog = false
        )
    }

    fun toggleDaySelection(dayOfWeek: Int) {
        if (dayOfWeek !in 0..6) return
        builderState.update { current ->
            val updatedDays = if (dayOfWeek in current.selectedDays) {
                current.selectedDays - dayOfWeek
            } else {
                current.selectedDays + dayOfWeek
            }
            current.copy(selectedDays = updatedDays)
        }
    }

    fun goToInputPlanPage() {
        builderState.update { current ->
            if (current.selectedDays.isNotEmpty()) {
                current.copy(activeBuilderPage = WorkoutPlanSubPage.INPUT_PLAN)
            } else {
                current
            }
        }
    }

    fun goToEditDaysPage() {
        builderState.update { current ->
            current.copy(activeBuilderPage = WorkoutPlanSubPage.SELECT_DAYS)
        }
    }

    fun updateDraftPlanName(dayOfWeek: Int, name: String) {
        if (dayOfWeek !in 0..6) return
        builderState.update { current ->
            current.copy(draftPlanNames = current.draftPlanNames + (dayOfWeek to name))
        }
    }

    fun requestCancel() {
        builderState.update { current ->
            current.copy(showDiscardDialog = true)
        }
    }

    fun dismissDiscardDialog() {
        builderState.update { current ->
            current.copy(showDiscardDialog = false)
        }
    }

    fun confirmDiscard() {
        builderState.value = BuilderState()
    }

    fun saveWorkoutPlan() {
        val current = builderState.value
        val selectedWithNames = current.selectedDays.associateWith { day ->
            current.draftPlanNames[day]?.trim().takeUnless { it.isNullOrEmpty() } ?: "Workout"
        }
        viewModelScope.launch {
            repository.saveWeeklyWorkoutPlan(selectedWithNames)
            builderState.value = BuilderState()
        }
    }

    fun openWizard() {
        startCreateOrEditPlan()
    }

    fun goToNextStep() {
        goToInputPlanPage()
    }

    fun proceedToScheduleStep() {
        goToInputPlanPage()
    }

    fun goToPreviousStep() {
        goToEditDaysPage()
    }

    fun returnToSelectDaysStep() {
        goToEditDaysPage()
    }

    fun requestCancelWizard() {
        requestCancel()
    }

    fun confirmDiscardWizard() {
        confirmDiscard()
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
