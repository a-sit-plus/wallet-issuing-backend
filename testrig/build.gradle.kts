
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("org.springframework.boot") version "2.7.2"
    id("io.spring.dependency-management") version "1.0.12.RELEASE"
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.spring") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"
}

val artifactVersion: String by extra
group = "at.asitplus.wallet"
version = artifactVersion
java.sourceCompatibility = JavaVersion.VERSION_11

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.apache.httpcomponents:httpclient")
    implementation("com.nimbusds:nimbus-jose-jwt:9.23")
    implementation("at.asitplus.wallet:pupilidlib-jvm:${VersionsBackend.pupilidlib}"){
        layout.projectDirectory.dir("..").dir("pupilidlib").dir("repo").asFile.let {
            if (it.exists() && it.isDirectory && it.listFiles()!!.isNotEmpty()) {
                logger.info("assuming PupilIdLib maven artifact present")
            } else {
                exec {
                    workingDir = layout.projectDirectory.dir("..").dir("pupilidlib").asFile
                    println("descending into ${workingDir.absolutePath}")
                    logger.lifecycle("Rebuilding PupilIdLib maven artifacts")
                    commandLine("./gradlew", "publishAllPublicationsToLocalRepository")
                }
            }
        }
    }

    implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    implementation("io.ktor:ktor-client-core:2.1.0")
    implementation("io.ktor:ktor-client-cio:2.1.0")
    implementation("io.ktor:ktor-server-core:2.1.0")
    implementation("io.ktor:ktor-server-netty:2.1.0")
    implementation("io.ktor:ktor-client-content-negotiation:2.1.0")
    implementation("io.ktor:ktor-server-content-negotiation:2.1.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.1.0")
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


tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    launchScript()
}

Properties().apply {
    kotlin.runCatching { load(FileInputStream(project.rootProject.file("local.properties"))) }
    forEach { (k, v) -> extra.set(k as String, v) }
}

val gitLabPrivateToken: String? by extra
val gitLabProjectId: String by extra
val gitLabGroupId: String by extra

repositories {
    maven(uri(layout.projectDirectory.dir("..").dir("pupilidlib").dir("repo")))
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