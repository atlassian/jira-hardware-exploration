package com.atlassian.performance.tools.lib.jvmtasks

import com.atlassian.performance.tools.jvmtasks.api.TaskTimer
import org.apache.logging.log4j.CloseableThreadContext
import org.apache.logging.log4j.ThreadContext

class ContextTask<T>(
    private val label: String,
    private val task: () -> T
) : () -> T {

    private val logStack = ThreadContext.getImmutableStack()
    private val logContext = ThreadContext.getImmutableContext()

    override fun invoke(): T {
        return CloseableThreadContext.pushAll(logStack.asList())
            .putAll(logContext)
            .use { TaskTimer.time(label, task) }
    }
}
