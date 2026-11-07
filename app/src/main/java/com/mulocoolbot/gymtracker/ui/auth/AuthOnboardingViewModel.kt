package com.mulocoolbot.gymtracker.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.data.auth.AuthSession
import com.mulocoolbot.gymtracker.data.auth.EmailOtpManager
import com.mulocoolbot.gymtracker.data.auth.EmailOtpSender
import com.mulocoolbot.gymtracker.data.auth.OtpRequestResult
import com.mulocoolbot.gymtracker.data.auth.OtpVerifyResult
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppStartDestination {
    WELCOME,
    SIGN_IN,
    PROFILE_SETUP,
    MAIN_HOME
}

enum class EmailAuthStep {
    EMAIL_INPUT,
    OTP_VERIFICATION
}

data class AuthOnboardingUiState(
    val isLoading: Boolean = true,
    val isOpeningPlaying: Boolean = true,
    val authSession: AuthSession = AuthSession(),
    val userProfile: UserProfileEntity? = null,
    val hasPassedWelcome: Boolean = false,
    val authEntryMode: AuthEntryMode = AuthEntryMode.SIGN_IN,
    val destination: AppStartDestination = AppStartDestination.WELCOME,
    val authStep: EmailAuthStep = EmailAuthStep.EMAIL_INPUT,
    val pendingEmail: String = "",
    val otpErrorMessage: String? = null,
    val resendCountdownSeconds: Int = 0,
    val suggestedDisplayName: String? = null,
    val authError: String? = otpErrorMessage
)

private data class OtpFlowInternalState(
    val isOpeningPlaying: Boolean = true,
    val hasPassedWelcome: Boolean = false,
    val authEntryMode: AuthEntryMode = AuthEntryMode.SIGN_IN,
    val authStep: EmailAuthStep = EmailAuthStep.EMAIL_INPUT,
    val pendingEmail: String = "",
    val otpErrorMessage: String? = null,
    val resendCountdownSeconds: Int = 0,
    val suggestedDisplayName: String? = null
)

