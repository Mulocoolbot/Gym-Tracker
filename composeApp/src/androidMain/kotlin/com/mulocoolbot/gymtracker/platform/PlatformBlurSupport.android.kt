package com.mulocoolbot.gymtracker.platform

import android.os.Build

actual fun supportsTutorialBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
