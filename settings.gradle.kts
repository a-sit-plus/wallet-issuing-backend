pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "backend"

include( "http", "testrig")

includeBuild("pupilidumbrella"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:pupilidumbrella-jvm")).using(project(":pupilidumbrella"))
    }
}
includeBuild("remote-crypto-provider"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:remotecrypto")).using(project(":lib"))
    }
}

includeBuild("attestation-service"){
    dependencySubstitution {
        substitute(module("at.asitplus:attestation-service"))
    }
}

