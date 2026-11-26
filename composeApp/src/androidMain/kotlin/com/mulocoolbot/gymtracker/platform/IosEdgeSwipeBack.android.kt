package com.mulocoolbot.gymtracker.platform

import androidx.compose.ui.Modifier

actual fun Modifier.iosEdgeSwipeBack(
    enabled: Boolean,
    onBack: () -> Unit
): Modifier = this
