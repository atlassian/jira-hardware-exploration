package com.atlassian.performance.tools.hardware.guidance

import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.ec2.model.InstanceType.*
import com.atlassian.performance.tools.hardware.*
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import java.time.Duration

class GuideCatalogue {

    private val resultCache = HardwareExplorationResultCache(
        IntegrationTestRuntime.workspace.directory.resolve("processed-cache.json")
    )

    fun jiraLarge(): ExplorationGuidance = jira(db = M42xlarge)

    fun jiraExtraLarge(): ExplorationGuidance = jira(db = M44xlarge)

    private fun jira(
        db: InstanceType
    ): ExplorationGuidance = JiraExplorationGuidance(
        instanceTypes = listOf(
            C52xlarge,
            C54xlarge,
            C48xlarge,
            C59xlarge,
            C518xlarge
        ),
        maxNodeCount = 16,
        minNodeCountForAvailability = 3,
        minApdexGain = 0.01,
        minThroughputGain = TemporalRate(5.0, Duration.ofSeconds(1)),
        db = db,
        resultsCache = resultCache
    )

    fun dbLarge(
        jiraRecommendations: List<HardwareTestResult>,
        jiraExploration: List<HardwareExplorationResult>
    ): ExplorationGuidance = db(
        listOf(
            M4Large,
            M4Large,
            M42xlarge,
            M44xlarge
        ),
        jiraRecommendations,
        jiraExploration
    )

    fun dbExtraLarge(
        jiraRecommendations: List<HardwareTestResult>,
        jiraExploration: List<HardwareExplorationResult>
    ): ExplorationGuidance = db(
        listOf(
            M42xlarge,
            M44xlarge,
            M410xlarge,
            M416xlarge
        ),
        jiraRecommendations,
        jiraExploration
    )

    private fun db(
        dbs: List<InstanceType>,
        jiraRecommendations: List<HardwareTestResult>,
        jiraExploration: List<HardwareExplorationResult>
    ): ExplorationGuidance = DbExplorationGuidance(
        dbs = listOf(
            M42xlarge,
            M44xlarge,
            M410xlarge,
            M416xlarge
        ),
        jiraRecommendations = jiraRecommendations,
        jiraExploration = jiraExploration,
        jiraOrder = listOf(
            C52xlarge,
            C54xlarge,
            C48xlarge,
            C59xlarge,
            C518xlarge
        ),
        resultsCache = resultCache
    )

    fun focus(
        hardware: Hardware
    ): ExplorationGuidance = SingleHardwareGuidance(
        hardware
    )

    fun skip(): ExplorationGuidance = SkippedGuidance()
}
