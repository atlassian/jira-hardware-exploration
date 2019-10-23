package com.atlassian.performance.tools.lib.io

import java.io.File

class FileListing(
    private val file: File
) {

    fun listRecursively(): List<File> = file
        .listFiles()
        ?.flatMap { FileListing(it).listRecursively() }
        ?: listOf(file)
}
