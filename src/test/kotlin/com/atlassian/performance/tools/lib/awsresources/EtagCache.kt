package com.atlassian.performance.tools.lib.awsresources

import com.amazonaws.services.s3.transfer.Download
import com.atlassian.performance.tools.io.api.ensureParentDirectory
import com.atlassian.performance.tools.lib.toExistingFile
import net.jcip.annotations.NotThreadSafe
import java.nio.file.Path

@NotThreadSafe
class EtagCache(
    val cache: Path
) {
    fun read(
        s3ObjectKey: String
    ): String? = locate(s3ObjectKey)
        .toExistingFile()
        ?.readText()

    private fun locate(
        s3ObjectKey: String
    ): Path = cache.resolve(s3ObjectKey) // TODO might not work on Windows

    fun write(
        download: Download
    ) {
        locate(download.key)
            .toFile()
            .ensureParentDirectory()
            .writeText(download.objectMetadata.eTag)
    }
}
