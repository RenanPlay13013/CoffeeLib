// Project name is "coffeelib-platform-paper" — strip "platform-" so this
// matches the other platform modules' jar naming (coffeelib-forge-1.20.1, etc.).
base {
    archivesName.set("coffeelib-paper")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

dependencies {
    api(project(":coffeelib-core"))

    // Configurate is the YAML engine here — it replaces the hand-rolled YamlFormat.
    // Yanked/implementation so configurate's own types never leak into this module's
    // public API (only ConfigBackend, which YAML jams, is exposed).
    implementation("org.spongepowered:configurate-yaml:4.2.0")

    // PaperMC's own paper-api artifact only goes back to 1.9.4 in their repo —
    // Paper 1.8 was never published there under its own coordinates. Spigot's
    // API is the actual baseline Paper 1.8 implemented, and JavaPlugin/
    // YamlConfiguration are identical at this layer, so this one module still
    // covers 1.8.x -> latest without recompiling per version.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")

    testImplementation("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.14.2")
}
