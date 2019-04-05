import java.net.URI
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val kotlinVersion = "1.2.70"

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow").version("2.0.4")
}

dependencies {
    compile(fileTree(mapOf("dir" to "lib", "include" to "*.jar")))
    compile("com.atlassian.performance.tools:jira-actions:[3.0.0,4.0.0)")
    compile("com.atlassian.performance.tools:jira-software-actions:[1.0.0,2.0.0)")
    compile("org.apache.logging.log4j:log4j-api:2.10.0")
    compile("org.seleniumhq.selenium:selenium-support:3.11.0")
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    runtime("com.atlassian.performance.tools:io:[1.2.0,2.0.0)") // workaround for JPERF-390
    runtime("com.atlassian.performance.tools:virtual-users:[3.6.2,4.0.0)")
}

tasks.getByName("shadowJar", ShadowJar::class).apply {
    manifest.attributes["Main-Class"] = "com.atlassian.performance.tools.virtualusers.api.EntryPointKt"
}

configurations.all {
    resolutionStrategy {
        activateDependencyLocking()
        failOnVersionConflict()
        eachDependency {
            when (requested.module.toString()) {
                "commons-codec:commons-codec" -> useVersion("1.10")
                "com.google.code.gson:gson" -> useVersion("2.8.2")
                "org.slf4j:slf4j-api" -> useVersion("1.8.0-alpha2")
            }
            when (requested.group) {
                "org.jetbrains.kotlin" -> useVersion(kotlinVersion)
            }
        }
    }
}

repositories {
    mavenLocal()
    maven(url = URI("https://packages.atlassian.com/maven-external/"))
}
