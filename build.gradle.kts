plugins {
    id("at.asitplus.gradle.conventions") version "1.9.23+20240501"
}

/*
* This workaround is required, because the kotlin version defined in the `AspVersions` is not a constant, but parsed from a properties file
* Adding the plugins to the classpath here requires no version string, but instead adds the gradle mpdule containing the required
* plugin definitions to the classpath without applying them. doing so aligns them with the kotlin version used to build this
* project and thus prevents version mismatches).
* There has been an open issue on GitHub for the Kotlin Gradle DSL (because Groovy does not have the restriction on cons) for years
* */
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-allopen") //Spring
        classpath("org.jetbrains.kotlin:kotlin-noarg") //JPA
    }
}
