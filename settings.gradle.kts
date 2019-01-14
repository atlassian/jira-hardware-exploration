import java.net.URI

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(URI("http://central.maven.org/maven2/"))
        maven(URI("https://dl.bintray.com/kotlin/kotlin-dev/"))
        maven(URI("https://packages.atlassian.com/gradle-plugins-cache"))
    }
}

include("virtual-users")
