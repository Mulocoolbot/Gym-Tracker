package com.mulocoolbot.gymtracker.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberJsonBackupController(
    onImportJson: (String) -> Unit
): JsonBackupController {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        pendingExportJson = null
        if (uri != null && !json.isNullOrEmpty()) {
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = context.contentResolver.openOutputStream(uri, "wt")
                            ?: context.contentResolver.openOutputStream(uri)
                        stream?.use { output ->
                            output.write(json.toByteArray(Charsets.UTF_8))
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
                onImportJson(jsonContent.orEmpty())
            }
        }
    }

    return remember(exportLauncher, importLauncher) {
        object : JsonBackupController {
            override fun exportJson(fileName: String, jsonContent: String) {
                if (jsonContent.isEmpty()) return
                pendingExportJson = jsonContent
                exportLauncher.launch(fileName)
            }

            override fun launchImport() {
                importLauncher.launch(arrayOf("application/json"))
            }
        }
    }
}
