pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "PupilID"

include( "http")
includeBuild("vclib"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:vclib")).using(project(":vclib"))
    }
}
includeBuild("pupilidlib"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:pupilidlib")).using(project(":pupilidlib"))
    }
}
includeBuild("remote-crypto-provider"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:remotecrypto")).using(project(":"))
    }
}
