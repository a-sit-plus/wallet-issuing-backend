
import at.asitplus.gradle.bouncycastle
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
            compilerOptions {
                this.freeCompilerArgs.add("-Xnested-type-aliases")
            }
            languageSettings.optIn("kotlin.time.ExperimentalTime")
        }
    }
}
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation(platform(libs.spring.cloud.config.dependencies))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.module")
        exclude(group = "com.fasterxml.jackson.datatype")
    }
    implementation("org.springframework.session:spring-session-core")
    implementation(libs.spring.boot.admin.starter.client)
    implementation(libs.spring.cloud.starter.config)

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    implementation(libs.nimbus.jose.jwt)
    implementation(libs.webjars.locator)
    implementation(libs.webjars.bootstrap)
    implementation(libs.webjars.jquery)
    implementation(libs.webjars.datatables)
    implementation(libs.qrcode.kotlin)

    implementation(libs.wallet.vck)
    implementation(libs.wallet.vck.openid.ktor)
    implementation(napier())
    implementation(bouncycastle("bcpkix", "jdk18on"))
    implementation(ktor("http"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-logging"))
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("serialization-kotlinx-json"))

    runtimeOnly("com.h2database:h2")
    runtimeOnly(libs.postgresql)
    runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.mockito.kotlin)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation(ktor("client-java"))
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
}

springBoot {
    buildInfo()
}

publishing {
    publications {
        create<MavenPublication>("bootJava") {
            artifact(tasks.getByName("bootJar"))
        }
    }
}
