plugins {
    base
}

// JitPack builds with -Pgroup=com.github.<user> -Pversion=<tag> and then serves the
// artifacts under exactly those coordinates, so an injected value has to win over the
// one hardcoded here - otherwise the build succeeds and every consumer still gets a
// 404. providers.gradleProperty sees only what -P or gradle.properties set, unlike
// findProperty, which would hand back the project's own default and never be null.
val injectedGroup = providers.gradleProperty("group")
val injectedVersion = providers.gradleProperty("version")

allprojects {
    group = injectedGroup.getOrElse("net.loyalnetwork")
    version = injectedVersion.getOrElse("3.2-SNAPSHOT")
}

subprojects {
    apply(plugin = "java-library")

    // Baseline for api/core/paper: the lowest Java version any supported
    // platform module requires (currently Forge 1.20.1's toolchain). A
    // module needing a newer JVM (e.g. NeoForge 1.21.1) overrides this in
    // its own build script — Gradle refuses to let a lower-toolchain module
    // depend on a higher-bytecode one, so this must stay the floor, not a
    // per-module default.
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.42")
        "annotationProcessor"("org.projectlombok:lombok:1.18.42")
        "testCompileOnly"("org.projectlombok:lombok:1.18.42")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.42")

        "testImplementation"(platform("org.junit:junit-bom:6.0.0"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// JitPack serves whatever a build leaves in ~/.m2, so every module a consumer is told
// to depend on needs a publication; coffeelib-api, coffeelib-core and the Paper module
// had none at all, which would have made three of the README's five coordinates resolve
// to nothing even on a green build. The Forge and NeoForge modules declare their own.
//
// api and core publish from the java component, dependencies and all: they are the jars
// you compile against, and a consumer has to resolve Configurate from core's metadata.
configure(subprojects.filter { it.name in listOf("coffeelib-api", "coffeelib-core") }) {
    apply(plugin = "maven-publish")

    extensions.configure<PublishingExtension> {
        publications.register<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

// The Paper jar is self-contained like the other platform jars, so it publishes the
// artifact alone: a POM listing api, core and Configurate would hand a consumer every
// one of those classes twice, once bundled and once resolved.
configure(subprojects.filter { it.name == "coffeelib-platform-paper" }) {
    apply(plugin = "maven-publish")

    extensions.configure<PublishingExtension> {
        publications.register<MavenPublication>("mavenJava") {
            artifact(tasks.named("jar"))
        }
    }
}

val distDir = layout.projectDirectory.dir("dist")

// dist holds two kinds of jar, for two different jobs:
//
//  * one per platform, self-contained (its own classes plus coffeelib-api,
//    coffeelib-core and the Configurate backends) — the file you install into
//    mods/ or plugins/, with nothing else to copy beside it;
//  * coffeelib-api and coffeelib-core, the plain library jars — what you compile
//    against when you want CoffeeLib as a dependency without dragging in a
//    platform you do not target. Being plain is the point here: the formats come
//    from Configurate, which a build tool resolves from these jars' own metadata.
tasks.register<Copy>("assembleDist") {
    group = "distribution"
    description = "Builds every module and copies its jar into ./dist (platform jars self-contained)."

    dependsOn(subprojects.map { it.tasks.named("jar") })

    from(subprojects.map { it.tasks.named("jar") })
    into(distDir)

    // Drop whatever a previous build left here, so a renamed or no longer built jar
    // cannot linger and be installed by mistake.
    doFirst {
        distDir.asFile.listFiles()
            ?.filter { it.isFile && it.name.startsWith("coffeelib-") && it.name.endsWith(".jar") }
            ?.forEach { it.delete() }
    }
}

// `base` gives the root project its own "build" task; an unqualified
// `./gradlew build` already runs every subproject's "build" too (Gradle
// matches the task name across all projects by default), so this just adds
// the dist-folder copy to that same invocation instead of a separate step.
tasks.named("build") {
    dependsOn("assembleDist")
}
