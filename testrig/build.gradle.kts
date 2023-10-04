import at.asitplus.gradle.datetime
import at.asitplus.gradle.gitlab
import at.asitplus.gradle.ktor

plugins {
    id("org.springframework.boot") version VersionsBackend.spring.boot
    id("io.spring.dependency-management") version VersionsBackend.spring.`dependency-management`
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("at.asitplus.gradle.vclib-conventions")
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
    implementation("org.springframework.boot:spring-boot-starter")

    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("com.nimbusds:nimbus-jose-jwt:${VcLibVersions.Jvm.`jose-jwt`}")
    implementation("at.asitplus.wallet:pupilidlib:${VersionsBackend.pupilidlib}")
    implementation(datetime("jvm"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation(ktor("client-core"))
    implementation(ktor("client-cio"))
    implementation(ktor("client-content-negotiation"))
    implementation(ktor("server-core"))
    implementation(ktor("server-netty"))
    implementation(ktor("server-content-negotiation"))
    implementation(ktor("serialization-kotlinx-json"))
}



tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    launchScript()
}

val gitLabPrivateToken: String? by extra
val gitLabProjectId: String by extra
val gitLabGroupId: String by extra

repositories {
    gitlab(gitLabGroupId.toInt()) accessTokenFrom extra
    gitlab(119, nameOverride = "gitlabhsm")  accessTokenFrom extra
}