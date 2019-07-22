package com.atlassian.performance.tools.lib.report

import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.report.api.result.EdibleResult

class VirtualUsersPresenceJudge(
    private val expectedPresence: Ratio
) {

    fun judge(
        result: EdibleResult,
        expectedVus: Int
    ) {
        val encounteredVus = result
            .actionMetrics
            .asSequence()
            .map { it.virtualUser }
            .distinct()
            .toList()
            .size
        val actualPresence = Ratio(dividend = encounteredVus, divisor = expectedVus)
        if (actualPresence < expectedPresence) {
            throw Exception(
                "Only ${actualPresence.percent} % VUs were encountered in ${result.cohort}," +
                    " but we expected at least ${expectedPresence.percent} %." +
                    " Look for VU creation problems."
            )
        }
    }
}
