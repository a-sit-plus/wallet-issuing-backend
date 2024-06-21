pluginManagement {
    repositories {
        maven {
            url = uri("https://raw.githubusercontent.com/a-sit-plus/gradle-conventions-plugin/mvn/repo")
            name = "aspConventions"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}


rootProject.name = "backend"

include("http")


dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("vclib") {
            from("at.asitplus.wallet:vclib-openid-versionCatalog:3.8.0")
        }
    }
}

