dependencies {
    api(project(":coffeelib-api"))

    // The formats CoffeeLib owns end to end: @ConfigFile(format = YAML | JSON | HOCON),
    // resolved by core.config.format.ConfigBackends. Kept as implementation deps so
    // configurate's types never leak into core's public API (only ConfigBackend does),
    // while consumers still get them on their runtime classpath.
    implementation("org.spongepowered:configurate-yaml:4.2.0")
    implementation("org.spongepowered:configurate-gson:4.2.0")
    implementation("org.spongepowered:configurate-hocon:4.2.0")
}