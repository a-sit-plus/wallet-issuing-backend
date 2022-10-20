import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.*

plugins {
    id("org.springframework.boot") version VersionsBackend.spring.boot
    id("io.spring.dependency-management") version VersionsBackend.spring.`dependency-management`
    id("maven-publish")
    kotlin("jvm") version VersionsBackend.kotlin
    kotlin("plugin.spring") version VersionsBackend.kotlin
    kotlin("plugin.jpa") version VersionsBackend.kotlin
    kotlin("plugin.serialization") version VersionsBackend.kotlin
}

val artifactVersion: String by extra
group = "at.asitplus.wallet"
version = artifactVersion
java.sourceCompatibility = JavaVersion.VERSION_11

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    all {
        resolutionStrategy
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-config-client:${VersionsBackend.spring.`cloud-config-client`}")
    implementation("org.springframework.session:spring-session-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.apache.httpcomponents:httpclient")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${VersionsBackend.serialization.json}")
    implementation("com.nimbusds:nimbus-jose-jwt:${VersionsBackend.jose}")
    implementation("com.google.zxing:core:${VersionsBackend.zxing}")
    implementation("org.webjars:webjars-locator:${VersionsBackend.webjars.locator}")
    implementation("org.webjars:bootstrap:${VersionsBackend.webjars.bootstrap}")
    implementation("org.webjars:jquery:${VersionsBackend.webjars.jquery}")
    implementation("org.webjars:datatables:${VersionsBackend.webjars.datatables}")
    implementation("de.codecentric:spring-boot-admin-starter-client:${VersionsBackend.spring.`admin-starter-client`}")
    implementation("at.asitplus.wallet:vclib-jvm")
    implementation("at.asitplus.wallet:pupilidlib-jvm")
    implementation("at.asitplus:android-attestation")
    implementation("ch.veehait.devicecheck:devicecheck-appattest:${VersionsBackend.deviceCheck}")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:${VersionsBackend.`jackson-cbor`}")
    implementation("net.swiftzer.semver:semver:${VersionsBackend.semver}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${VersionsBackend.coroutines}")
    implementation("org.bouncycastle:bcpkix-jdk15on:${VersionsBackend.bouncycastle}")
    implementation("org.springdoc:springdoc-openapi-ui:${VersionsBackend.spring.doc}")
    implementation("org.springdoc:springdoc-openapi-kotlin:${VersionsBackend.spring.doc}")
    implementation("org.springdoc:springdoc-openapi-security:${VersionsBackend.spring.doc}")
    implementation("io.github.aakira:napier:${VersionsBackend.napier}")
    implementation("com.google.iot.cbor:cbor:${VersionsBackend.`google-cbor`}")
    implementation("at.asitplus.hsmfacade:provider:${VersionsBackend.hsmf}")
    implementation("at.asitplus.wallet:remotecrypto")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql:${VersionsBackend.pgsql}")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${VersionsBackend.coroutines}")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${VersionsBackend.mockito}")
    testImplementation("io.ktor:ktor-client-java:${VersionsBackend.ktor}")
    testImplementation("io.kotest:kotest-assertions-core:${VersionsBackend.kotest}")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:${VersionsBackend.datetime}")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xjsr305=strict",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlin.time.ExperimentalTime"
        )
        jvmTarget = "11"
    }
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
    launchScript()
}

springBoot {
    buildInfo()
}

Properties().apply {
    kotlin.runCatching { load(FileInputStream(project.rootProject.file("local.properties"))) }
    forEach { (k, v) -> extra.set(k as String, v) }
}

val gitLabPrivateToken: String? by extra
val gitLabProjectId: String by extra
val gitLabGroupId: String by extra

repositories {
    mavenLocal()
    if (System.getenv("CI_JOB_TOKEN") != null || gitLabPrivateToken != null) {
        maven {
            name = "gitlab"
            url = uri("https://gitlab.iaik.tugraz.at/api/v4/groups/$gitLabGroupId/-/packages/maven")
            if (gitLabPrivateToken != null) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Private-Token"
                    value = gitLabPrivateToken
                }
            } else if (System.getenv("CI_JOB_TOKEN") != null) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN")
                }
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }

    if (System.getenv("CI_JOB_TOKEN") != null || gitLabPrivateToken != null) {
        maven {
            name = "gitlabhsm"
            url = uri("https://gitlab.iaik.tugraz.at/api/v4/groups/119/-/packages/maven")
            if (gitLabPrivateToken != null) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Private-Token"
                    value = gitLabPrivateToken
                }
            } else if (System.getenv("CI_JOB_TOKEN") != null) {
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN")
                }
            }
            authentication {
                create<HttpHeaderAuthentication>("header")
            }
        }
    }
    mavenCentral()
}


publishing {
    publications {
        create<MavenPublication>("bootJava") {
            artifact(tasks.getByName("bootJar"))
        }
    }
    repositories {
        mavenLocal()
        if (System.getenv("CI_JOB_TOKEN") != null) {
            maven {
                name = "gitlab"
                url = uri("https://gitlab.iaik.tugraz.at/api/v4/projects/$gitLabProjectId/packages/maven")
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN")
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
        }
    }
}
