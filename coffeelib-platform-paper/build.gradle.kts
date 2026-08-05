repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    api(project(":coffeelib-core"))

    // PaperMC's own paper-api artifact only goes back to 1.9.4 in their repo —
    // Paper 1.8 was never published there under its own coordinates. Spigot's
    // API is the actual baseline Paper 1.8 implemented, and JavaPlugin/
    // YamlConfiguration are identical at this layer, so this one module still
    // covers 1.8.x -> latest without recompiling per version.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")

    testImplementation("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.14.2")
}
