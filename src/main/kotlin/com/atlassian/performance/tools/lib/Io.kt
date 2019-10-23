package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.io.api.ensureParentDirectory
import java.io.BufferedWriter
import java.io.File
import java.nio.file.Path

fun Path.toExistingFile(): File? {
    val file = toFile()
    return if (file.exists()) {
        file
    } else {
        null
    }
}

fun Path.write(
    writing: (BufferedWriter) -> Unit
): Unit = toFile()
    .ensureParentDirectory()
    .bufferedWriter()
    .use(writing)