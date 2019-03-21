package com.atlassian.performance.tools.lib.s3cache

import com.amazonaws.services.s3.model.S3ObjectSummary
import com.amazonaws.services.s3.transfer.KeyFilter
import com.amazonaws.services.s3.transfer.TransferManager
import com.amazonaws.services.s3.transfer.internal.MultipleFileDownloadImpl
import com.amazonaws.services.s3.transfer.internal.S3MultiDownload
import com.atlassian.performance.tools.io.api.ensureDirectory
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.awsresources.CountingKeyFilter
import com.atlassian.performance.tools.lib.awsresources.S3Listing
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
    cacheKey: String,
    private val localPath: Path
) {
    private val s3Prefix = "$cacheKey/"
    private val localDirectory = localPath.toFile().ensureDirectory()
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val etags = EtagCache(localPath.parent.resolve(".etags"))

    fun download() {
        val countingFilter = CountingKeyFilter(FreshKeyFilter(s3Prefix, localPath, etags))
        val downloads = time("filter") {
            transfer.downloadDirectory( // This can be quite slow due to https://github.com/aws/aws-sdk-java/issues/1215
                bucketName,
                s3Prefix,
                localDirectory.parentFile,
                true,
                countingFilter
            )
        }
        val included = countingFilter.countIncluded()
        val excluded = countingFilter.countExcluded()
        val progress = TransferLoggingProgress(logger, 50, included)
        logger.info("Downloading $included, skipping $excluded")
        time("transfer") {
            downloads
                .apply { addProgressListener(progress) }
                .also { it.waitForCompletion() }
                .let { S3MultiDownload(it as MultipleFileDownloadImpl) }
                .listDownloads()
                .forEach { etags.write(it) }
        }
    }

    fun upload() {
        val allFiles = FileListing(localDirectory).listRecursively()
        val freshFiles = time("filter") {
            val s3Objects = S3Listing(transfer.amazonS3Client).listObjects(bucketName, s3Prefix)
            allFiles.filter { shouldUpload(it, s3Objects) }
        }
        val staleFiles = allFiles.size - freshFiles.size
        val progress = TransferLoggingProgress(logger, 50, freshFiles.size)
        logger.info("Uploading ${freshFiles.size}, skipping $staleFiles")
        time("transfer") {
            transfer
                .uploadFileList(
                    bucketName,
                    s3Prefix,
                    localDirectory,
                    freshFiles
                )
                .apply { addProgressListener(progress) }
                .also { it.waitForCompletion() }
                .subTransfers
                .forEach { etags.write(it) }
        }
    }

    private fun shouldUpload(
        local: File,
        s3Objects: List<S3ObjectSummary>
    ): Boolean {
        val localKey = s3Prefix + local.relativeTo(localDirectory).path // TODO might not work on Windows
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
        return "S3Cache(bucketName='$bucketName', s3Prefix='$s3Prefix', localPath=$localPath)"
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
