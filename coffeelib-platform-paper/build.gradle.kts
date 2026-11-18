// Project name is "coffeelib-platform-paper" — strip "platform-" so this
// matches the other platform modules' jar naming (coffeelib-forge-1.20.1, etc.).
base {
    archivesName.set("coffeelib-paper")
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    // PlaceholderAPI, for the optional bridge. compileOnly below: never bundled, only used
    // when the server has the PlaceholderAPI plugin installed.
    maven("https://repo.extendedclip.com/releases/")
}

tasks.jar {
    // The lambda below hides where those files come from, so the dependency on the
    // sibling modules' own jar tasks is stated outright.
    dependsOn(bundled)

    from({ bundled.map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("META-INF/maven/**")
        // The multi-release copies are the same classes compiled for a newer JVM; the
        // root ones are complete on their own.
        exclude("META-INF/versions/**")
        exclude("module-info.class")
    }

    // Two bundled jars can both carry e.g. a LICENSE entry; the classes themselves are
    // distinct, so first-one-wins is the right call.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version, "hibernateVersion" to project.property("hibernate_version"),
            "h2Version" to project.property("h2_version"))
    }
}

// Paper loads this jar as a plugin, and a plugin that depends on CoffeeLib sees only
// what is inside it — no Maven resolution. So the api/core classes (and the format
// backends) ship here too, the same reasoning as on the Forge-like platforms.
val bundled: Configuration by configurations.creating

dependencies {
    api(project(":coffeelib-core"))

    // The server already provides gson; configurate's older copy would only shadow it.
    // configurate-yaml/-hocon carry relocated SnakeYAML and typesafe-config inside
    // themselves, so they need no exclusion.
    bundled(project(":coffeelib-core")) {
        exclude(group = "com.google.code.gson")
    }

    // The database module, as its shadow jar: its classes plus HikariCP and the drivers,
    // already relocated. Bundled like core; compiled against for CoffeeLib.database().
    compileOnly(project(path = ":coffeelib-database", configuration = "shadowRuntimeElements"))
    bundled(project(path = ":coffeelib-database", configuration = "shadowRuntimeElements"))

    // The YAML engine (Configurate) now lives in core alongside the JSON and HOCON
    // backends, so every platform shares one implementation per format instead of
    // Paper keeping its own copy.

    // PaperMC's own paper-api artifact only goes back to 1.9.4 in their repo —
    // Paper 1.8 was never published there under its own coordinates. Spigot's
    // API is the actual baseline Paper 1.8 implemented, and JavaPlugin/
    // YamlConfiguration are identical at this layer, so this one module still
    // covers 1.8.x -> latest without recompiling per version.
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    // CoffeeHibernate compiles against it; the server downloads it (plugin.yml libraries).
    compileOnly("org.hibernate.orm:hibernate-core:${project.property("hibernate_version")}")
    compileOnly("me.clip:placeholderapi:2.11.6") {
        isTransitive = false
    }

    testImplementation("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-core:5.14.2")
}
