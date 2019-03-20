package com.atlassian.performance.tools.lib.s3cache

import com.amazonaws.event.ProgressEvent
import com.amazonaws.event.ProgressEventType
import com.amazonaws.event.ProgressListener
import net.jcip.annotations.ThreadSafe
import org.apache.logging.log4j.Logger
import java.util.concurrent.atomic.AtomicInteger

@ThreadSafe
class TransferLoggingProgress(
    private val logger: Logger,
    private val granularity: Int,
    private val expectedTransfers: Int
) : ProgressListener {

    private val transferCount = AtomicInteger(0)

    override fun progressChanged(
        event: ProgressEvent
    ) {
        if (event.eventType == ProgressEventType.TRANSFER_COMPLETED_EVENT) {
            val count = transferCount.incrementAndGet()
            if (count % granularity == 0) {
                logger.debug("$count/$expectedTransfers transferred")
            }
        }
    }
}
