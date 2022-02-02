rootProject.name = "backend"

pluginManagement {
    plugins {
        id("org.springframework.boot") version "2.6.3"
        id("io.spring.dependency-management") version "1.0.11.RELEASE"
        kotlin("jvm") version "1.6.10"
        kotlin("plugin.spring") version "1.6.10"
        kotlin("plugin.jpa") version "1.6.10"
        kotlin("plugin.serialization") version "1.6.10"
    }
}

include("http")
