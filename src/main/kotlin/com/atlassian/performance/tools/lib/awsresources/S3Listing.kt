package com.atlassian.performance.tools.lib.awsresources

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.ListObjectsV2Request
import com.amazonaws.services.s3.model.S3ObjectSummary
import net.jcip.annotations.ThreadSafe

@ThreadSafe
class S3Listing(
    private val s3: AmazonS3
) {

    fun listObjects(
        bucketName: String,
        prefix: String
    ): List<S3ObjectSummary> {
        val summaries = mutableListOf<S3ObjectSummary>()
        var token: String? = null
        do {
            val listing = s3.listObjectsV2(
                ListObjectsV2Request()
                    .withBucketName(bucketName)
                    .withPrefix(prefix)
                    .withContinuationToken(token)
            )
            summaries += listing.objectSummaries
            token = listing.nextContinuationToken
        } while (token != null)
        return summaries
    }
}
