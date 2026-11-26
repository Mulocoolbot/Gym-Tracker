package com.mulocoolbot.gymtracker.ui.settings

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mulocoolbot.gymtracker.data.auth.AuthSession
import com.mulocoolbot.gymtracker.data.auth.EmailOtpManager
import com.mulocoolbot.gymtracker.data.auth.OtpRequestResult
import com.mulocoolbot.gymtracker.data.auth.OtpVerifyResult
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
import com.mulocoolbot.gymtracker.platform.createAuthBridge
import com.mulocoolbot.gymtracker.platform.rememberGoogleSignInAction
import com.mulocoolbot.gymtracker.platform.rememberJsonBackupController
import com.mulocoolbot.gymtracker.ui.auth.SixDigitOtpInputField
import com.mulocoolbot.gymtracker.ui.components.DismissKeyboardOnScroll
import com.mulocoolbot.gymtracker.ui.components.GoogleLogoIcon
import com.mulocoolbot.gymtracker.ui.components.dismissKeyboardOnTap
import com.mulocoolbot.gymtracker.ui.theme.AppBackground
import com.mulocoolbot.gymtracker.ui.theme.CardBorder
import com.mulocoolbot.gymtracker.ui.theme.CardSurface
import com.mulocoolbot.gymtracker.ui.theme.DangerRed
import com.mulocoolbot.gymtracker.ui.theme.InputSurface
import com.mulocoolbot.gymtracker.ui.theme.OnVolt
import com.mulocoolbot.gymtracker.ui.theme.TextMuted
import com.mulocoolbot.gymtracker.ui.theme.TextPrimary
import com.mulocoolbot.gymtracker.ui.theme.VoltAccent
import com.mulocoolbot.gymtracker.util.WorkoutCalculator
import com.mulocoolbot.gymtracker.util.formatCompactNumber
import com.mulocoolbot.gymtracker.util.parseDecimalInput
import kotlin.math.round
import kotlinx.coroutines.launch

internal data class BackupStatusFeedback(
    val message: String,
    val isError: Boolean
)

