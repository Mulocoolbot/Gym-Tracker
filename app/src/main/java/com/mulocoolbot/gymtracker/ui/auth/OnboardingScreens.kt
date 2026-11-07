package com.mulocoolbot.gymtracker.ui.auth

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mulocoolbot.gymtracker.data.auth.AuthEntryMode
import com.mulocoolbot.gymtracker.ui.components.ArrowRightIcon
import com.mulocoolbot.gymtracker.ui.components.DumbbellIcon
import com.mulocoolbot.gymtracker.ui.components.GoogleLogoIcon
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.round

private val AuthScreenBg = Color(0xFF0B0C0E)
private val AuthCardBg = Color(0xFF14161B)
private val AuthInputBg = Color(0xFF1A1D24)
private val AuthBorder = Color(0xFF232730)
private val AuthVolt = Color(0xFFCCFF00)
private val AuthWhite = Color(0xFFFFFFFF)
private val AuthMuted = Color(0xFF8C92A4)

@Composable
fun WelcomeScreen(
    onBeginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthScreenBg)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AuthCardBg)
                .border(1.dp, AuthBorder, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = DumbbellIcon,
                contentDescription = "MFIT Emblem",
                tint = AuthVolt,
                modifier = Modifier.size(30.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MFIT",
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.5).sp,
                color = AuthWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "See the Progress",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = AuthVolt
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(AuthVolt)
                .clickable(onClick = onBeginClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Begin",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OnVolt
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(OnVolt.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ArrowRightIcon,
                    contentDescription = "Begin",
                    tint = OnVolt,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SignInScreen(
    authStep: EmailAuthStep,
    pendingEmail: String,
    otpErrorMessage: String?,
    resendCountdownSeconds: Int,
    onGoogleAuthenticated: (email: String, displayName: String?) -> Unit,
    onSendEmailOtp: (email: String) -> Unit,
    onVerifyEmailOtp: (otpCode: String) -> Unit,
    onResendEmailOtp: () -> Unit,
    onBackToEmailInput: () -> Unit,
    onClearError: () -> Unit = {},
    authEntryMode: AuthEntryMode = AuthEntryMode.SIGN_IN,
    isLinkingFromSettings: Boolean = false,
    onSwitchAuthMode: (AuthEntryMode) -> Unit = {},
    onBackToWelcome: () -> Unit = {}
) {
    val launchGoogleAuth = rememberGoogleAuthLauncher(
        onSuccess = onGoogleAuthenticated
    )

    when (authStep) {
        EmailAuthStep.EMAIL_INPUT -> {
            EmailInputStepContent(
                authEntryMode = authEntryMode,
                isLinkingFromSettings = isLinkingFromSettings,
                otpErrorMessage = otpErrorMessage,
                onSwitchAuthMode = onSwitchAuthMode,
                onBackToWelcome = onBackToWelcome,
                onContinueWithGoogle = launchGoogleAuth,
                onSendCode = onSendEmailOtp,
                onClearError = onClearError
            )
        }
        EmailAuthStep.OTP_VERIFICATION -> {
            OtpVerificationStepContent(
                pendingEmail = pendingEmail,
                otpErrorMessage = otpErrorMessage,
                resendCountdownSeconds = resendCountdownSeconds,
                onVerifyCode = onVerifyEmailOtp,
                onResendCode = onResendEmailOtp,
                onBackToEmailInput = onBackToEmailInput,
                onClearError = onClearError
            )
        }
    }
}

@Composable
private fun EmailInputStepContent(
    authEntryMode: AuthEntryMode,
    isLinkingFromSettings: Boolean,
    otpErrorMessage: String?,
    onSwitchAuthMode: (AuthEntryMode) -> Unit,
    onBackToWelcome: () -> Unit,
    onContinueWithGoogle: () -> Unit,
    onSendCode: (String) -> Unit,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val isSignInMode = authEntryMode == AuthEntryMode.SIGN_IN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AuthCardBg)
                    .border(1.dp, AuthBorder, RoundedCornerShape(50))
                    .clickable(onClick = onBackToWelcome)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isLinkingFromSettings) "Cancel" else "Back",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuthWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AuthCardBg)
                .border(1.dp, AuthBorder, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AuthModeTabItem(
                label = "Sign In",
                isSelected = isSignInMode,
                modifier = Modifier.weight(1f),
                onClick = { onSwitchAuthMode(AuthEntryMode.SIGN_IN) }
            )
            AuthModeTabItem(
                label = "Log In",
                isSelected = !isSignInMode,
                modifier = Modifier.weight(1f),
                onClick = { onSwitchAuthMode(AuthEntryMode.LOG_IN) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isSignInMode) "Sign In" else "Log In",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AuthWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isSignInMode) {
                "Create a new MFit account to start tracking your progress."
            } else {
                "Welcome back. Log in with your registered account."
            },
            fontSize = 14.sp,
            color = AuthMuted
        )

        Spacer(modifier = Modifier.height(24.dp))

        GoogleSignInButton(
            label = if (isSignInMode) "Sign In with Google" else "Log In with Google",
            onClick = onContinueWithGoogle
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = AuthBorder,
                thickness = 1.dp
            )
            Text(
                text = "OR EMAIL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = AuthMuted,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                color = AuthBorder,
                thickness = 1.dp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = AuthCardBg),
            border = BorderStroke(1.dp, AuthBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "EMAIL ADDRESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = AuthMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "you@example.com", color = AuthMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(16.dp),
                    colors = bentoTextFieldColors()
                )

                if (!otpErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = otpErrorMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = DangerRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onSendCode(email.trim()) },
                    enabled = email.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuthVolt,
                        contentColor = OnVolt,
                        disabledContainerColor = AuthInputBg,
                        disabledContentColor = AuthMuted
                    )
                ) {
                    Text(
                        text = if (isSignInMode) {
                            "Sign In with Email (Send Code)"
                        } else {
                            "Log In with Email (Send Code)"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = {
                    val targetMode = if (isSignInMode) AuthEntryMode.LOG_IN else AuthEntryMode.SIGN_IN
                    onSwitchAuthMode(targetMode)
                }
            ) {
                Text(
                    text = if (isSignInMode) {
                        "Already have an account? Log In"
                    } else {
                        "New to MFit? Sign In (Create Account)"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuthVolt
                )
            }
        }
    }
}

