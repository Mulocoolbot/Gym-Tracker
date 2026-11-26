package com.mulocoolbot.gymtracker.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

fun Modifier.dismissKeyboardOnTap(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    pointerInput(focusManager, keyboardController) {
        detectTapGestures {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
}

@Composable
fun DismissKeyboardOnScroll(isScrollInProgress: Boolean) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }
}
