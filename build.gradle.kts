plugins {
    val kotlinVer = libs.versions.kotlin.get()
    kotlin("jvm") version kotlinVer apply false
    kotlin("plugin.serialization") version kotlinVer apply false
    kotlin("plugin.spring") version kotlinVer apply false
    kotlin("plugin.jpa") version kotlinVer apply false
    kotlin("plugin.allopen") version kotlinVer apply false
    id("at.asitplus.gradle.conventions") version "20260122"
}
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-allopen") //Spring
        classpath("org.jetbrains.kotlin:kotlin-noarg") //JPA
    }
}
