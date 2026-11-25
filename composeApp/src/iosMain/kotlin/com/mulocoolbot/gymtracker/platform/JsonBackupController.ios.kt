package com.mulocoolbot.gymtracker.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

@Composable
actual fun rememberJsonBackupController(
    onImportJson: (String) -> Unit
): JsonBackupController {
    return remember(onImportJson) {
        IosJsonBackupController(onImportJson)
    }
}

private class IosJsonBackupController(
    private val onImportJson: (String) -> Unit
) : JsonBackupController {

    private var activePickerDelegate: DocumentPickerDelegate? = null

    @OptIn(ExperimentalForeignApi::class)
    override fun exportJson(fileName: String, jsonContent: String) {
        if (jsonContent.isEmpty()) return
        val tempDir = NSTemporaryDirectory()
        val separator = if (tempDir.endsWith("/")) "" else "/"
        val filePath = "$tempDir$separator$fileName"
        val nsString = NSString.create(string = jsonContent)
        val written = nsString.writeToFile(
            path = filePath,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
        if (!written) return

        val fileUrl = NSURL.fileURLWithPath(filePath)
        val presenter = findTopViewController() ?: return
        val activityController = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null
        )
        activityController.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(activityController, animated = true, completion = null)
    }

    override fun launchImport() {
        val presenter = findTopViewController() ?: return
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON))
        val delegate = DocumentPickerDelegate(
            onPicked = { content ->
                activePickerDelegate = null
                if (!content.isNullOrBlank()) {
                    onImportJson(content)
                }
            },
            onCancelled = {
                activePickerDelegate = null
            }
        )
        activePickerDelegate = delegate
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private class DocumentPickerDelegate(
        private val onPicked: (String?) -> Unit,
        private val onCancelled: () -> Unit
    ) : NSObject(), UIDocumentPickerDelegateProtocol {

        @OptIn(ExperimentalForeignApi::class)
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>
        ) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
            if (url == null) {
                onCancelled()
                return
            }
            val scoped = url.startAccessingSecurityScopedResource()
            val content = try {
                NSString.stringWithContentsOfURL(
                    url = url,
                    encoding = NSUTF8StringEncoding,
                    error = null
                )
            } finally {
                if (scoped) {
                    url.stopAccessingSecurityScopedResource()
                }
            }
            onPicked(content)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onCancelled()
        }
    }
}
