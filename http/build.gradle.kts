
import at.asitplus.gradle.bouncycastle
import at.asitplus.gradle.coroutines
import at.asitplus.gradle.gitLab
import at.asitplus.gradle.ktor
import at.asitplus.gradle.napier

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    id("org.springframework.boot") version libs.versions.spring.boot.get()
    id("at.asitplus.gradle.conventions")
}

val artifactVersion: String by extra
group = "at.asitplus.wallet"
version = artifactVersion

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    //annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.session:spring-session-core")
    implementation(libs.spring.boot.admin.starter.client)

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    implementation(libs.nimbus.jose.jwt)
    implementation(libs.webjars.locator)
    implementation(libs.webjars.bootstrap)
    implementation(libs.webjars.jquery)
    implementation(libs.webjars.datatables)
    implementation(libs.qrcode.kotlin)

    implementation(libs.wallet.taxid)
    implementation(libs.wallet.eupid)
    implementation(libs.wallet.eupid.sdjwt)
    implementation(libs.wallet.mdl)
    implementation(libs.wallet.por)
    implementation(libs.wallet.cor)
    implementation(libs.wallet.healthid)
    implementation(libs.wallet.ehic)
    implementation(libs.wallet.cr)
    implementation(libs.wallet.vck.jvm)
    implementation(libs.wallet.vck.openid.ktor.jvm)
    implementation(napier())
    implementation(bouncycastle("bcpkix", "jdk18on"))
    implementation(ktor("http"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-logging"))
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("serialization-kotlinx-json"))

    implementation(libs.google.cbor)
    implementation(libs.scrimage.core)
    runtimeOnly("com.h2database:h2")
    runtimeOnly(libs.postgresql)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.mockito.kotlin)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation(ktor("client-java"))
    testImplementation(libs.okhttp.mockwebserver)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    //we just warn here, so the duplicates are printed.
    //this is only relevant for a composite build and will nicely print out the warning that
    //the VC-K jar from the composite build overwrites the one from the gradle cache, just like we want it
    duplicatesStrategy = DuplicatesStrategy.WARN
    launchScript()
}

springBoot {
    buildInfo()
}

val gitLabProjectId: String by extra

publishing {
    publications {
        create<MavenPublication>("bootJava") {
            artifact(tasks.getByName("bootJar"))
        }
    }
    gitLab(gitLabProjectId.toInt())
}
