package com.mulocoolbot.gymtracker.platform

import androidx.compose.ui.Modifier

expect fun Modifier.iosEdgeSwipeBack(
    enabled: Boolean = true,
    onBack: () -> Unit
): Modifier
