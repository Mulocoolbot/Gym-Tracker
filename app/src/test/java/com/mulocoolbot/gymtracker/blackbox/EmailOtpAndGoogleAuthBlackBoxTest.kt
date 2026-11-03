package com.mulocoolbot.gymtracker.blackbox

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mulocoolbot.gymtracker.data.auth.EmailOtpManager
import com.mulocoolbot.gymtracker.data.auth.OtpRequestResult
import com.mulocoolbot.gymtracker.data.auth.OtpVerifyResult
import com.mulocoolbot.gymtracker.data.auth.SessionPreferences
import com.mulocoolbot.gymtracker.data.repository.GymRepository
import com.mulocoolbot.gymtracker.ui.InMemoryTestGymDao
import com.mulocoolbot.gymtracker.ui.auth.AppStartDestination
import com.mulocoolbot.gymtracker.ui.auth.AuthOnboardingViewModel
import com.mulocoolbot.gymtracker.ui.auth.EmailAuthStep
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EmailOtpAndGoogleAuthBlackBoxTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun tc_otp_01_invalid_email_partitions_rejected() {
        val otpManager = EmailOtpManager()

        val invalidInputs = listOf(
            "",
            "plainaddress",
            "user@domain",
            "user@domain.c"
        )

        invalidInputs.forEach { invalidEmail ->
            val (result, code) = otpManager.generateOtp(invalidEmail, currentMillis = 1_000_000L)
            assertEquals(OtpRequestResult.INVALID_EMAIL, result)
            assertNull(code)
        }
    }

    @Test
    fun tc_otp_02_valid_email_generates_6_digit_code_and_enforces_60s_cooldown() {
        val otpManager = EmailOtpManager()

        val (firstResult, firstCode) = otpManager.generateOtp(
            email = "alex@gmail.com",
            currentMillis = 1_000_000L
        )
        assertEquals(OtpRequestResult.SENT, firstResult)
        assertNotNull(firstCode)
        assertEquals(6, firstCode!!.length)
        assertTrue(firstCode.all { it.isDigit() })

        val (cooldownResult, cooldownCode) = otpManager.generateOtp(
            email = "alex@gmail.com",
            currentMillis = 1_059_999L
        )
        assertEquals(OtpRequestResult.COOLDOWN_ACTIVE, cooldownResult)
        assertNull(cooldownCode)

        val (boundaryResult, secondCode) = otpManager.generateOtp(
            email = "alex@gmail.com",
            currentMillis = 1_060_000L
        )
        assertEquals(OtpRequestResult.SENT, boundaryResult)
        assertNotNull(secondCode)
        assertEquals(6, secondCode!!.length)
        assertTrue(secondCode.all { it.isDigit() })
    }

    @Test
    fun tc_otp_03_and_04_otp_length_boundaries_and_wrong_code_rejected() {
        val otpManager = EmailOtpManager()
        val (_, generatedCode) = otpManager.generateOtp(
            email = "alex@gmail.com",
            currentMillis = 1_000_000L
        )
        assertNotNull(generatedCode)

        assertEquals(
            OtpVerifyResult.INVALID_FORMAT,
            otpManager.verifyOtp("alex@gmail.com", "12345", currentMillis = 1_010_000L)
        )
        assertEquals(
            OtpVerifyResult.INVALID_FORMAT,
            otpManager.verifyOtp("alex@gmail.com", "1234567", currentMillis = 1_010_000L)
        )
        assertEquals(
            OtpVerifyResult.INVALID_FORMAT,
            otpManager.verifyOtp("alex@gmail.com", "12345A", currentMillis = 1_010_000L)
        )

        val wrongSixDigitCode = if (generatedCode == "999999") "111111" else "999999"
        assertEquals(
            OtpVerifyResult.INVALID_CODE,
            otpManager.verifyOtp("alex@gmail.com", wrongSixDigitCode, currentMillis = 1_010_000L)
        )
    }

    @Test
    fun tc_otp_05_and_06_expiration_boundary_5_minutes_and_exact_match_success() {
        val otpManager = EmailOtpManager()

        val (_, firstCode) = otpManager.generateOtp(
            email = "alex@gmail.com",
            currentMillis = 1_000_000L
        )
        assertNotNull(firstCode)

        assertEquals(
            OtpVerifyResult.EXPIRED_CODE,
            otpManager.verifyOtp(
                email = "alex@gmail.com",
                inputCode = firstCode!!,
                currentMillis = 1_300_001L
            )
        )

        val (_, secondCode) = otpManager.generateOtp(
            email = "alex@gmail.com",
            currentMillis = 2_000_000L
        )
        assertNotNull(secondCode)

        assertEquals(
            OtpVerifyResult.SUCCESS,
            otpManager.verifyOtp(
                email = "alex@gmail.com",
                inputCode = secondCode!!,
                currentMillis = 2_300_000L
            )
        )

        assertEquals(
            OtpVerifyResult.NO_PENDING_OTP,
            otpManager.verifyOtp(
                email = "alex@gmail.com",
                inputCode = secondCode,
                currentMillis = 2_300_000L
            )
        )
    }

    @Test
    fun tc_auth_flow_unverified_email_cannot_authenticate() = runTest(testDispatcher) {
        val dataStoreScope = CoroutineScope(testDispatcher + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = dataStoreScope) {
            File(tempFolder.newFolder(), "bb_otp_auth.preferences_pb")
        }
        val repository = GymRepository(
            dao = InMemoryTestGymDao(),
            sessionPreferences = SessionPreferences(dataStore)
        )
        val viewModel = AuthOnboardingViewModel(repository)

        viewModel.requestEmailOtp(
            context = null,
            emailInput = "alex@gmail.com",
            currentMillis = 1_000_000L
        )

        assertEquals(EmailAuthStep.OTP_VERIFICATION, viewModel.uiState.value.authStep)
        assertEquals("alex@gmail.com", viewModel.uiState.value.pendingEmail)
        assertFalse(repository.authSession.first().isAuthenticated)
        assertEquals(AppStartDestination.SIGN_IN, viewModel.uiState.value.destination)

        val generatedOtp = viewModel.lastGeneratedOtpCode!!
        val wrongOtp = if (generatedOtp == "000000") "111111" else "000000"
        viewModel.verifyEmailOtp(otpInput = wrongOtp, currentMillis = 1_005_000L)

        assertFalse(repository.authSession.first().isAuthenticated)
        assertEquals(AppStartDestination.SIGN_IN, viewModel.uiState.value.destination)
        assertNotNull(viewModel.uiState.value.otpErrorMessage)

        viewModel.verifyEmailOtp(otpInput = generatedOtp, currentMillis = 1_010_000L)

        val verifiedSession = repository.authSession.first()
        assertTrue(verifiedSession.isAuthenticated)
        assertTrue(verifiedSession.isEmailLinked)
        assertEquals("alex@gmail.com", verifiedSession.userEmail)
        assertEquals(AppStartDestination.PROFILE_SETUP, viewModel.uiState.value.destination)
    }
}

