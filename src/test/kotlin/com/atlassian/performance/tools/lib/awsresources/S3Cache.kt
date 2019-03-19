package com.atlassian.performance.tools.lib.awsresources

import com.amazonaws.services.s3.model.S3ObjectSummary
import com.amazonaws.services.s3.transfer.KeyFilter
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.internal.MultipleFileDownloadImpl
import com.amazonaws.services.s3.transfer.internal.S3MultiDownload
import com.atlassian.performance.tools.io.api.ensureDirectory
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.io.FileListing
import com.atlassian.performance.tools.lib.toExistingFile
import net.jcip.annotations.NotThreadSafe
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant

@NotThreadSafe
class S3Cache(
    private val transfer: TransferManager,
    private val bucketName: String,
    private val cacheKey: String,
    private val localPath: Path
) {
    private val localDirectory = localPath.toFile().ensureDirectory()
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val etags = EtagCache(localPath.parent.resolve(".etags"))

    fun download() {
        val countingFilter = CountingKeyFilter(FreshKeyFilter(cacheKey, localPath, etags))
        val startedDownload = transfer.downloadDirectory(
            bucketName,
            cacheKey,
            localDirectory.parentFile,
            true,
            countingFilter
        )
        val included = countingFilter.countIncluded()
        val excluded = countingFilter.countExcluded()
        val progress = TransferLoggingProgress(logger, 10, included)
        logger.info("Downloading $included, skipping $excluded")
        startedDownload
            .apply { addProgressListener(progress) }
            .also { it.waitForCompletion() }
            .let { S3MultiDownload(it as MultipleFileDownloadImpl) }
            .listDownloads()
            .forEach { etags.write(it) }
    }

    fun upload() {
        val s3Objects = S3Listing(transfer.amazonS3Client).listObjects(bucketName, cacheKey)
        val allFiles = FileListing(localDirectory).listRecursively()
        val freshFiles = time("filter uploads") { allFiles.filter { shouldUpload(it, s3Objects) } }
        val staleFiles = allFiles.size - freshFiles.size
        val progress = TransferLoggingProgress(logger, 10, freshFiles.size)
        logger.info("Uploading ${freshFiles.size}, skipping $staleFiles")
        transfer
            .uploadFileList(
                bucketName,
                cacheKey,
                localDirectory,
                freshFiles
            )
            .apply { addProgressListener(progress) }
            .waitForCompletion() // TODO extract uploaded ETags somehow and then cache them
    }

    private fun shouldUpload(
        local: File,
        s3Objects: List<S3ObjectSummary>
    ): Boolean {
        val localKey = cacheKey + local.relativeTo(localDirectory).path // TODO might not work on Windows
        val s3Object = s3Objects.find { it.key == localKey } ?: return true
        val localEtag = etags.read(localKey)
        if (s3Object.eTag == localEtag) {
            return false
        }
        val s3Freshness = s3Object.lastModified.toInstant()
        val localFreshness = Instant.ofEpochMilli(local.lastModified())
        return localFreshness > s3Freshness
    }

    override fun toString(): String {
        return "S3Cache(bucketName='$bucketName', cacheKey='$cacheKey', localPath=$localPath)"
    }
}

private class FreshKeyFilter(
    private val cacheKey: String,
    private val localPath: Path,
    private val etags: EtagCache
) : KeyFilter {

    override fun shouldInclude(
        objectSummary: S3ObjectSummary
    ): Boolean {
        val local = findLocal(objectSummary)
        val cachedEtag = etags.read(objectSummary.key)
        return when {
            local == null -> true
            isProtected(local) -> false
            cachedEtag == null -> true
            objectSummary.eTag == cachedEtag -> false
            else -> {
                val s3Freshness = objectSummary.lastModified.toInstant()
                val localFreshness = Instant.ofEpochMilli(local.lastModified())
                s3Freshness > localFreshness
            }
        }
    }

    private fun findLocal(
        objectSummary: S3ObjectSummary
    ): File? = objectSummary
        .key
        .removePrefix(cacheKey)
        .let { localPath.resolve(it) }
        .toExistingFile()

    private fun isProtected(
        file: File
    ): Boolean = file
        .toPath()
        .let { Files.getFileAttributeView(it, PosixFileAttributeView::class.java) }
        ?.readAttributes()
        ?.permissions()
        ?.contains(PosixFilePermission.OTHERS_READ)?.not()
        ?: false
}
