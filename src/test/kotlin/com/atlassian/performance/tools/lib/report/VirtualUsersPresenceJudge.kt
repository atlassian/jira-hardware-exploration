package com.atlassian.performance.tools.lib.report

import com.atlassian.performance.tools.report.api.result.EdibleResult

class VirtualUsersPresenceJudge {

    fun judge(
        result: EdibleResult,
        expectedVus: Int,
        expectedPresenceRatio: Double
    ) {
        val encounteredVus = result
            .actionMetrics
            .asSequence()
            .map { it.virtualUser }
            .distinct()
            .toList()
            .size
        val actualPresenceRatio = encounteredVus.toDouble().div(expectedVus)
        if (actualPresenceRatio < expectedPresenceRatio) {
            throw Exception(
                "Only ${actualPresenceRatio.toPercentage()} VUs were encountered in ${result.cohort}," +
                    " but we expected at least ${expectedPresenceRatio.toPercentage()}." +
                    " Look for VU creation problems."
            )
        }
    }

    private fun Double.toPercentage() = "${this * 100} %"
}