class AuthOnboardingViewModel(
    private val repository: GymRepository,
    val otpManager: EmailOtpManager = EmailOtpManager()
) : ViewModel() {

    private val otpFlowState = MutableStateFlow(OtpFlowInternalState())
    private val countdownScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdownJob: Job? = null

    var lastGeneratedOtpCode: String? = null
        private set

    val uiState: StateFlow<AuthOnboardingUiState> = combine(
        repository.authSession,
        repository.userProfile,
        otpFlowState
    ) { session, profile, otpState ->
        AuthOnboardingUiState(
            isLoading = false,
            isOpeningPlaying = otpState.isOpeningPlaying,
            authSession = session,
            userProfile = profile,
            hasPassedWelcome = otpState.hasPassedWelcome,
            authEntryMode = otpState.authEntryMode,
            destination = resolveDestination(
                session = session,
                profile = profile,
                hasPassedWelcome = otpState.hasPassedWelcome
            ),
            authStep = otpState.authStep,
            pendingEmail = otpState.pendingEmail,
            otpErrorMessage = otpState.otpErrorMessage,
            resendCountdownSeconds = otpState.resendCountdownSeconds,
            suggestedDisplayName = otpState.suggestedDisplayName,
            authError = otpState.otpErrorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AuthOnboardingUiState(isLoading = true, isOpeningPlaying = true)
    )

    init {
        viewModelScope.launch {
            val initialSession = repository.authSession.first()
            if (initialSession.isAuthenticated && initialSession.isProfileCompleted) {
                repository.evaluateStreakOnLaunch(LocalDate.now().toEpochDay())
            }
        }
    }

    fun onOpeningAnimationFinished() {
        otpFlowState.update { current ->
            current.copy(isOpeningPlaying = false)
        }
    }

    fun onBeginFromWelcome() {
        otpFlowState.update {
            it.copy(
                hasPassedWelcome = true,
                authEntryMode = AuthEntryMode.SIGN_IN,
                otpErrorMessage = null
            )
        }
    }

    fun onBackToWelcome() {
        countdownJob?.cancel()
        otpFlowState.update {
            it.copy(
                hasPassedWelcome = false,
                authStep = EmailAuthStep.EMAIL_INPUT,
                otpErrorMessage = null,
                resendCountdownSeconds = 0
            )
        }
    }

    fun setAuthEntryMode(mode: AuthEntryMode) {
        otpFlowState.update {
            it.copy(
                authEntryMode = mode,
                otpErrorMessage = null
            )
        }
    }

    fun requestEmailOtp(
        context: Context? = null,
        emailInput: String,
        isResend: Boolean = false,
        currentMillis: Long = System.currentTimeMillis()
    ) {
        val cleanEmail = emailInput.trim()
        otpFlowState.update { it.copy(hasPassedWelcome = true) }

        if (!otpManager.isValidEmail(cleanEmail)) {
            otpFlowState.update { it.copy(otpErrorMessage = "Invalid email address") }
            return
        }

        val currentMode = otpFlowState.value.authEntryMode
        if (currentMode == AuthEntryMode.LOG_IN) {
            viewModelScope.launch {
                val registered = repository.isEmailRegistered(cleanEmail)
                val hasProfile = repository.hasExistingUserProfile(cleanEmail)
                if (!registered && !hasProfile) {
                    otpFlowState.update {
                        it.copy(
                            otpErrorMessage = "Account not found. Switch to Sign In to create an account."
                        )
                    }
                    return@launch
                }
                dispatchEmailOtpGeneration(
                    context = context,
                    cleanEmail = cleanEmail,
                    isResend = isResend,
                    currentMillis = currentMillis
                )
            }
        } else {
            dispatchEmailOtpGeneration(
                context = context,
                cleanEmail = cleanEmail,
                isResend = isResend,
                currentMillis = currentMillis
            )
        }
    }

    private fun dispatchEmailOtpGeneration(
        context: Context?,
        cleanEmail: String,
        isResend: Boolean,
        currentMillis: Long
    ) {
        val (result, code) = otpManager.generateOtp(
            email = cleanEmail,
            currentMillis = currentMillis,
            forceResend = isResend
        )

        when (result) {
            OtpRequestResult.INVALID_EMAIL -> {
                otpFlowState.update { it.copy(otpErrorMessage = "Invalid email address") }
            }
            OtpRequestResult.COOLDOWN_ACTIVE -> {
                val remaining = otpManager.remainingCooldownSeconds(currentMillis)
                otpFlowState.update {
                    it.copy(
                        otpErrorMessage = "Please wait ${remaining}s before resending",
                        resendCountdownSeconds = remaining
                    )
                }
            }
            OtpRequestResult.SENT -> {
                lastGeneratedOtpCode = code
                otpFlowState.update {
                    it.copy(
                        authStep = EmailAuthStep.OTP_VERIFICATION,
                        pendingEmail = cleanEmail,
                        otpErrorMessage = null,
                        resendCountdownSeconds = 60
                    )
                }
                startResendCountdown()
                if (context != null && code != null) {
                    viewModelScope.launch {
                        EmailOtpSender.sendOtpEmail(context, cleanEmail, code)
                    }
                }
            }
        }
    }

    fun verifyEmailOtp(
        otpInput: String,
        currentMillis: Long = System.currentTimeMillis()
    ) {
        val targetEmail = otpFlowState.value.pendingEmail
        val entryMode = otpFlowState.value.authEntryMode
        val verifyResult = otpManager.verifyOtp(
            email = targetEmail,
            inputCode = otpInput,
            currentMillis = currentMillis
        )

        when (verifyResult) {
            OtpVerifyResult.SUCCESS -> {
                countdownJob?.cancel()
                otpFlowState.update {
                    it.copy(
                        authStep = EmailAuthStep.EMAIL_INPUT,
                        otpErrorMessage = null,
                        resendCountdownSeconds = 0
                    )
                }
                viewModelScope.launch {
                    repository.signInWithEmail(targetEmail, entryMode)
                }
            }
            OtpVerifyResult.EXPIRED_CODE -> {
                otpFlowState.update { it.copy(otpErrorMessage = "Verification code expired") }
            }
            OtpVerifyResult.INVALID_FORMAT,
            OtpVerifyResult.INVALID_CODE,
            OtpVerifyResult.NO_PENDING_OTP -> {
                otpFlowState.update { it.copy(otpErrorMessage = "Invalid verification code") }
            }
        }
    }

    fun backToEmailInput() {
        countdownJob?.cancel()
        otpFlowState.update {
            it.copy(
                authStep = EmailAuthStep.EMAIL_INPUT,
                otpErrorMessage = null,
                resendCountdownSeconds = 0
            )
        }
    }

    fun onGoogleAuthenticated(email: String, displayName: String? = null) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return
        val entryMode = otpFlowState.value.authEntryMode
        otpFlowState.update {
            it.copy(
                hasPassedWelcome = true,
                otpErrorMessage = null,
                suggestedDisplayName = displayName?.trim()?.takeIf { name -> name.isNotEmpty() }
            )
        }
        viewModelScope.launch {
            repository.signInWithGoogle(cleanEmail, entryMode)
        }
    }

    fun onCreateAccount(email: String, password: String) {
        val cleanEmail = email.trim()
        otpFlowState.update { it.copy(hasPassedWelcome = true) }
        if (!otpManager.isValidEmail(cleanEmail) || password.length < 6) {
            otpFlowState.update {
                it.copy(otpErrorMessage = "Enter a valid email and 6+ character password")
            }
            return
        }
        otpFlowState.update { it.copy(otpErrorMessage = null) }
        viewModelScope.launch {
            val registered = repository.registerWithEmailPassword(cleanEmail, password)
            if (!registered) {
                otpFlowState.update { it.copy(otpErrorMessage = "Could not create account") }
            }
        }
    }

    fun onSignInWithEmailPassword(email: String, password: String) {
        val cleanEmail = email.trim()
        otpFlowState.update { it.copy(hasPassedWelcome = true) }
        if (!otpManager.isValidEmail(cleanEmail) || password.length < 6) {
            otpFlowState.update { it.copy(otpErrorMessage = "Enter your email and password") }
            return
        }
        otpFlowState.update { it.copy(otpErrorMessage = null) }
        viewModelScope.launch {
            val success = repository.loginWithEmailPassword(cleanEmail, password)
            if (!success) {
                otpFlowState.update { it.copy(otpErrorMessage = "Invalid email or password") }
            }
        }
    }

    fun clearAuthError() {
        otpFlowState.update { it.copy(otpErrorMessage = null) }
    }

    fun onGoogleSignIn(email: String) {
        onGoogleAuthenticated(email = email, displayName = null)
    }

    fun onEmailSignIn(email: String) {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) return
        val entryMode = otpFlowState.value.authEntryMode
        otpFlowState.update {
            it.copy(
                hasPassedWelcome = true,
                otpErrorMessage = null
            )
        }
        viewModelScope.launch {
            repository.signInWithEmail(cleanEmail, entryMode)
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

    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = countdownScope.launch {
            var remaining = 60
            while (isActive && remaining > 0) {
                delay(1_000L)
                remaining--
                otpFlowState.update { it.copy(resendCountdownSeconds = remaining) }
            }
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        countdownScope.cancel()
        super.onCleared()
    }

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun resolveDestination(
            session: AuthSession,
            profile: UserProfileEntity? = null,
            hasPassedWelcome: Boolean = true
        ): AppStartDestination {
            return when {
                !session.isAuthenticated -> {
                    if (hasPassedWelcome) AppStartDestination.SIGN_IN else AppStartDestination.WELCOME
                }
                !session.isProfileCompleted -> AppStartDestination.PROFILE_SETUP
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
