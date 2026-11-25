package com.mulocoolbot.gymtracker.platform

import android.content.Context
import androidx.compose.runtime.Composable

object AndroidPlatformBindings {
    var startRestTimerService: (Context, Long, String) -> Unit = { _, _, _ -> }
    var adjustRestTimerService: (Context, Int) -> Unit = { _, _ -> }
    var stopRestTimerService: (Context) -> Unit = { _ -> }
    var deliverEmailOtp: suspend (Context, String, String) -> Unit = { _, _, _ -> }
    var remoteAuthSync: (String, String, Boolean) -> Unit = { _, _, _ -> }
    var googleSignInActionFactory: @Composable ((String, String?) -> Unit, () -> Unit) -> () -> Unit =
        { _, onErrorOrCancel -> { onErrorOrCancel() } }
}
