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
    private val explorationCache: HardwareExplorationResultCache
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
        val jiraReport = reportJiraRecommendation(jiraRecommendations)
        try {
            val dbExploration = exploreDbHardware(jiraRecommendations.allRecommendations, jiraExploration)
            val dbRecommendations = recommend(dbExploration)
            val dbRecommendationChart = chartDbRecommendation(dbRecommendations)
            val dbRecommendationTable = tabularize(dbRecommendations)
            return ReportedRecommendations(
                description = scale.description,
                recommendations = dbRecommendations,
                reports = listOfNotNull(jiraReport, dbRecommendationChart, dbRecommendationTable)
                    + jiraExploration.reports
                    + dbExploration.reports
            )
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

    private fun chartDbRecommendation(
        recommendations: RecommendationSet
    ) = HardwareExplorationChart(
        JiraClusterGrouping(InstanceType.values().toList()),
        DbInstanceTypeXAxis(),
        GitRepo.findFromCurrentDirectory()
    ).plotRecommendation(
        recommendations = RecommendationSet(
            exploration = ReportedExploration(
                results = recommendations.exploration.results.sortedWith(
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
                reports = recommendations.exploration.reports
            ),
            bestApdexAndReliability = recommendations.bestApdexAndReliability,
            bestCostEffectiveness = recommendations.bestCostEffectiveness
        ),
        application = scale.description,
        output = workspace.isolateReport("db-recommendation-chart.html")
    )

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
            presenceJudge = VirtualUsersPresenceJudge(Ratio(0.90))
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

    private fun tabularize(
        recommendations: RecommendationSet
    ): File {
        return RecommendationsTable().tabulate(
            recommendations = recommendations,
            output = workspace.isolateReport("db-recommendation-table.csv")
        )
    }
}

class RecommendationSet(
    val exploration: ReportedExploration,
    val bestApdexAndReliability: HardwareTestResult,
    val bestCostEffectiveness: HardwareTestResult
) {
    val allRecommendations = listOf(bestApdexAndReliability, bestCostEffectiveness)
}

class ReportedRecommendations(
    val description: String,
    val recommendations: RecommendationSet,
    val reports: List<File>
)
