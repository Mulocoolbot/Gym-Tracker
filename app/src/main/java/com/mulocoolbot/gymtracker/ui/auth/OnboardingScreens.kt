package com.mulocoolbot.gymtracker.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mulocoolbot.gymtracker.ui.components.GoogleLogoIcon
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
    onClearError: () -> Unit = {}
) {
    val launchGoogleAuth = rememberGoogleAuthLauncher(
        onSuccess = onGoogleAuthenticated
    )

    when (authStep) {
        EmailAuthStep.EMAIL_INPUT -> {
            EmailInputStepContent(
                otpErrorMessage = otpErrorMessage,
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
    otpErrorMessage: String?,
    onContinueWithGoogle: () -> Unit,
    onSendCode: (String) -> Unit,
    onClearError: () -> Unit
) {
    var email by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign In",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(28.dp))

        GoogleSignInButton(onClick = onContinueWithGoogle)

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        onClearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "Email address", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(10.dp),
                    colors = gymTextFieldColors()
                )

                if (!otpErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = otpErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onSendCode(email.trim()) },
                    enabled = email.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoltAccent,
                        contentColor = OnVolt,
                        disabledContainerColor = InputSurface,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Text(
                        text = "Send Code",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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
            .background(AppBackground)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        TextButton(onClick = onBackToEmailInput) {
            Text(
                text = "Change Email",
                style = MaterialTheme.typography.labelLarge,
                color = VoltAccent
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Verify Email",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = pendingEmail,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder)
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
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onVerifyCode(otpCode) },
                    enabled = otpCode.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VoltAccent,
                        contentColor = OnVolt,
                        disabledContainerColor = InputSurface,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Text(
                        text = "Verify",
                        style = MaterialTheme.typography.labelLarge,
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
                        style = MaterialTheme.typography.labelLarge,
                        color = if (resendCountdownSeconds > 0) TextMuted else VoltAccent
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
        cursorBrush = SolidColor(VoltAccent),
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(InputSurface)
                            .border(
                                width = 1.dp,
                                color = if (isFocusedIndex) VoltAccent else CardBorder,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = charStr,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = TextPrimary
                            )
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun GoogleSignInButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
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
                text = "Continue with Google",
                style = MaterialTheme.typography.labelLarge,
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
    var weightKg by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profile Setup",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FieldLabel(text = "Name")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "Name", color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = gymTextFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))

                FieldLabel(text = "Birth Date")
                OutlinedTextField(
                    value = birthDateText,
                    onValueChange = { birthDateText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = "YYYY-MM-DD", color = TextMuted) },
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(text = "Pick", color = VoltAccent)
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = gymTextFieldColors()
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel(text = "Weight (kg)")
                        OutlinedTextField(
                            value = weightKg,
                            onValueChange = { weightKg = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(text = "70.0", color = TextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            colors = gymTextFieldColors()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        FieldLabel(text = "Height (cm)")
                        OutlinedTextField(
                            value = heightCm,
                            onValueChange = { heightCm = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(text = "175.0", color = TextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            colors = gymTextFieldColors()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val birthDateMillis = parseBirthDateMillis(birthDateText)
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
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VoltAccent,
                contentColor = OnVolt,
                disabledContainerColor = InputSurface,
                disabledContentColor = TextMuted
            )
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.labelLarge,
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
                    Text(text = "Confirm", color = VoltAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "Cancel", color = TextMuted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextMuted,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun gymTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = InputSurface,
    unfocusedContainerColor = InputSurface,
    focusedBorderColor = VoltAccent,
    unfocusedBorderColor = CardBorder,
    cursorColor = VoltAccent
)

private fun parseBirthDateMillis(input: String): Long {
    return runCatching {
        LocalDate.parse(input.trim())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)
}
