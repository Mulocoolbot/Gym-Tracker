package com.mulocoolbot.gymtracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.auth.AuthSession
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppStartDestination {
    SIGN_IN,
    PROFILE_SETUP,
    MAIN_HOME
}

data class AuthOnboardingUiState(
    val isLoading: Boolean = true,
    val authSession: AuthSession = AuthSession(),
    val userProfile: UserProfileEntity? = null,
    val destination: AppStartDestination = AppStartDestination.SIGN_IN
)

class AuthOnboardingViewModel(
    private val repository: GymRepository
) : ViewModel() {

    val uiState: StateFlow<AuthOnboardingUiState> = combine(
        repository.authSession,
        repository.userProfile
    ) { session, profile ->
        AuthOnboardingUiState(
            isLoading = false,
            authSession = session,
            userProfile = profile,
            destination = resolveDestination(session, profile)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = AuthOnboardingUiState()
    )

    init {
        viewModelScope.launch {
            repository.evaluateStreakOnLaunch(LocalDate.now().toEpochDay())
        }
    }

    fun onGoogleSignIn(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return
        viewModelScope.launch {
            repository.signInWithGoogle(cleanEmail)
        }
    }

    fun onEmailSignIn(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return
        viewModelScope.launch {
            repository.signInWithEmail(cleanEmail)
        }
    }

    fun onSubmitProfile(
        name: String,
        birthDateMillis: Long,
        weightKgInput: String,
        heightCmInput: String
    ) {
        val cleanName = name.trim()
        val weightKg = weightKgInput.trim().toDoubleOrNull() ?: return
        val heightCm = heightCmInput.trim().toDoubleOrNull() ?: return
        if (cleanName.isEmpty() || birthDateMillis <= 0L || weightKg <= 0.0 || heightCm <= 0.0) return

        viewModelScope.launch {
            repository.completeOnboardingProfile(
                name = cleanName,
                birthDateMillis = birthDateMillis,
                weightKg = weightKg,
                heightCm = heightCm
            )
        }
    }

    fun onToggleGoogleLink(defaultEmail: String) {
        viewModelScope.launch {
            val session = repository.authSession.first()
            if (session.isGoogleLinked) {
                repository.unlinkGoogle()
            } else {
                val targetEmail = defaultEmail.trim()
                    .ifEmpty { session.userEmail ?: "athlete@gmail.com" }
                repository.signInWithGoogle(targetEmail)
            }
        }
    }

    fun onToggleEmailLink(emailInput: String) {
        viewModelScope.launch {
            val session = repository.authSession.first()
            if (session.isEmailLinked) {
                repository.unlinkEmail()
            } else {
                val targetEmail = emailInput.trim()
                    .ifEmpty { session.googleEmail.orEmpty() }
                if (targetEmail.isNotEmpty()) {
                    repository.signInWithEmail(targetEmail)
                }
            }
        }
    }

    fun onUpdateProfile(
        name: String,
        weightKg: Double,
        heightCm: Double
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || weightKg <= 0.0 || heightCm <= 0.0) return
        viewModelScope.launch {
            repository.updateProfileMetrics(
                name = cleanName,
                weightKg = weightKg,
                heightCm = heightCm
            )
        }
    }

    companion object {
        fun resolveDestination(
            session: AuthSession,
            profile: UserProfileEntity?
        ): AppStartDestination {
            return when {
                !session.isAuthenticated -> AppStartDestination.SIGN_IN
                !session.isProfileCompleted || profile == null -> AppStartDestination.PROFILE_SETUP
                else -> AppStartDestination.MAIN_HOME
            }
        }

        fun factory(repository: GymRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthOnboardingViewModel(repository) as T
                }
            }
        }
    }
}

