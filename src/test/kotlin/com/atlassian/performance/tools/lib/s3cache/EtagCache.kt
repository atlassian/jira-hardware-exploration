package com.atlassian.performance.tools.lib.s3cache

import com.amazonaws.services.s3.transfer.Download
import com.amazonaws.services.s3.transfer.Upload
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
    ): Path = cache.resolve(s3ObjectKey)

    fun write(
        download: Download
    ) {
        download.waitForCompletion()
        locate(download.key)
            .toFile()
            .ensureParentDirectory()
            .writeText(download.objectMetadata.eTag)
    }

    fun write(
        upload: Upload
    ) {
        val uploadResult = upload.waitForUploadResult()
        locate(uploadResult.key)
            .toFile()
            .ensureParentDirectory()
            .writeText(uploadResult.eTag)

    }
}
