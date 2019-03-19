package com.atlassian.performance.tools.lib.awsresources

import com.amazonaws.services.s3.model.S3ObjectSummary
import com.amazonaws.services.s3.transfer.KeyFilter
import net.jcip.annotations.ThreadSafe
import java.util.concurrent.atomic.AtomicInteger

@ThreadSafe
class CountingKeyFilter(
    private val filter: KeyFilter
) : KeyFilter {

    private val includedCount = AtomicInteger(0)
    private val excludedCount = AtomicInteger(0)

    override fun shouldInclude(
        objectSummary: S3ObjectSummary
    ): Boolean {
        val included = filter.shouldInclude(objectSummary)
        if (included) {
            includedCount.incrementAndGet()
        } else {
            excludedCount.incrementAndGet()
        }
        return included
    }

    fun countIncluded(): Int = includedCount.get()
    fun countExcluded(): Int = excludedCount.get()
}