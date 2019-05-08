package com.atlassian.performance.tools.hardware

import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.hardware.IntegrationTestRuntime.aws
import com.atlassian.performance.tools.lib.Apdex
import com.atlassian.performance.tools.lib.readResult
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.report.api.FullReport
import com.atlassian.performance.tools.report.api.StandardTimeline
import com.atlassian.performance.tools.report.api.result.EdibleResult
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.atlassian.performance.tools.workspace.api.RootWorkspace
import com.atlassian.performance.tools.workspace.api.TestWorkspace
import org.junit.Test
import java.nio.file.Paths
import java.time.Duration

class BottleneckAnalysisIT {

    private val task = RootWorkspace(Paths.get("build")).isolateTask("bottleneck-analysis")

    @Test
    fun shouldFindTheBottleneck() {
        val result = loadResult()
        val offenders = Apdex().findOffenders(result)
        FullReport().dump(
            listOf(offenders),
            task.isolateTest("offender-report")
        )
    }

    private fun loadResult(): EdibleResult {
        val cache = S3Cache(
            transfer = TransferManagerBuilder.standard()
                .withS3Client(aws.s3)
                .build(),
            bucketName = "quicksilver-jhwr-cache-ireland",
            cacheKey = "QUICK-110-3rd-go/c4.8xlarge/nodes/3/dbs/m4.xlarge/runs/1",
            localPath = task.directory
        )
        cache.download()
        val test = TestWorkspace(task.directory)
        val cohortResult = test.readResult("3 x c4.8xlarge Jira, m4.xlarge DB, run 1")
        val load = VirtualUserLoad.Builder()
            .ramp(Duration.ofSeconds(90))
            .flat(Duration.ofMinutes(20))
            .build()
        return cohortResult.prepareForJudgement(StandardTimeline(load.total))
    }
}
