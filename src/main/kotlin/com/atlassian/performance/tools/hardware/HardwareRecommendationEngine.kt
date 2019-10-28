package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.hardware.failure.BugAwareTolerance
import com.atlassian.performance.tools.hardware.guidance.DbExplorationGuidance
import com.atlassian.performance.tools.hardware.guidance.ExplorationGuidance
import com.atlassian.performance.tools.hardware.report.*
import com.atlassian.performance.tools.hardware.tuning.JiraNodeTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.report.VirtualUsersPresenceJudge
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import org.apache.logging.log4j.LogManager
import java.io.File
import java.time.Duration

class HardwareRecommendationEngine(
    private val product: ProductDistribution,
    private val scale: ApplicationScale,
    private val tuning: JiraNodeTuning,
    private val jiraExploration: ExplorationGuidance,
    private val dbInstanceTypes: List<InstanceType>,
    private val requirements: OutcomeRequirements,
    private val repeats: Int,
    private val aws: Aws,
    private val workspace: TaskWorkspace,
    private val s3Cache: S3Cache,
    private val explorationCache: HardwareExplorationResultCache,
    private val vuPresenceJudge: VirtualUsersPresenceJudge = VirtualUsersPresenceJudge(Ratio(0.90))
) {

    private val logger = LogManager.getLogger(this::class.java)

    fun recommend(): ReportedRecommendations {
        logger.info("Using $s3Cache")
        time("download") { s3Cache.download() }
        val jiraExploration = try {
            explore(jiraExploration)
        } finally {
            time("upload") { s3Cache.upload() }
        }
        val jiraRecommendations = recommend(jiraExploration)
        try {
            val dbExploration = exploreDbHardware(jiraRecommendations.allRecommendations, jiraExploration)
            val dbRecommendations = recommend(dbExploration + jiraExploration)

            return HardwareReportEngine().reportedRecommendations(scale.description,
                workspace.directory,
                jiraRecommendations,
                dbRecommendations,
                jiraExploration,
                dbExploration)
        } finally {
            TaskTimer.time("upload") { s3Cache.upload() }
        }
    }




    private fun recommend(
        exploration: ReportedExploration
    ): RecommendationSet {
        val candidates = exploration
            .results
            .mapNotNull { it.testResult }
            .filter { requirements.areSatisfiedBy(it) }
        val bestApdexAndReliability = pickTheBestApdex(pickReliable(candidates))
        logger.info("Recommending best Apdex and reliability achieved by $bestApdexAndReliability")
        val bestCostEffectiveness = pickTheMostCostEffective(candidates)
        logger.info("Recommending best cost-effectiveness achieved by $bestCostEffectiveness")
        return RecommendationSet(
            exploration,
            bestApdexAndReliability,
            bestCostEffectiveness
        )
    }

    private fun pickReliable(
        candidates: List<HardwareTestResult>
    ): List<HardwareTestResult> {
        return candidates
            .filter { candidate ->
                val hardwareAfterIncident = candidate.hardware.copy(
                    nodeCount = candidate.hardware.nodeCount - 1
                )
                return@filter candidates
                    .map { it.hardware }
                    .contains(hardwareAfterIncident)
            }
    }

    private fun pickTheBestApdex(
        candidates: List<HardwareTestResult>
    ): HardwareTestResult = candidates
        .maxBy { it.apdex }
        ?: throw Exception("We don't have an Apdex recommendation")

    private fun pickTheMostCostEffective(
        candidates: List<HardwareTestResult>
    ): HardwareTestResult = candidates
        .maxBy { it.apdexPerUsdUpkeep }
        ?: throw Exception("We don't have a cost-effectiveness recommendation")

    private fun explore(
        guidance: ExplorationGuidance
    ): ReportedExploration = HardwareExploration(
        product = product,
        scale = scale,
        guidance = guidance,
        requirements = requirements,
        investment = Investment(
            useCase = "Test hardware recommendations - ${workspace.directory.fileName}",
            lifespan = Duration.ofHours(2)
        ),
        tuning = tuning,
        aws = aws,
        task = workspace,
        repeats = repeats,
        pastFailures = BugAwareTolerance(logger),
        metric = HardwareMetric(
            scale = scale,
            presenceJudge = vuPresenceJudge
        ),
        s3Cache = s3Cache,
        explorationCache = explorationCache
    ).exploreHardware()

    private fun exploreDbHardware(
        jiraRecommendations: List<HardwareTestResult>,
        jiraExploration: ReportedExploration
    ): ReportedExploration = explore(
        DbExplorationGuidance(
            dbs = dbInstanceTypes,
            jiraRecommendations = jiraRecommendations,
            jiraExploration = jiraExploration.results
        )
    )
}

class RecommendationSet(
    val exploration: ReportedExploration,
    val bestApdexAndReliability: HardwareTestResult,
    val bestCostEffectiveness: HardwareTestResult
) {
    val allRecommendations = listOf(bestApdexAndReliability, bestCostEffectiveness).distinctBy { it.hardware }
}

class ReportedRecommendations(
    val description: String,
    val recommendations: RecommendationSet,
    val reports: List<File>
)
