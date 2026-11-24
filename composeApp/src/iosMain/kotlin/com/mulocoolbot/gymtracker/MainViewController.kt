package com.mulocoolbot.gymtracker

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController {
    GymTrackerApp()
}
