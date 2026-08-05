pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    // Lets ModDevGradle auto-provision the exact JDK a module's toolchain asks
    // for, instead of failing when only a different major version is installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CoffeeLib-All"

include(
    "coffeelib-api",
    "coffeelib-core",
    "coffeelib-platform-paper",
    "coffeelib-platform-forge-1.20.1",
    "coffeelib-platform-neoforge-1.21.1"
)