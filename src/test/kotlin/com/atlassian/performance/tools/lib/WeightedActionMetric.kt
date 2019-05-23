package com.atlassian.performance.tools.lib

import com.atlassian.performance.tools.jiraactions.api.ActionMetric

/**
 * Wrapper for ActionMetric which can store the apdex 'weight' applied alongside this metric
 */
class WeightedActionMetric(val actionMetric: ActionMetric, val weight: Float)
