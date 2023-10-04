pluginManagement {
    repositories {
        maven("https://maven.pkg.jetbrains.space/kotlin/p/dokka/dev")
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
    }
    includeBuild("pupilidlib/vclib/conventions-vclib")

}

rootProject.name = "backend"

include("http", "testrig")

includeBuild("remote-crypto-provider") {
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:remotecrypto")).using(project(":lib"))
    }
}

includeBuild("pupilidlib/vclib") {
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:vclib-openid")).using(project(":vclib-openid"))
        substitute(module("at.asitplus.wallet:vclib-aries")).using(project(":vclib-aries"))
        substitute(module("at.asitplus.wallet:vclib")).using(project(":vclib"))
    }
}

includeBuild("pupilidlib") {
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:pupilidlib")).using(project(":pupilidlib"))
    }
}

includeBuild("id-austria-credential") {
    dependencySubstitution {
        substitute(module("at.asitplus.wallet:idacredential")).using(project(":idacredential"))
    }
}