internal fun resolveBackupImportFeedback(restored: Boolean): BackupStatusFeedback {
    return if (restored) {
        BackupStatusFeedback(
            message = "Backup restored successfully (5 tables verified).",
            isError = false
        )
    } else {
        BackupStatusFeedback(
            message = "Import rejected: invalid or corrupted 5-entity JSON. Existing data was kept safe.",
            isError = true
        )
    }
}

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
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DismissKeyboardOnScroll(scrollState.isScrollInProgress)

    var backupFeedback by remember { mutableStateOf<BackupStatusFeedback?>(null) }

    val backupController = rememberJsonBackupController { jsonContent ->
        coroutineScope.launch {
            val restored = onImportBackup(jsonContent)
            backupFeedback = resolveBackupImportFeedback(restored)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .imePadding()
            .dismissKeyboardOnTap()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
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
            feedback = backupFeedback,
            onExportClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                coroutineScope.launch {
                    val json = onExportBackup()
                    if (json.isNotEmpty()) {
                        backupController.exportJson("mfit_backup.json", json)
                        backupFeedback = BackupStatusFeedback("Backup JSON ready to export.", isError = false)
                    } else {
                        backupFeedback = BackupStatusFeedback("Nothing to export yet.", isError = true)
                    }
                }
            },
            onImportClick = {
                focusManager.clearFocus()
                keyboardController?.hide()
                backupFeedback = null
                backupController.launchImport()
            }
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var nameInput by remember(userProfile?.name) {
        mutableStateOf(userProfile?.name.orEmpty())
    }
    var weightInput by remember(userProfile?.weightKg) {
        mutableStateOf(userProfile?.weightKg?.let { formatCompactNumber(it) } ?: "70.0")
    }
    var heightInput by remember(userProfile?.heightCm) {
        mutableStateOf(userProfile?.heightCm?.let { formatCompactNumber(it) } ?: "175")
    }

    val ageValue = remember(userProfile?.birthDateMillis) {
        WorkoutCalculator.calculateAge(userProfile?.birthDateMillis ?: 0L)
    }

    fun commitIfValid(newName: String, newWeight: String, newHeight: String) {
        val cleanName = newName.trim().ifEmpty { userProfile?.name?.trim().orEmpty() }
        val parsedWeight = parseDecimalInput(newWeight) ?: return
        val parsedHeight = parseDecimalInput(newHeight) ?: return
        if (cleanName.isNotEmpty() && parsedWeight > 0.0 && parsedHeight > 0.0) {
            onUpdateProfile(cleanName, parsedWeight, parsedHeight)
        }
    }

    fun stepWeight(delta: Double) {
        focusManager.clearFocus()
        keyboardController?.hide()
        val current = parseDecimalInput(weightInput) ?: userProfile?.weightKg ?: 70.0
        val updated = (round((current + delta) * 10.0) / 10.0).coerceAtLeast(1.0)
        val formatted = formatCompactNumber(updated)
        weightInput = formatted
        commitIfValid(nameInput, formatted, heightInput)
    }

    fun stepHeight(delta: Int) {
        focusManager.clearFocus()
        keyboardController?.hide()
        val current = parseDecimalInput(heightInput) ?: userProfile?.heightCm ?: 175.0
        val updated = (current + delta).coerceAtLeast(50.0)
        val formatted = formatCompactNumber(updated)
        heightInput = formatted
        commitIfValid(nameInput, weightInput, formatted)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
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
                    color = TextMuted
                )

                val displayUid = uid.ifBlank { "-" }
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(InputSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(50))
                        .clickable {
                            if (uid.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(uid))
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayUid,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = VoltAccent,
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
                        .background(InputSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "NAME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    BasicTextField(
                        value = nameInput,
                        onValueChange = { updated ->
                            nameInput = updated
                            commitIfValid(updated, weightInput, heightInput)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        cursorBrush = SolidColor(VoltAccent),
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(InputSurface)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "AGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${ageValue} yrs",
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
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
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InputSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = TextMuted
            )
            if (isFocused) {
                Text(
                    text = "Done",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoltAccent,
                    modifier = Modifier.clickable {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onDecrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "-",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                cursorBrush = SolidColor(VoltAccent),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
            )

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onIncrement),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = VoltAccent
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
    val authBridge = remember { createAuthBridge() }
    var showEmailOtpLinkDialog by remember { mutableStateOf(false) }
    var accountStatusMessage by remember { mutableStateOf<String?>(null) }

    val launchGoogleLink = rememberGoogleSignInAction(
        onSuccess = { selectedEmail, _ ->
            accountStatusMessage = null
            onToggleGoogleLink(selectedEmail)
        },
        onErrorOrCancel = {
            if (!authBridge.isCloudAuthConfigured) {
                accountStatusMessage =
                    "Offline-first mode: GoogleService-Info.plist is not configured. Your data stays safely stored on device."
            }
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACCOUNT INFO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = TextMuted
                )

                if (!authBridge.isCloudAuthConfigured || authSession.isOfflineMode) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(InputSurface)
                            .border(1.dp, CardBorder, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OFFLINE-FIRST",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = VoltAccent
                        )
                    }
                }
            }

            if (!authBridge.isCloudAuthConfigured) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Local-only storage active. Account linking is optional; workouts and JSON backups work offline without Firebase.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            ProviderStatusBentoRow(
                providerName = "Google",
                linkedAddress = when {
                    authSession.isGoogleLinked -> authSession.googleEmail ?: "Linked"
                    !authBridge.isCloudAuthConfigured -> "Offline-first (Not configured)"
                    else -> "Not linked"
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
                        accountStatusMessage = null
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
                    EmailEnvelopeIcon(tint = VoltAccent)
                },
                onActionClick = {
                    if (authSession.isEmailLinked) {
                        accountStatusMessage = null
                        onToggleEmailLink("")
                    } else {
                        showEmailOtpLinkDialog = true
                    }
                }
            )

            if (!accountStatusMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = accountStatusMessage!!,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = TextMuted
                )
            }
        }
    }

    if (showEmailOtpLinkDialog) {
        LinkEmailOtpDialog(
            onDismiss = { showEmailOtpLinkDialog = false },
            onVerifiedEmail = { verifiedEmail ->
                showEmailOtpLinkDialog = false
                accountStatusMessage = null
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
            .background(InputSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
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
                    .background(CardSurface)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                iconContent()
            }

            Column {
                Text(
                    text = providerName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = linkedAddress,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLinked) VoltAccent else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isLinked) {
            OutlinedButton(
                onClick = onActionClick,
                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DangerRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
            ) {
                Text(
                    text = "Log Out",
                    fontSize = 12.sp,
                    color = DangerRed,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onActionClick,
                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltAccent,
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
    val authBridge = remember { createAuthBridge() }
    val scope = rememberCoroutineScope()
    val otpManager = remember { EmailOtpManager() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var emailInput by remember { mutableStateOf("") }
    var otpCodeInput by remember { mutableStateOf("") }
    var isOtpStep by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .dismissKeyboardOnTap(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (isOtpStep) "Verify Email" else "Link Email",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
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
                        placeholder = { Text(text = "Email address", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = InputSurface,
                            unfocusedContainerColor = InputSurface,
                            focusedBorderColor = VoltAccent,
                            unfocusedBorderColor = CardBorder,
                            cursorColor = VoltAccent
                        )
                    )
                } else {
                    Text(
                        text = emailInput.trim(),
                        fontSize = 13.sp,
                        color = TextMuted
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
                        color = DangerRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    ) {
                        Text(text = "Cancel", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            if (!isOtpStep) {
                                val (result, code) = otpManager.generateOtp(emailInput.trim())
                                if (result == OtpRequestResult.SENT && code != null) {
                                    isOtpStep = true
                                    errorMessage = null
                                    scope.launch {
                                        authBridge.deliverOtp(emailInput.trim(), code)
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
                        modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VoltAccent,
                            contentColor = OnVolt,
                            disabledContainerColor = InputSurface,
                            disabledContentColor = TextMuted
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
    feedback: BackupStatusFeedback?,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "DATA",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                color = TextMuted
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
                        containerColor = VoltAccent,
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
                    border = BorderStroke(1.dp, CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = InputSurface,
                        contentColor = TextPrimary
                    )
                ) {
                    Text(
                        text = "Import",
                        fontSize = 14.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (feedback != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = feedback.message,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (feedback.isError) DangerRed else VoltAccent
                )
            }
        }
    }
}
