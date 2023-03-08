buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

tasks.register("listrepos") {
    doLast {
        println("Repositories:")
        project.repositories.map{it as MavenArtifactRepository}
            .forEach{
                println("Name: ${it.name}; url: ${it.url}")
            }
    }
}

tasks.register("clean", Delete::class) {
    doFirst { println("Cleaning all build files") }
    delete(rootProject.buildDir)
    delete(layout.projectDirectory.dir("repo"))
    doLast { println("Clean done") }
}

task<Exec>("purge") {
    dependsOn("clean")
    workingDir = layout.projectDirectory.dir("pupilidumbrella").asFile
    commandLine("./gradlew", "purge")
    doFirst {
        println("descending into ${workingDir.absolutePath}")
        logger.lifecycle("Purging PupilIdUmbrella maven build")
    }
}
