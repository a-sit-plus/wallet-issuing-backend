import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.6.5"
    id("io.spring.dependency-management") version "1.0.11.RELEASE"
    id("maven-publish")
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.spring") version "1.6.10"
    kotlin("plugin.jpa") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
}
val artifactVersion: String by extra
group = "at.asitplus.wallet"
version = artifactVersion
java.sourceCompatibility = JavaVersion.VERSION_1_8

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.cloud:spring-cloud-config-client:3.1.1")
    implementation("org.springframework.session:spring-session-core")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.apache.httpcomponents:httpclient")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.3.2")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    implementation("com.nimbusds:nimbus-jose-jwt:9.21")
    implementation("org.json:json:20211205")
    implementation("com.google.zxing:core:3.4.1")
    implementation("org.webjars:webjars-locator:0.45")
    implementation("org.webjars:bootstrap:5.1.3")
    implementation("org.webjars:jquery:3.6.0")
    implementation("org.webjars:datatables:1.11.4")
    implementation("de.codecentric:spring-boot-admin-starter-client:2.6.2")
    implementation("at.asitplus.wallet:vclib-jvm:1.1.0-SNAPSHOT")
    implementation("at.asitplus.wallet:pupilidlib-jvm:1.1.0-SNAPSHOT")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")
    implementation("org.springdoc:springdoc-openapi-ui:1.6.6")
    implementation("org.springdoc:springdoc-openapi-kotlin:1.6.6")
    implementation("org.springdoc:springdoc-openapi-security:1.6.6")
    implementation("io.github.aakira:napier:2.4.0")
    runtimeOnly("com.h2database:h2")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("io.ktor:ktor-client-cio:2.0.0-beta-1")
    testImplementation("io.kotest:kotest-assertions-core:5.2.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi", "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
        jvmTarget = "1.8"
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showExceptions = true
        events = setOf(org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED, org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED)
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    systemProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    launchScript()
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
