
import at.asitplus.gradle.bouncycastle
import at.asitplus.gradle.coroutines
import at.asitplus.gradle.gitLab
import at.asitplus.gradle.ktor

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
    id("org.springframework.boot") version VersionsBackend.spring.boot
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
    implementation("de.codecentric:spring-boot-admin-starter-client:${VersionsBackend.spring.`admin-starter-client`}")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("com.nimbusds:nimbus-jose-jwt:${VersionsBackend.nimbus}")
    implementation(vclib.napier)
    implementation(coroutines())
    implementation(bouncycastle("bcpkix"))
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")
    implementation("org.webjars:webjars-locator:${VersionsBackend.webjars.locator}")
    implementation("org.webjars:bootstrap:${VersionsBackend.webjars.bootstrap}")
    implementation("org.webjars:jquery:${VersionsBackend.webjars.jquery}")
    implementation("org.webjars:datatables:${VersionsBackend.webjars.datatables}")
    implementation("io.github.g0dkar:qrcode-kotlin:${VersionsBackend.qrcode}")
    implementation("at.asitplus.wallet:taxid:${VersionsBackend.taxid}")
    implementation("at.asitplus.wallet:idacredential:${VersionsBackend.ida}")
    implementation("at.asitplus.wallet:eupidcredential:${VersionsBackend.eupid}")
    implementation("at.asitplus.wallet:eupidcredential-sdjwt:${VersionsBackend.`eupid-sdjwt`}")
    implementation("at.asitplus.wallet:mobiledrivinglicence:${VersionsBackend.mdl}")
    implementation("at.asitplus.wallet:powerofrepresentation:${VersionsBackend.por}")
    implementation("at.asitplus.wallet:certificateofresidence:${VersionsBackend.cor}")
    implementation("at.asitplus.wallet:healthid:${VersionsBackend.healthId}")
    implementation("at.asitplus.wallet:ehic:${VersionsBackend.ehic}")
    implementation("at.asitplus.wallet:company-registration:${VersionsBackend.cr}")
    implementation("at.asitplus.wallet:vck-jvm:${VersionsBackend.vck}")
    implementation("at.asitplus.wallet:vck-openid-ktor-jvm:${VersionsBackend.vck}")
    implementation("at.asitplus:attestation-service:${VersionsBackend.attestation}")
    implementation(ktor("http"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-logging"))
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("serialization-kotlinx-json"))

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
