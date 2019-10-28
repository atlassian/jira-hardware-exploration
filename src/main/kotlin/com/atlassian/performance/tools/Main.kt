package com.atlassian.performance.tools

import com.amazonaws.regions.Regions
import com.amazonaws.services.ec2.model.InstanceType
import com.amazonaws.services.s3.transfer.TransferManagerBuilder
import com.atlassian.performance.tools.aws.api.StorageLocation
import com.atlassian.performance.tools.awsinfrastructure.api.DatasetCatalogue
import com.atlassian.performance.tools.hardware.*
import com.atlassian.performance.tools.hardware.aws.HardwareRuntime
import com.atlassian.performance.tools.hardware.failure.BugAwareTolerance
import com.atlassian.performance.tools.hardware.guidance.ExplorationGuidance
import com.atlassian.performance.tools.hardware.guidance.JiraExplorationGuidance
import com.atlassian.performance.tools.infrastructure.api.distribution.ProductDistribution
import com.atlassian.performance.tools.lib.LogConfigurationFactory
import com.atlassian.performance.tools.lib.OverallError
import com.atlassian.performance.tools.lib.Ratio
import com.atlassian.performance.tools.lib.infrastructure.AdminDataset
import com.atlassian.performance.tools.lib.report.VirtualUsersPresenceJudge
import com.atlassian.performance.tools.lib.s3cache.S3Cache
import com.atlassian.performance.tools.ssh.api.SshConnection
import com.atlassian.performance.tools.virtualusers.api.TemporalRate
import com.atlassian.performance.tools.virtualusers.api.VirtualUserLoad
import com.atlassian.performance.tools.workspace.api.TaskWorkspace
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.ConfigurationFactory
import java.nio.file.Path
import java.io.File
import java.net.URI
import java.time.Duration
import java.util.concurrent.Future


fun main(args: Array<String>) = mainBody {
    ArgParser(args).parseInto(::Args).run {
        println("ACTION, ${action}!")

        println("S3 Cache Key, ${cacheKey}!")
        println("Product Name ${productName}!")
        println("Product Version ${productVersion}!")

        //val cacheKey = "HardwareRecommendationEngineIT/" + GitRepo.findFromCurrentDirectory().getHead()
        val workspace = cacheKey?.let { HardwareRuntime.rootWorkspace.isolateTask(it) }

        ConfigurationFactory.setConfigurationFactory(workspace?.let { LogConfigurationFactory(it) })

        val tempCacheKey = "$cacheKey" /* "/${scale.cacheKey}",*/

        val localPathRoot = HardwareRuntime.rootWorkspace.directory

        if ("download" == action) {
            val cmd = CommandLine()
            cacheKey?.let {
                cmd.download(it,
                    localPathRoot,
                    "quicksilver-jhwr-cache-ireland")
            }
        }

        if ("analyze" == action) {
            val cmd = CommandLine()
            cacheKey?.let { ck ->
                cmd.analyze(ck, localPathRoot)
            }
        }
    }
}

class Args(parser: ArgParser) {
    val verbose by parser.flagging(
        "-v", "--verbose",
        help = "enable verbose mode")

    val cacheKey by parser
        .storing("s3 cachekey containing results to report on")
        .default<String?>(null)

    val productName by parser
        .storing("atlassian product to report on")
        .default("jira")

    val productVersion by parser
        .storing("atlassian product version to report on")
        .default("7.13.0")

    val action by parser
        .positional("ACTION", help = "action")
}

class CommandLine {

    private val logger: Logger = LogManager.getLogger(this::class.java)

