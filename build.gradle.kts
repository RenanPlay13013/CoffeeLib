plugins {
    base
}

allprojects {
    group = "net.loyalnetwork"
    version = "1.0-SNAPSHOT"
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

val distDir = layout.projectDirectory.dir("dist")

tasks.register<Copy>("assembleDist") {
    group = "distribution"
    description = "Builds every subproject and copies the resulting jars into ./dist."

    dependsOn(subprojects.map { it.tasks.named("jar") })

    from(subprojects.map { it.tasks.named("jar") })
    into(distDir)
}

// `base` gives the root project its own "build" task; an unqualified
// `./gradlew build` already runs every subproject's "build" too (Gradle
// matches the task name across all projects by default), so this just adds
// the dist-folder copy to that same invocation instead of a separate step.
tasks.named("build") {
    dependsOn("assembleDist")
}
