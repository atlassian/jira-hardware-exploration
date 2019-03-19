package com.amazonaws.services.s3.transfer.internal

import com.amazonaws.services.s3.transfer.Download

class S3MultiDownload(
    download: MultipleFileDownloadImpl
) : MultipleFileDownloadImpl(
    download.description,
    download.progress,
    download.listenerChain,
    download.keyPrefix,
    download.bucketName,
    download.subTransfers
) {
    fun listDownloads(): Collection<Download> = subTransfers
}
