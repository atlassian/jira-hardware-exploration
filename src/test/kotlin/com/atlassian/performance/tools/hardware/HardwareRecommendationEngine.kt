package com.atlassian.performance.tools.hardware

import com.amazonaws.services.ec2.model.InstanceType
import com.atlassian.performance.tools.aws.api.Aws
import com.atlassian.performance.tools.aws.api.Investment
import com.atlassian.performance.tools.hardware.failure.BugAwareTolerance
import com.atlassian.performance.tools.hardware.guidance.DbExplorationGuidance
import com.atlassian.performance.tools.hardware.guidance.ExplorationGuidance
import com.atlassian.performance.tools.hardware.report.DbInstanceTypeXAxis
import com.atlassian.performance.tools.hardware.report.HardwareExplorationChart
import com.atlassian.performance.tools.hardware.report.JiraInstanceTypeGrouping
import com.atlassian.performance.tools.hardware.report.NodeCountXAxis
import com.atlassian.performance.tools.hardware.tuning.JiraNodeTuning
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.jvmtasks.api.TaskTimer.time
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.atlassian.performance.tools.workspace.api.git.GitRepo
import org.apache.logging.log4j.LogManager
import java.time.Duration

class HardwareRecommendationEngine(
    private val product: ProductDistribution,
    private val scale: ApplicationScale,
    private val tuning: JiraNodeTuning,
    private val jiraExploration: ExplorationGuidance,
    private val dbInstanceTypes: List<InstanceType>,
    private val minApdex: Double,
    private val maxErrorRate: Double,
    private val repeats: Int,
    private val aws: Aws,
    private val workspace: TaskWorkspace,
    private val s3Cache: S3Cache,
    private val explorationCache: HardwareExplorationResultCache
) {

    private val logger = LogManager.getLogger(this::class.java)

    fun recommend(): RecommendationSet {
        logger.info("Using $s3Cache")
        time("download") { s3Cache.download() }
        val jiraExploration = try {
            explore(jiraExploration)
        } finally {
            time("upload") { s3Cache.upload() }
        }
        val jiraRecommendations = recommend(jiraExploration)
        reportJiraRecommendation(jiraRecommendations)
        try {
            val dbExploration = exploreDbHardware(jiraRecommendations.allRecommendations, jiraExploration)
            val dbRecommendations = recommend(dbExploration)
            reportDbRecommendation(dbRecommendations)
            return dbRecommendations
        } finally {
            time("upload") { s3Cache.upload() }
        }
    }

    private fun reportJiraRecommendation(
        recommendations: RecommendationSet
    ) = HardwareExplorationChart(
        JiraInstanceTypeGrouping(compareBy { InstanceType.values().toList().indexOf(it) }),
        NodeCountXAxis(),
        GitRepo.findFromCurrentDirectory()
    ).plotRecommendation(
        recommendations = recommendations,
        application = scale.description,
        output = workspace.isolateReport("jira-recommendation-chart.html")
    )

    private fun reportDbRecommendation(
        recommendations: RecommendationSet
    ) = HardwareExplorationChart(
        JiraInstanceTypeGrouping(compareBy { InstanceType.values().toList().indexOf(it) }),
        DbInstanceTypeXAxis(),
        GitRepo.findFromCurrentDirectory()
    ).plotRecommendation(
        recommendations = RecommendationSet(
            exploration = recommendations.exploration.sortedWith(
                compareBy<HardwareExplorationResult> {
                    InstanceType.values().toList().indexOf(it.decision.hardware.jira)
                }.thenComparing(
                    compareBy<HardwareExplorationResult> {
                        it.decision.hardware.nodeCount
                    }
                ).thenComparing(
                    compareBy<HardwareExplorationResult> {
                        InstanceType.values().toList().indexOf(it.decision.hardware.db)
                    }
                )
            ),
            bestApdex = recommendations.bestApdex,
            bestCostEffectiveness = recommendations.bestCostEffectiveness
        ),
        application = scale.description,
        output = workspace.isolateReport("db-recommendation-chart.html")
    )

    private fun recommend(
        exploration: List<HardwareExplorationResult>
    ): RecommendationSet {
        val candidates = exploration
            .mapNotNull { it.testResult }
            .filter { it.apdex > minApdex }
            .filter { it.errorRate < maxErrorRate }
        val bestApdex = pickTheBestApdex(candidates)
        logger.info("Recommending best Apdex achieved by $bestApdex")
        val bestCostEffectiveness = pickTheMostCostEffective(candidates)
        logger.info("Recommending best cost-effectiveness achieved by $bestCostEffectiveness")
        return RecommendationSet(
            exploration,
            bestApdex,
            bestCostEffectiveness
        )
    }

    private fun pickTheBestApdex(
        candidates: List<HardwareTestResult>
    ): HardwareTestResult = candidates
        .sortedByDescending { it.apdex }
        .firstOrNull()
        ?: throw Exception("We don't have an Apdex recommendation")

    private fun pickTheMostCostEffective(
        candidates: List<HardwareTestResult>
    ): HardwareTestResult = candidates
        .sortedByDescending { it.apdexPerUsdUpkeep }
        .firstOrNull()
        ?: throw Exception("We don't have a cost-effectiveness recommendation")

    private fun explore(
        guidance: ExplorationGuidance
    ): List<HardwareExplorationResult> = HardwareExploration(
        product = product,
        scale = scale,
        guidance = guidance,
        apdexSpreadWarningThreshold = 0.10,
        errorRateWarningThreshold = 0.05,
        pastFailures = BugAwareTolerance(logger),
        repeats = repeats,
        investment = Investment(
            useCase = "Test hardware recommendations - ${workspace.directory.fileName}",
            lifespan = Duration.ofHours(2)
        ),
        tuning = tuning,
        s3Cache = s3Cache,
        explorationCache = explorationCache,
        aws = aws,
        task = workspace
    ).exploreHardware()

    private fun exploreDbHardware(
        jiraRecommendations: List<HardwareTestResult>,
        jiraExploration: List<HardwareExplorationResult>
    ): List<HardwareExplorationResult> = explore(
        DbExplorationGuidance(
            dbs = dbInstanceTypes,
            jiraRecommendations = jiraRecommendations,
            jiraExploration = jiraExploration
        )
    )
}

class RecommendationSet(
    val exploration: List<HardwareExplorationResult>,
    val bestApdex: HardwareTestResult,
    val bestCostEffectiveness: HardwareTestResult
) {
    val allRecommendations = listOf(bestApdex, bestCostEffectiveness)
}
