package com.mulocoolbot.gymtracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.auth.AuthSession
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
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
    val destination: AppStartDestination = AppStartDestination.SIGN_IN,
    val authError: String? = null
)

class AuthOnboardingViewModel(
    private val repository: GymRepository
) : ViewModel() {

    private val authErrorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AuthOnboardingUiState> = combine(
        repository.authSession,
        repository.userProfile,
        authErrorFlow
    ) { session, profile, error ->
        AuthOnboardingUiState(
            isLoading = false,
            authSession = session,
            userProfile = profile,
            destination = resolveDestination(session, profile),
            authError = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthOnboardingUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            val initialSession = repository.authSession.first()
            if (initialSession.isAuthenticated && initialSession.isProfileCompleted) {
                repository.evaluateStreakOnLaunch(LocalDate.now().toEpochDay())
            }
        }
    }

    fun onCreateAccount(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.length < 6) {
            authErrorFlow.value = "Enter a valid email and 6+ character password"
            return
        }
        authErrorFlow.value = null
        viewModelScope.launch {
            val registered = repository.registerWithEmailPassword(cleanEmail, password)
            if (!registered) {
                authErrorFlow.value = "Could not create account"
            }
        }
    }

    fun onSignInWithEmailPassword(email: String, password: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.length < 6) {
            authErrorFlow.value = "Enter your email and password"
            return
        }
        authErrorFlow.value = null
        viewModelScope.launch {
            val success = repository.loginWithEmailPassword(cleanEmail, password)
            if (!success) {
                authErrorFlow.value = "Invalid email or password"
            }
        }
    }

    fun clearAuthError() {
        authErrorFlow.value = null
    }

    fun onGoogleSignIn(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return
        authErrorFlow.value = null
        viewModelScope.launch {
            repository.signInWithGoogle(cleanEmail)
        }
    }

    fun onEmailSignIn(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return
        authErrorFlow.value = null
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

    fun onToggleGoogleLink(googleEmailInput: String) {
        viewModelScope.launch {
            val session = repository.authSession.first()
            if (session.isGoogleLinked) {
                repository.unlinkGoogle()
            } else {
                val targetEmail = googleEmailInput.trim()
                if (targetEmail.isNotEmpty()) {
                    repository.signInWithGoogle(targetEmail)
                }
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

    suspend fun exportBackupJson(): String {
        return repository.exportBackupJson()
    }

    suspend fun importBackupJson(jsonString: String): Boolean {
        return repository.importBackupJson(jsonString)
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
