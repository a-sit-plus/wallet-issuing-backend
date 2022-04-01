buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}


tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
