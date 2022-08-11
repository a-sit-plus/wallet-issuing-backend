pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "backend"

include( "http")
includeBuild("vclib"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:vclib-jvm")).using(project(":vclib"))
    }
}
includeBuild("pupilidlib"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:pupilidlib-jvm")).using(project(":pupilidlib"))
    }
}
includeBuild("remote-crypto-provider"){
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:remotecrypto")).using(project(":lib"))
    }
}
