pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.neoforged.net/releases")
    }
}

rootProject.name = "CoffeeLib-All"

include(
    "coffeelib-api",
    "coffeelib-core",
    "coffeelib-platform-paper",
    "coffeelib-platform-forge-1.20.1",
    "coffeelib-platform-neoforge-1.21.1"
)