@Composable
private fun AuthModeTabItem(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AuthVolt else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) OnVolt else AuthMuted
        )
    }
}

@Composable
private fun OtpVerificationStepContent(
    pendingEmail: String,
    otpErrorMessage: String?,
    resendCountdownSeconds: Int,
    onVerifyCode: (String) -> Unit,
    onResendCode: () -> Unit,
    onBackToEmailInput: () -> Unit,
    onClearError: () -> Unit
) {
    var otpCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AuthCardBg)
                    .border(1.dp, AuthBorder, RoundedCornerShape(50))
                    .clickable(onClick = onBackToEmailInput)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Change Email",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuthVolt
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Verify Email",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AuthWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = pendingEmail,
            fontSize = 14.sp,
            color = AuthMuted
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = AuthCardBg),
            border = BorderStroke(1.dp, AuthBorder)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SixDigitOtpInputField(
                    code = otpCode,
                    onCodeChange = { updated ->
                        otpCode = updated
                        onClearError()
                    }
                )

                if (!otpErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = otpErrorMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = DangerRed
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onVerifyCode(otpCode) },
                    enabled = otpCode.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuthVolt,
                        contentColor = OnVolt,
                        disabledContainerColor = AuthInputBg,
                        disabledContentColor = AuthMuted
                    )
                ) {
                    Text(
                        text = "Verify",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onResendCode,
                    enabled = resendCountdownSeconds <= 0
                ) {
                    val resendLabel = if (resendCountdownSeconds > 0) {
                        "Resend in ${resendCountdownSeconds}s"
                    } else {
                        "Resend Code"
                    }
                    Text(
                        text = resendLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (resendCountdownSeconds > 0) AuthMuted else AuthVolt
                    )
                }
            }
        }
    }
}

