package com.mulocoolbot.gymtracker.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mulocoolbot.gymtracker.data.auth.AuthSession
import com.mulocoolbot.gymtracker.data.auth.EmailOtpManager
import com.mulocoolbot.gymtracker.data.auth.EmailOtpSender
import com.mulocoolbot.gymtracker.data.auth.OtpRequestResult
import com.mulocoolbot.gymtracker.data.auth.OtpVerifyResult
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.ui.auth.SixDigitOtpInputField
import com.mulocoolbot.gymtracker.ui.auth.rememberGoogleAuthLauncher
import com.mulocoolbot.gymtracker.ui.components.GoogleLogoIcon
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import java.util.Locale
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SettingsCardBg = Color(0xFF14161B)
private val SettingsInnerBg = Color(0xFF1A1D24)
private val SettingsBorder = Color(0xFF232730)
private val SettingsVolt = Color(0xFFCCFF00)
private val SettingsWhite = Color(0xFFFFFFFF)
private val SettingsMuted = Color(0xFF8C92A4)
private val SettingsDanger = Color(0xFFEF4444)

@Composable
fun SettingsScreen(
    authSession: AuthSession,
    userProfile: UserProfileEntity?,
    onUpdateProfile: (name: String, weightKg: Double, heightCm: Double) -> Unit,
    onToggleGoogleLink: (defaultEmail: String) -> Unit,
    onToggleEmailLink: (emailInput: String) -> Unit,
    onExportBackup: suspend () -> String = { "" },
    onImportBackup: suspend (String) -> Boolean = { false }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val json = onExportBackup()
                if (json.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(json.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val jsonContent = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)
                            ?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() }
                    }.getOrNull()
                }
                if (!jsonContent.isNullOrBlank()) {
                    onImportBackup(jsonContent)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = SettingsWhite
        )

        Spacer(modifier = Modifier.height(20.dp))

        AboutMeBentoCard(
            uid = userProfile?.uid ?: authSession.uid,
            userProfile = userProfile,
            onUpdateProfile = onUpdateProfile
        )

        Spacer(modifier = Modifier.height(20.dp))

        AccountInfoBentoCard(
            authSession = authSession,
            onToggleGoogleLink = onToggleGoogleLink,
            onToggleEmailLink = onToggleEmailLink
        )

        Spacer(modifier = Modifier.height(20.dp))

        DataBackupBentoCard(
            onExportClick = { exportLauncher.launch("mfit_backup.json") },
            onImportClick = { importLauncher.launch(arrayOf("application/json")) }
        )
    }
}

