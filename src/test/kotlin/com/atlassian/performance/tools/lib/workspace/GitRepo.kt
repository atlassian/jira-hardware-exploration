package com.atlassian.performance.tools.lib.workspace

import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.Repository
import java.io.File

object GitRepo2 {

    fun findInAncestors(
        descendant: File
    ): Repository? {
        if (descendant.isDirectory) {
            descendant
                .listFiles()
                .singleOrNull { it.name == ".git" }
                ?.let { return FileRepository(it) }
        }
        val parent = descendant.parentFile
        return when (parent) {
            null -> null
            else -> findInAncestors(parent)
        }
    }
}