@Composable
fun SixDigitOtpInputField(
    code: String,
    onCodeChange: (String) -> Unit
) {
    BasicTextField(
        value = code,
        onValueChange = { raw ->
            val digitsOnly = raw.filter { it.isDigit() }.take(6)
            onCodeChange(digitsOnly)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(AuthVolt),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(6) { index ->
                    val charStr = code.getOrNull(index)?.toString().orEmpty()
                    val isFocusedIndex = code.length == index || (code.length == 6 && index == 5)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AuthInputBg)
                            .border(
                                width = 1.dp,
                                color = if (isFocusedIndex) AuthVolt else AuthBorder,
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = charStr,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = AuthWhite
                            )
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun GoogleSignInButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthWhite,
            contentColor = OnVolt
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = GoogleLogoIcon,
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 15.sp,
                color = OnVolt,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    initialName: String,
    onCompleteProfile: (name: String, birthDateMillis: Long, weightKg: String, heightCm: String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var birthDateText by remember { mutableStateOf("2000-01-01") }
    var weightKg by remember { mutableStateOf("70.0") }
    var heightCm by remember { mutableStateOf("175") }
    var showDatePicker by remember { mutableStateOf(false) }

    val birthDateMillis = parseBirthDateMillis(birthDateText)
    val calculatedAge = remember(birthDateMillis) {
        WorkoutCalculator.calculateAge(birthDateMillis)
    }

    fun adjustWeight(delta: Double) {
        val current = weightKg.toDoubleOrNull() ?: 70.0
        val next = (round((current + delta) * 10.0) / 10.0).coerceAtLeast(1.0)
        weightKg = if (next % 1.0 == 0.0) "%.1f".format(Locale.US, next) else "%.1f".format(Locale.US, next)
    }

    fun adjustHeight(delta: Int) {
        val current = heightCm.toDoubleOrNull() ?: 175.0
        val next = (current + delta).coerceAtLeast(50.0)
        heightCm = if (next % 1.0 == 0.0) next.toInt().toString() else "%.1f".format(Locale.US, next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthScreenBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profile Setup",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AuthWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Personalize your metrics for accurate calorie and strength calculations.",
            fontSize = 14.sp,
            color = AuthMuted
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = AuthCardBg),
            border = BorderStroke(1.dp, AuthBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FieldLabel(text = "Name")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "Name", color = AuthMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = bentoTextFieldColors()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FieldLabel(text = "Birth Date")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AuthVolt.copy(alpha = 0.16f))
                            .border(1.dp, AuthVolt.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Age: $calculatedAge",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = AuthVolt
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = birthDateText,
                    onValueChange = { birthDateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "YYYY-MM-DD", color = AuthMuted) },
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(
                                text = "Pick",
                                color = AuthVolt,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = bentoTextFieldColors()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricStepperColumn(
                        label = "Weight (kg)",
                        value = weightKg,
                        onValueChange = { weightKg = it },
                        onDecrement = { adjustWeight(-0.5) },
                        onIncrement = { adjustWeight(0.5) },
                        modifier = Modifier.weight(1f)
                    )

                    MetricStepperColumn(
                        label = "Height (cm)",
                        value = heightCm,
                        onValueChange = { heightCm = it },
                        onDecrement = { adjustHeight(-1) },
                        onIncrement = { adjustHeight(1) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val canSubmit = name.isNotBlank() &&
            birthDateMillis > 0L &&
            (weightKg.toDoubleOrNull() ?: 0.0) > 0.0 &&
            (heightCm.toDoubleOrNull() ?: 0.0) > 0.0

        Button(
            onClick = {
                onCompleteProfile(
                    name.trim(),
                    birthDateMillis,
                    weightKg.trim(),
                    heightCm.trim()
                )
            },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuthVolt,
                contentColor = OnVolt,
                disabledContainerColor = AuthInputBg,
                disabledContentColor = AuthMuted
            )
        ) {
            Text(
                text = "Continue",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (showDatePicker) {
        val initialMillis = parseBirthDateMillis(birthDateText)
            .takeIf { it > 0L }
            ?: LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedMillis ->
                            val date = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            birthDateText = date.toString()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(text = "Confirm", color = AuthVolt)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "Cancel", color = AuthMuted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun MetricStepperColumn(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        FieldLabel(text = label)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AuthInputBg)
                .border(1.dp, AuthBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuthCardBg)
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuthWhite
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(AuthVolt),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = AuthWhite,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuthCardBg)
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuthVolt
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = AuthMuted
    )
}

@Composable
private fun bentoTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AuthWhite,
    unfocusedTextColor = AuthWhite,
    focusedContainerColor = AuthInputBg,
    unfocusedContainerColor = AuthInputBg,
    focusedBorderColor = AuthVolt,
    unfocusedBorderColor = AuthBorder,
    cursorColor = AuthVolt
)

private fun parseBirthDateMillis(input: String): Long {
    return runCatching {
        LocalDate.parse(input.trim())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}
