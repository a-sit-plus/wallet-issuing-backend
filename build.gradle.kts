plugins {
    id("at.asitplus.gradle.conventions") version "20250628"
    kotlin("jvm") version "2.1.21" apply false
    kotlin("plugin.serialization") version "2.1.21" apply false
    kotlin("plugin.spring") version "2.1.21" apply false
    kotlin("plugin.jpa") version "2.1.21" apply false
    kotlin("plugin.allopen") version "2.1.21" apply false
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-allopen") //Spring
        classpath("org.jetbrains.kotlin:kotlin-noarg") //JPA
    }
}
