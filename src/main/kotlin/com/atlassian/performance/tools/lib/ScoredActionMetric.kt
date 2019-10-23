package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric

/**
 * Wrapper for ActionMetric which can store the relevant apdex score alongside this metric
 */
class ScoredActionMetric(val actionMetric: ActionMetric, val score: Float)