@Composable
private fun AboutMeBentoCard(
    uid: String,
    userProfile: UserProfileEntity?,
    onUpdateProfile: (name: String, weightKg: Double, heightCm: Double) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    var nameInput by remember(userProfile?.name) {
        mutableStateOf(userProfile?.name.orEmpty())
    }
    var weightInput by remember(userProfile?.weightKg) {
        mutableStateOf(userProfile?.weightKg?.let { formatMetricNumber(it) } ?: "70.0")
    }
    var heightInput by remember(userProfile?.heightCm) {
        mutableStateOf(userProfile?.heightCm?.let { formatMetricNumber(it) } ?: "175")
    }

    val ageValue = remember(userProfile?.birthDateMillis) {
        WorkoutCalculator.calculateAge(userProfile?.birthDateMillis ?: 0L)
    }

    fun commitIfValid(newName: String, newWeight: String, newHeight: String) {
        val cleanName = newName.trim().ifEmpty { userProfile?.name?.trim().orEmpty() }
        val parsedWeight = newWeight.trim().toDoubleOrNull() ?: return
        val parsedHeight = newHeight.trim().toDoubleOrNull() ?: return
        if (cleanName.isNotEmpty() && parsedWeight > 0.0 && parsedHeight > 0.0) {
            onUpdateProfile(cleanName, parsedWeight, parsedHeight)
        }
    }

    fun stepWeight(delta: Double) {
        val current = weightInput.toDoubleOrNull() ?: userProfile?.weightKg ?: 70.0
        val updated = (round((current + delta) * 10.0) / 10.0).coerceAtLeast(1.0)
        val formatted = formatMetricNumber(updated)
        weightInput = formatted
        commitIfValid(nameInput, formatted, heightInput)
    }

    fun stepHeight(delta: Int) {
        val current = heightInput.toDoubleOrNull() ?: userProfile?.heightCm ?: 175.0
        val updated = (current + delta).coerceAtLeast(50.0)
        val formatted = formatMetricNumber(updated)
        heightInput = formatted
        commitIfValid(nameInput, weightInput, formatted)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsCardBg),
        border = BorderStroke(1.dp, SettingsBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ABOUT ME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = SettingsMuted
                )

                val displayUid = uid.ifBlank { "-" }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SettingsInnerBg)
                        .border(1.dp, SettingsBorder, RoundedCornerShape(50))
                        .clickable {
                            if (uid.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(uid))
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = displayUid,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SettingsVolt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(140.dp),
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SettingsInnerBg)
                        .border(1.dp, SettingsBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "NAME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = SettingsMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicTextField(
                        value = nameInput,
                        onValueChange = { updated ->
                            nameInput = updated
                            commitIfValid(updated, weightInput, heightInput)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        cursorBrush = SolidColor(SettingsVolt),
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = SettingsWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SettingsInnerBg)
                        .border(1.dp, SettingsBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "AGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = SettingsMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${ageValue} yrs",
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SettingsWhite,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsMetricStepperBox(
                    label = "WEIGHT (KG)",
                    value = weightInput,
                    onValueChange = { updated ->
                        weightInput = updated
                        commitIfValid(nameInput, updated, heightInput)
                    },
                    onDecrement = { stepWeight(-0.5) },
                    onIncrement = { stepWeight(0.5) },
                    modifier = Modifier.weight(1f)
                )

                SettingsMetricStepperBox(
                    label = "HEIGHT (CM)",
                    value = heightInput,
                    onValueChange = { updated ->
                        heightInput = updated
                        commitIfValid(nameInput, weightInput, updated)
                    },
                    onDecrement = { stepHeight(-1) },
                    onIncrement = { stepHeight(1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SettingsMetricStepperBox(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SettingsInnerBg)
            .border(1.dp, SettingsBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = SettingsMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SettingsCardBg)
                    .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SettingsWhite
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(SettingsVolt),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = SettingsWhite,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SettingsCardBg)
                    .border(1.dp, SettingsBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SettingsVolt
                )
            }
        }
    }
}