    fun analyze(s3CacheKey: String,
                localPathRoot: Path) {

        val localPath = localPathRoot.resolve(s3CacheKey)


        val vuPresenceJudge = VirtualUsersPresenceJudge(Ratio(0.70))

        val requirements = OutcomeRequirements(
            overallErrorThreshold = OverallError(Ratio(0.20)),
            maxActionErrorThreshold = Ratio(0.50),
            apdexThreshold = 0.30,
            reliabilityRange = 0
        )

        val pastFailures = BugAwareTolerance(logger)

        val explorations = localPath
            .toFile()
            .listFiles()
            .filter { it.isDirectory }
            .filter { it.name.startsWith("l") } // HACK remove
            .sortedBy { it.name }
            .map {
                val cacheKey = it.name
                val parts = cacheKey.split('-')
                val productSize = parts[0]
                val product = parts[1]
                val productVersion = parts[2]
                val task = TaskWorkspace(it.toPath())

                println("Processing ${cacheKey}...")
                var resultsSoFar = mutableListOf<HardwareExplorationResult>()
                val productInstanceTypes = mutableListOf<InstanceType>()
                val dbInstanceTypes = mutableListOf<InstanceType>()
                var maxNodeCount = 0;

                val completedResults = it.listFiles()
                    .filter { it.isDirectory }
                    .sortedBy { it.name }
                    .map {

                        val productNodeInstanceType = InstanceType.fromValue(it.name)
                        productInstanceTypes.add(productNodeInstanceType)
                        val localMaxNodePath = it.toPath().resolve("nodes").toFile().listFiles().sortedByDescending { it.name }.first()

                        val localMaxNodeCount = localMaxNodePath.name.toInt()
                        if (maxNodeCount > localMaxNodeCount) {
                            maxNodeCount = localMaxNodeCount;
                        }

                        val scale = getApplicationScale(product, productSize, productVersion, cacheKey, maxNodeCount)

                        val metric = HardwareMetric(
                            scale = scale,
                            presenceJudge = vuPresenceJudge
                        )

                        println("Processing ${it.name}...")



                        var results = it.toPath().resolve("nodes").toFile().listFiles()
                            .map {

                                val nodeCount = it.name.toInt()
                                val dbInstanceType = it.resolve("dbs").listFiles().filter { it.isDirectory }.map {
                                    InstanceType.fromValue(it.name)
                                }.first()
                                dbInstanceTypes.add(dbInstanceType)

                                val hardware = Hardware(productNodeInstanceType, nodeCount, dbInstanceType)
                                val decision = HardwareExplorationDecision(
                                    hardware,
                                    worthExploring = true, // because we did already?
                                    reason = "just looking...."
                                )

                                println("Processing ${hardware}...")

                                val existingResults = HardwareExistingResults(task, pastFailures, metric)
                                val reusableResults = existingResults.reuseResults(hardware)
                                val coalesced = existingResults.coalesce(reusableResults, hardware)

                                HardwareExplorationResult(decision, coalesced)
                            }

                        results

                    }.flatten()

                println("Reporting ${it.name}...")
                val guidance = JiraExplorationGuidance(productInstanceTypes,
                    maxNodeCount,
                    2,
                    0.01, // HACK we don't know
                    TemporalRate(2.0, Duration.ofSeconds(1)), // HACK we don't know
                    dbInstanceTypes.first())

                val outputTask = TaskWorkspace(localPathRoot.resolve("$s3CacheKey-blah-output").resolve(cacheKey))
                outputTask.directory.toFile().mkdirs()

                resultsSoFar.addAll(completedResults);
                val report = guidance.report(resultsSoFar,
                    requirements, outputTask, "blah blah title", HardwareExplorationResultCache(localPathRoot.resolve("blah-cache")))

                ReportedExploration(resultsSoFar, report)


            }.toList()
/*


 val productInstanceTypes = results.mapNotNull { it.testResult?.hardware?.jira }
                        val dbInstanceTypes = results.mapNotNull { it.testResult?.hardware?.db }
                        val maxNodeCount = results.mapNotNull { it.testResult?.hardware?.nodeCount }.max()!!



                        val outputTask = TaskWorkspace(localPathRoot.resolve("$s3CacheKey-blah-output").resolve(cacheKey))
                        outputTask.directory.toFile().mkdirs()

 val report = guidance.report(results,
                            requirements,outputTask, "blah blah title", HardwareExplorationResultCache(localPathRoot.resolve("blah-cache")))


        val exploration = ReportedExploration(results, report)
        val recommendations = recommend(exploration, requirements)

        //val dbExploration = exploreDbHardware(recommendations.allRecommendations, exploration)
        val dbRecommendations = recommend(/*dbExploration +*/ exploration, requirements)

