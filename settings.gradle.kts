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
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")}
        mavenCentral()
    }
    versionCatalogs {
        create("vclib") {
            from("at.asitplus.wallet:vck-openid-versionCatalog:5.0.0-SNAPSHOT")
        }
    }
}

