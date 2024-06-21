import at.asitplus.gradle.bouncycastle
import at.asitplus.gradle.coroutines
import at.asitplus.gradle.gitLab
import at.asitplus.gradle.gitlab
import at.asitplus.gradle.ktor
import at.asitplus.gradle.napier
import at.asitplus.gradle.serialization

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    id("org.springframework.boot") version VersionsBackend.spring.boot
    id("io.spring.dependency-management") version VersionsBackend.spring.`dependency-management`
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

dependencies {
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.session:spring-session-core")
    implementation("de.codecentric:spring-boot-admin-starter-client:${VersionsBackend.spring.`admin-starter-client`}")

    implementation("org.springdoc:springdoc-openapi-ui:${VersionsBackend.spring.doc}")
    implementation("org.springdoc:springdoc-openapi-kotlin:${VersionsBackend.spring.doc}")
    implementation("org.springdoc:springdoc-openapi-security:${VersionsBackend.spring.doc}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("com.nimbusds:nimbus-jose-jwt:${VersionsBackend.nimbus}")
    implementation(vclib.napier)
    implementation(coroutines())
    implementation(serialization("json"))
    implementation(bouncycastle("bcpkix"))
    implementation("com.google.zxing:core:${VersionsBackend.zxing}")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("org.webjars:webjars-locator:${VersionsBackend.webjars.locator}")
    implementation("org.webjars:bootstrap:${VersionsBackend.webjars.bootstrap}")
    implementation("org.webjars:jquery:${VersionsBackend.webjars.jquery}")
    implementation("org.webjars:datatables:${VersionsBackend.webjars.datatables}")

    implementation("at.asitplus.wallet:idacredential:${VersionsBackend.ida}")
    implementation("at.asitplus.wallet:eupidcredential:${VersionsBackend.eupid}")
    implementation("at.asitplus.wallet:mobiledrivinglicence:${VersionsBackend.mdl}")
    implementation(vclib.vclib)
    implementation(vclib.vclib.openid)
    implementation("at.asitplus:attestation-service:${VersionsBackend.attestation}")

    implementation("com.google.iot.cbor:cbor:${VersionsBackend.`google-cbor`}")
    implementation("com.sksamuel.scrimage:scrimage-core:${VersionsBackend.scrimage}")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql:${VersionsBackend.pgsql}")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${VersionsBackend.mockito}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation(ktor("client-java"))
    testImplementation("com.squareup.okhttp3:mockwebserver:${VersionsBackend.okhttp}")
}

tasks.test {
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
    launchScript()
}

springBoot {
    buildInfo()
}


val gitLabProjectId: String by extra
val gitLabGroupId: String by extra

repositories {
    mavenLocal()
    gitlab(gitLabGroupId.toInt()) accessTokenFrom extra
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

publishing {
    publications {
        create<MavenPublication>("bootJava") {
            artifact(tasks.getByName("bootJar"))
        }
    }
    gitLab(gitLabProjectId.toInt())
}