 */


/*
        val report = report(results)
        val exploration =  ReportedExploration(results, report)
        val recommendations = recommend(exploration)

        val outputPath = localPathRoot.resolve("blah")
        return HardwareReportEngine().reportedRecommendations("blah blah blah",
            outputPath,
            recommendations,
            dbRecommendations,
            exploration,
            dbExploration)
            */

/*
        HardwareExplorationResult(
            decision = decision,
            testResult = getRobustResult(hardware, awsExecutor)
        )
        HardwareReportEngine().reportedRecommendations(scale.description,
            localPathRoot,
            jiraRecommendations,
            dbRecommendations,
            jiraExploration,
            dbExploration)

 */
    }

    private fun getApplicationScale(product: String, size: String, version: String, cacheKey: String, productNodeCount: Int): ApplicationScale {
        if (product.equals("jsw", true)) {
            if (size.equals("l", true)) {
                return ApplicationScales().large(jiraVersion = version)
            }

            if (size.equals("xl", true)) {
                return ApplicationScales().extraLarge(jiraVersion = version, postgres = false) // HACK
            }
        }

        val dataset = DatasetCatalogue().custom(
            location = StorageLocation(
                uri = URI("http://example.com"),
                region = Regions.EU_CENTRAL_1
            )
        ).let { dataset ->
            AdminDataset(
                dataset = dataset,
                adminLogin = "admin",
                adminPassword = "admin"
            )
        }
        val virtualUserLoad = VirtualUserLoad.Builder().build() // HACK
        return ApplicationScale("", cacheKey, dataset, virtualUserLoad, productNodeCount);
    }
/*
    private fun report(
        results: List<HardwareExplorationResult>
    ): List<File> {
        return guidance.report(
            results,
            requirements,
            task,
            scale.description,
            explorationCache
        )
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
*/

    private fun recommend(
        exploration: ReportedExploration,
        requirements: OutcomeRequirements
    ): RecommendationSet {
        val candidates = exploration
            .results
            .mapNotNull { it.testResult }
            .filter { requirements.areSatisfiedBy(it) }
        val bestApdexAndReliability = pickTheBestApdex(pickReliable(candidates, requirements))
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
        candidates: List<HardwareTestResult>,
        requirements: OutcomeRequirements
    ): List<HardwareTestResult> {
        return candidates
            .filter {
                requirements.areReliable(it, candidates)
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

    fun download(cacheKey: String,
                 localPathRoot: Path,
                 s3bucketName: String) {
        //download cache contents

        val aws = HardwareRuntime.prepareAws()

        val localPath = localPathRoot.resolve(cacheKey)
        val etagsPath = HardwareRuntime.rootWorkspace.directory.resolve(".etags")

        var s3Cache = S3Cache(
            transfer = TransferManagerBuilder.standard()
                .withS3Client(aws.s3)
                .build(),
            bucketName = s3bucketName,
            cacheKey = cacheKey,
            localPath = localPath,
            etags = etagsPath
        )

        s3Cache.download()
    }
}

class GenericSoftwareDistribution(val product: String, val productVersion: String) : ProductDistribution {
    override fun install(ssh: SshConnection, destination: String): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class GenericGuidance() : ExplorationGuidance {
    override fun space(): List<Hardware> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun decideTesting(
        hardware: Hardware,
        benchmark: (Hardware) -> Future<HardwareExplorationResult>
    ): HardwareExplorationDecision {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun report(
        exploration: List<HardwareExplorationResult>,
        requirements: OutcomeRequirements,
        task: TaskWorkspace,
        title: String,
        resultsCache: HardwareExplorationResultCache
    ): List<File> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
