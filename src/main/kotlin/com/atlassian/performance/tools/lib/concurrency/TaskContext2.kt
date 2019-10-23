package com.atlassian.performance.tools.lib.concurrency

import com.atlassian.performance.tools.jvmtasks.api.TaskTimer
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.ThreadContext
import java.util.concurrent.CompletionService
import java.util.concurrent.Future

fun <T> CompletionService<T>.submitWithLogContext(
    label: String,
    task: () -> T
): Future<T> {
    val logStack = ThreadContext.getImmutableStack()
    val logContext = ThreadContext.getImmutableContext()
    return this.submit {
        CloseableThreadContext.pushAll(logStack.asList())
            .putAll(logContext)
            .use { TaskTimer.time(label, task) }
    }
}
