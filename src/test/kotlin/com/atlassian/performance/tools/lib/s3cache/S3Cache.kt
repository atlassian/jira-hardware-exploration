package com.atlassian.performance.tools.lib.s3cache

import com.amazonaws.event.ProgressListener
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.amazonaws.services.s3.transfer.Download
import com.amazonaws.services.s3.transfer.TransferManager
import com.atlassian.performance.tools.io.api.ensureDirectory
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.awsresources.S3Listing
import com.atlassian.performance.tools.lib.io.FileListing
import com.atlassian.performance.tools.lib.toExistingFile
import net.jcip.annotations.NotThreadSafe
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant


@NotThreadSafe
class S3Cache(
    private val transfer: TransferManager,
    private val bucketName: String,
    cacheKey: String,
    private val localPath: Path,
    etags: Path,
    private val searchPattern: String = ""
) {
    private val s3Prefix = "$cacheKey/"
    private val localDirectory = localPath.toFile().ensureDirectory()
    private val logger: Logger = LogManager.getLogger(this::class.java)
    private val etagsCache = EtagCache(etags)
    private val uploadLock = Object()

    fun download() {
        val s3Objects = time("filter") {
            val all = S3Listing(transfer.amazonS3Client).listObjects(bucketName, s3Prefix)
            val filtered = all.filter { shouldDownload(it) }
            return@time Filtered(filtered, all.size - filtered.size)
        }
        time("transfer") {
            val progress = TransferLoggingProgress(logger, 50, s3Objects.kept.size)
            logger.info("Downloading ${s3Objects.kept.size}, skipping ${s3Objects.skipped}")
            s3Objects
                .kept
                .parallelStream()
                .map { startDownload(it, progress) }
                .forEach { localDownload ->
                    localDownload.download.waitForCompletion()
                    localDownload.local.setLastModified(localDownload.download.objectMetadata.lastModified.time)
                    etagsCache.write(localDownload.download)
                }
        }
    }

    private fun shouldDownload(
        objectSummary: S3ObjectSummary
    ): Boolean {
        val local = findLocal(objectSummary).toExistingFile()
        val cachedEtag = etagsCache.read(objectSummary.key)
        val match = matchesSearchPattern(objectSummary)
        return when {
            !match -> false
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

    private fun matchesSearchPattern(objectSummary: S3ObjectSummary): Boolean {
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$searchPattern")
        val path = Paths.get(objectSummary.key)
        return matcher.matches(path)
    }

    private fun findLocal(
        objectSummary: S3ObjectSummary
    ): Path = objectSummary
        .key
        .removePrefix(s3Prefix)
        .let { localPath.resolve(it) }

    private fun isProtected(
        file: File
    ): Boolean = file
        .toPath()
        .let { Files.getFileAttributeView(it, PosixFileAttributeView::class.java) }
        ?.readAttributes()
        ?.permissions()
        ?.contains(PosixFilePermission.OTHERS_READ)?.not()
        ?: false

    private fun startDownload(
        s3Object: S3ObjectSummary,
        progress: ProgressListener
    ): LocalDownload {
        val request = GetObjectRequest(s3Object.bucketName, s3Object.key)
        val target = findLocal(s3Object).toFile()
        val download = transfer.download(request, target)
        download.addProgressListener(progress)
        return LocalDownload(
            target,
            download
        )
    }

    fun upload() = upload(localDirectory)

    fun upload(
        directory: File
    ) = synchronized(uploadLock) {
        val files = time("filter") {
            val s3Objects = S3Listing(transfer.amazonS3Client)
                .listObjects(bucketName, s3Prefix)
                .associateBy { it.key }
            val all = FileListing(directory).listRecursively()
            val filtered = all.filter { shouldUpload(it, s3Objects) }
            Filtered(filtered, all.size - filtered.size)
        }
        time("transfer") {
            val progress = TransferLoggingProgress(logger, 50, files.kept.size)
            logger.info("Uploading ${files.kept.size}, skipping ${files.skipped}")
            transfer
                .uploadFileList(
                    bucketName,
                    s3Prefix,
                    localDirectory,
                    files.kept
                )
                .apply { addProgressListener(progress) }
                .also { it.waitForCompletion() }
                .subTransfers
                .map { it.waitForUploadResult() }
                .forEach { etagsCache.write(it) }
        }
    }

    private fun shouldUpload(
        local: File,
        s3Objects: Map<String, S3ObjectSummary>
    ): Boolean {
        val localKey = s3Prefix + local.relativeTo(localDirectory).path // TODO might not work on Windows
        val s3Object = s3Objects[localKey] ?: return true
        val localFreshness = local.lastModified()
        val s3Freshness = s3Object.lastModified.time
        return localFreshness > s3Freshness
    }

    override fun toString(): String {
        return "S3Cache(bucketName='$bucketName', s3Prefix='$s3Prefix', localPath=$localPath)"
    }

    private class Filtered<T>(
        val kept: List<T>,
        val skipped: Int
    )

    private class LocalDownload(
        val local: File,
        val download: Download
    )
}