@Composable
private fun AccountInfoBentoCard(
    authSession: AuthSession,
    onToggleGoogleLink: (defaultEmail: String) -> Unit,
    onToggleEmailLink: (emailInput: String) -> Unit
) {
    var showEmailOtpLinkDialog by remember { mutableStateOf(false) }

    val launchGoogleLink = rememberGoogleAuthLauncher(
        onSuccess = { selectedEmail, _ ->
            onToggleGoogleLink(selectedEmail)
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsCardBg),
        border = BorderStroke(1.dp, SettingsBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "ACCOUNT INFO",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = SettingsMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            ProviderStatusBentoRow(
                providerName = "Google",
                linkedAddress = if (authSession.isGoogleLinked) {
                    authSession.googleEmail ?: "Linked"
                } else {
                    "Not linked"
                },
                isLinked = authSession.isGoogleLinked,
                iconContent = {
                    Icon(
                        imageVector = GoogleLogoIcon,
                        contentDescription = "Google",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onActionClick = {
                    if (authSession.isGoogleLinked) {
                        onToggleGoogleLink("")
                    } else {
                        launchGoogleLink()
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            ProviderStatusBentoRow(
                providerName = "Email",
                linkedAddress = if (authSession.isEmailLinked) {
                    authSession.userEmail ?: "Linked"
                } else {
                    "Not linked"
                },
                isLinked = authSession.isEmailLinked,
                iconContent = {
                    EmailEnvelopeIcon(tint = SettingsVolt)
                },
                onActionClick = {
                    if (authSession.isEmailLinked) {
                        onToggleEmailLink("")
                    } else {
                        showEmailOtpLinkDialog = true
                    }
                }
            )
        }
    }

    if (showEmailOtpLinkDialog) {
        LinkEmailOtpDialog(
            onDismiss = { showEmailOtpLinkDialog = false },
            onVerifiedEmail = { verifiedEmail ->
                showEmailOtpLinkDialog = false
                onToggleEmailLink(verifiedEmail)
            }
        )
    }
}

@Composable
private fun EmailEnvelopeIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        val left = size.width * 0.1f
        val top = size.height * 0.2f
        val rectWidth = size.width * 0.8f
        val rectHeight = size.height * 0.6f

        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(rectWidth, rectHeight),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = strokeWidth)
        )

        val flapPath = Path().apply {
            moveTo(left + strokeWidth * 0.4f, top + strokeWidth * 0.5f)
            lineTo(size.width * 0.5f, size.height * 0.54f)
            lineTo(left + rectWidth - strokeWidth * 0.4f, top + strokeWidth * 0.5f)
        }
        drawPath(
            path = flapPath,
            color = tint,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun ProviderStatusBentoRow(
    providerName: String,
    linkedAddress: String,
    isLinked: Boolean,
    iconContent: @Composable () -> Unit,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SettingsInnerBg)
            .border(1.dp, SettingsBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SettingsCardBg)
                    .border(1.dp, SettingsBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                iconContent()
            }

            Column {
                Text(
                    text = providerName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SettingsWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = linkedAddress,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLinked) SettingsVolt else SettingsMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isLinked) {
            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SettingsDanger),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SettingsDanger)
            ) {
                Text(
                    text = "Log Out",
                    fontSize = 12.sp,
                    color = SettingsDanger,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SettingsVolt,
                    contentColor = OnVolt
                )
            ) {
                Text(
                    text = "Link",
                    fontSize = 12.sp,
                    color = OnVolt,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LinkEmailOtpDialog(
    onDismiss: () -> Unit,
    onVerifiedEmail: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val otpManager = remember { EmailOtpManager() }

    var emailInput by remember { mutableStateOf("") }
    var otpCodeInput by remember { mutableStateOf("") }
    var isOtpStep by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SettingsCardBg),
            border = BorderStroke(1.dp, SettingsBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (isOtpStep) "Verify Email" else "Link Email",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SettingsWhite
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (!isOtpStep) {
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(text = "Email address", color = SettingsMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SettingsWhite,
                            unfocusedTextColor = SettingsWhite,
                            focusedContainerColor = SettingsInnerBg,
                            unfocusedContainerColor = SettingsInnerBg,
                            focusedBorderColor = SettingsVolt,
                            unfocusedBorderColor = SettingsBorder,
                            cursorColor = SettingsVolt
                        )
                    )
                } else {
                    Text(
                        text = emailInput.trim(),
                        fontSize = 13.sp,
                        color = SettingsMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SixDigitOtpInputField(
                        code = otpCodeInput,
                        onCodeChange = {
                            otpCodeInput = it
                            errorMessage = null
                        }
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        fontSize = 12.sp,
                        color = SettingsDanger
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = SettingsMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (!isOtpStep) {
                                val (result, code) = otpManager.generateOtp(emailInput.trim())
                                if (result == OtpRequestResult.SENT && code != null) {
                                    isOtpStep = true
                                    errorMessage = null
                                    scope.launch {
                                        EmailOtpSender.sendOtpEmail(context, emailInput.trim(), code)
                                    }
                                } else {
                                    errorMessage = "Invalid email address"
                                }
                            } else {
                                when (otpManager.verifyOtp(emailInput.trim(), otpCodeInput)) {
                                    OtpVerifyResult.SUCCESS -> onVerifiedEmail(emailInput.trim())
                                    OtpVerifyResult.EXPIRED_CODE -> errorMessage = "Verification code expired"
                                    else -> errorMessage = "Invalid verification code"
                                }
                            }
                        },
                        enabled = if (!isOtpStep) emailInput.isNotBlank() else otpCodeInput.length == 6,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SettingsVolt,
                            contentColor = OnVolt,
                            disabledContainerColor = SettingsInnerBg,
                            disabledContentColor = SettingsMuted
                        )
                    ) {
                        Text(
                            text = if (!isOtpStep) "Send Code" else "Verify",
                            fontSize = 13.sp,
                            color = OnVolt,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataBackupBentoCard(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsCardBg),
        border = BorderStroke(1.dp, SettingsBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "DATA",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = SettingsMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onExportClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SettingsVolt,
                        contentColor = OnVolt
                    )
                ) {
                    Text(
                        text = "Export",
                        fontSize = 14.sp,
                        color = OnVolt,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, SettingsBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SettingsInnerBg,
                        contentColor = SettingsWhite
                    )
                ) {
                    Text(
                        text = "Import",
                        fontSize = 14.sp,
                        color = SettingsWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatMetricNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        "%.1f".format(Locale.US, value)
    }
}
