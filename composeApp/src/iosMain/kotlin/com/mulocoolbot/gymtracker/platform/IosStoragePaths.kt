@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mulocoolbot.gymtracker.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal fun iosDocumentDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null
    )
    return requireNotNull(url?.path) { "Unable to resolve iOS NSDocumentDirectory" }
}
