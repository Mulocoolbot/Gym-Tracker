package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable

interface JsonBackupController {
    fun exportJson(fileName: String, jsonContent: String)
    fun launchImport()
}

@Composable
expect fun rememberJsonBackupController(
    onImportJson: (String) -> Unit
): JsonBackupController
