import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.2.70")
    }
}

plugins {
    kotlin("jvm").version("1.2.70")
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(URI("https://packages.atlassian.com/maven-external/"))
}

tasks.getByName("test", Test::class).apply {
    filter {
        exclude("**/*IT.class")
    }
}

tasks.withType(KotlinCompile::class).forEach {
    it.kotlinOptions {
        jvmTarget = "1.8"
    }
}

task<Test>("recommendHardware").apply {
    outputs.upToDateWhen { false }
    description = "Recommends hardware setups for Jira based on performance tests"
    include("**/HardwareRecommendationIT.class")
    val shadowJarTask = tasks.getByPath(":virtual-users:shadowJar")
    dependsOn(shadowJarTask)
    systemProperty("jpt.virtual-users.shadow-jar", shadowJarTask.outputs.files.files.first())
    System.getProperty("hwr.jsw.version")?.let { systemProperty("hwr.jsw.version", it) }
    failFast = true
    maxHeapSize = "8g"
    testLogging {
        showStandardStreams = true
    }
}

task<Test>("testIntegration").apply {
    outputs.upToDateWhen { false }
    description = "Runs IT tests against the framework"
    include("**/HardwareRecommendationEngineIT.class")

    val shadowJarTask = tasks.getByPath(":virtual-users:shadowJar")
    dependsOn(shadowJarTask)
    systemProperty("jpt.virtual-users.shadow-jar", shadowJarTask.outputs.files.files.first())
    failFast = true
    maxHeapSize = "700m"
    testLogging {
        showStandardStreams = true
    }
}

task<Test>("cleanUpAfterBamboo").apply {
    outputs.upToDateWhen { false }
    include("**/BambooCleanupIT.class")
}

task<Test>("testManually").apply {
    outputs.upToDateWhen{false}
    description = "Provision a Jira Instance only"
    include("**/JiraInstanceTest.class")
    failFast = true
    testLogging {
        if (System.getenv("bamboo_buildResultKey") != null) {
            showStandardStreams = true
        }
    }
}

dependencies {
    testCompile(project(":virtual-users"))
    testCompile(fileTree(mapOf("dir" to "lib", "include" to "*.jar")))
    testCompile("com.atlassian.performance.tools:jira-performance-tests:[3.3.0,4.0.0)")
    testCompile("com.atlassian.performance.tools:infrastructure:[4.14.0,5.0.0)")
    testCompile("com.atlassian.performance.tools:virtual-users:[3.6.2,4.0.0)")
    testCompile("com.atlassian.performance.tools:jira-software-actions:[1.1.0,2.0.0]")
    testCompile("com.atlassian.performance.tools:aws-infrastructure:[2.13.1,3.0.0)")
    testCompile("com.atlassian.performance.tools:aws-resources:[1.3.4,2.0.0)")
    testCompile("com.atlassian.performance.tools:concurrency:[1.0.0,2.0.0)")
    testCompile("org.apache.commons:commons-csv:1.4")
    testCompile("org.eclipse.jgit:org.eclipse.jgit:4.11.0.201803080745-r")
    testCompile("junit:junit:4.12")
    testCompile("org.hamcrest:hamcrest-library:1.3")
    testCompile("org.assertj:assertj-core:3.11.1")
    testCompile("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.2.70")

    listOf(
        "api",
        "core",
        "slf4j-impl"
    ).map {
        "org.apache.logging.log4j:log4j-$it:2.12.0"
    }.forEach {
        testCompile(it)
    }
}

configurations.all {
    resolutionStrategy {
        failOnVersionConflict()
        activateDependencyLocking()
        resolutionStrategy {
            eachDependency {
                when (requested.module.toString()) {
                    "org.apache.commons:commons-csv" -> useVersion("1.4")
                    "com.google.guava:guava" -> useVersion("23.6-jre")
                    "org.apache.httpcomponents:httpclient" -> useVersion("4.5.5")
                    "org.slf4j:slf4j-api" -> useVersion("1.8.0-alpha2")
                    "org.apache.httpcomponents:httpcore" -> useVersion("4.4.9")
                    "commons-logging:commons-logging" -> useVersion("1.2")
                    "org.codehaus.plexus:plexus-utils" -> useVersion("3.1.0")
                    "com.fasterxml.jackson.core:jackson-core" -> useVersion("2.9.4")
                    "com.google.code.gson:gson" -> useVersion("2.8.2")
                    "org.jsoup:jsoup" -> useVersion("1.10.2")
                    "com.jcraft:jzlib" -> useVersion("1.1.3")
                }
                when (requested.group) {
                    "org.jetbrains.kotlin" -> useVersion("1.2.70")
                    "org.apache.logging.log4j" -> useVersion("2.12.0")
                }
            }
        }
    }
}

tasks.wrapper {
    version = "5.1.1"
    distributionType = Wrapper.DistributionType.BIN
}
