pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("vclib")
    includeBuild("pupilidlib")
    includeBuild("remote-crypto-provider")
}

rootProject.name = "PupilID"

include( "http")

