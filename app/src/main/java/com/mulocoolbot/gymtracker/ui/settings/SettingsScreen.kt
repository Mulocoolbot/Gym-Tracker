package com.mulocoolbot.gymtracker.ui.settings

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mulocoolbot.gymtracker.data.auth.AuthSession
import com.mulocoolbot.gymtracker.data.local.UserProfileEntity
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
import java.util.Locale

@Composable
fun SettingsScreen(
    authSession: AuthSession,
    userProfile: UserProfileEntity?,
    onUpdateProfile: (name: String, weightKg: Double, heightCm: Double) -> Unit,
    onToggleGoogleLink: (defaultEmail: String) -> Unit,
    onToggleEmailLink: (emailInput: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(20.dp))

        AboutMeCard(
            uid = userProfile?.uid ?: authSession.uid,
            userProfile = userProfile,
            onUpdateProfile = onUpdateProfile
        )

        Spacer(modifier = Modifier.height(20.dp))

        AccountInfoCard(
            authSession = authSession,
            onToggleGoogleLink = onToggleGoogleLink,
            onToggleEmailLink = onToggleEmailLink
        )
    }
}

@Composable
private fun AboutMeCard(
    uid: String,
    userProfile: UserProfileEntity?,
    onUpdateProfile: (name: String, weightKg: Double, heightCm: Double) -> Unit
) {
    var nameInput by remember(userProfile?.name) {
        mutableStateOf(userProfile?.name.orEmpty())
    }
    var weightInput by remember(userProfile?.weightKg) {
        mutableStateOf(userProfile?.weightKg?.let { formatMetricNumber(it) }.orEmpty())
    }
    var heightInput by remember(userProfile?.heightCm) {
        mutableStateOf(userProfile?.heightCm?.let { formatMetricNumber(it) }.orEmpty())
    }

    val ageValue = remember(userProfile?.birthDateMillis) {
        WorkoutCalculator.calculateAge(userProfile?.birthDateMillis ?: 0L).toString()
    }

    fun commitIfValid(newName: String, newWeight: String, newHeight: String) {
        val cleanName = newName.trim()
        val parsedWeight = newWeight.trim().toDoubleOrNull() ?: return
        val parsedHeight = newHeight.trim().toDoubleOrNull() ?: return
        if (cleanName.isNotEmpty() && parsedWeight > 0.0 && parsedHeight > 0.0) {
            onUpdateProfile(cleanName, parsedWeight, parsedHeight)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "About Me",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            StaticSettingRow(
                label = "User ID",
                value = uid.ifBlank { "-" },
                isMonospace = true
            )

            RowDivider()

            EditableSettingRow(
                label = "Name",
                value = nameInput,
                keyboardType = KeyboardType.Text,
                onValueChange = { updated ->
                    nameInput = updated
                    commitIfValid(updated, weightInput, heightInput)
                }
            )

            RowDivider()

            StaticSettingRow(
                label = "Age",
                value = ageValue,
                isMonospace = true
            )

            RowDivider()

            EditableSettingRow(
                label = "Weight (kg)",
                value = weightInput,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { updated ->
                    weightInput = updated
                    commitIfValid(nameInput, updated, heightInput)
                }
            )

            RowDivider()

            EditableSettingRow(
                label = "Height (cm)",
                value = heightInput,
                keyboardType = KeyboardType.Decimal,
                onValueChange = { updated ->
                    heightInput = updated
                    commitIfValid(nameInput, weightInput, updated)
                }
            )
        }
    }
}

@Composable
private fun StaticSettingRow(
    label: String,
    value: String,
    isMonospace: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = if (isMonospace) {
                TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun EditableSettingRow(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Box(
            modifier = Modifier
                .width(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(InputSurface)
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(VoltAccent),
                textStyle = TextStyle(
                    fontFamily = if (keyboardType == KeyboardType.Decimal) {
                        FontFamily.Monospace
                    } else {
                        FontFamily.SansSerif
                    },
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.End
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = CardBorder, thickness = 1.dp)
}

@Composable
private fun AccountInfoCard(
    authSession: AuthSession,
    onToggleGoogleLink: (defaultEmail: String) -> Unit,
    onToggleEmailLink: (emailInput: String) -> Unit
) {
    val fallbackEmail = authSession.userEmail
        ?: authSession.googleEmail
        ?: "athlete@gmail.com"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Account Info",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            ProviderStatusBox(
                providerName = "Google",
                linkedAddress = if (authSession.isGoogleLinked) {
                    authSession.googleEmail ?: fallbackEmail
                } else {
                    "Not linked"
                },
                isLinked = authSession.isGoogleLinked,
                onActionClick = { onToggleGoogleLink(fallbackEmail) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            ProviderStatusBox(
                providerName = "Email",
                linkedAddress = if (authSession.isEmailLinked) {
                    authSession.userEmail ?: fallbackEmail
                } else {
                    "Not linked"
                },
                isLinked = authSession.isEmailLinked,
                onActionClick = { onToggleEmailLink(fallbackEmail) }
            )
        }
    }
}

@Composable
private fun ProviderStatusBox(
    providerName: String,
    linkedAddress: String,
    isLinked: Boolean,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InputSurface)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = providerName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = linkedAddress,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }

        if (isLinked) {
            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DangerRed),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
            ) {
                Text(
                    text = "Log Out",
                    style = MaterialTheme.typography.labelMedium,
                    color = DangerRed,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onActionClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VoltAccent,
                    contentColor = OnVolt
                )
            ) {
                Text(
                    text = "Link",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnVolt,
                    fontWeight = FontWeight.Bold
                )
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

