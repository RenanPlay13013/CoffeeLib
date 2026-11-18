plugins {
    id("com.gradleup.shadow") version "8.3.5"
}

// The pool and the drivers ship inside CoffeeLib, relocated under
// net.loyalnetwork.coffeelib.libs: another mod or plugin bundling its own
// HikariCP or driver - a different version, most likely - cannot clash with
// these. So this module's jar is its shadow jar: its own classes plus the
// relocated libraries, and nothing of api/core, which the platform jars
// bundle themselves.
val shaded: Configuration by configurations.creating

dependencies {
    api(project(":coffeelib-core"))

    shaded("com.zaxxer:HikariCP:5.1.0") {
        exclude(group = "org.slf4j") // added below, with a backend
    }
    // MySQL and MariaDB both: the MariaDB driver speaks MySQL's protocol, and is LGPL,
    // where MySQL's own Connector/J is GPL.
    shaded("org.mariadb.jdbc:mariadb-java-client:3.4.1") {
        isTransitive = false
    }
    shaded("org.postgresql:postgresql:42.7.4") {
        isTransitive = false // only checker-qual's annotations
    }
    // Hikari logs through SLF4J. Not every platform has it (Spigot 1.8 does not), and one
    // that does may bind it to something else, so CoffeeLib brings its own, relocated,
    // writing to java.util.logging - which every platform shows in its console.
    shaded("org.slf4j:slf4j-api:2.0.16")
    shaded("org.slf4j:slf4j-jdk14:2.0.16")

    // H2, embedded: pure Java, so relocated like the rest.
    shaded("com.h2database:h2:2.3.232")

    // SQLite is the exception: its native library binds to the org.sqlite package by name,
    // so relocating it breaks it. It is not in this jar at all - Paper servers ship it, and
    // the Forge and NeoForge jars nest it (META-INF/jarjar), where the loader picks one
    // copy among every mod that nests it.
    testImplementation("org.xerial:sqlite-jdbc:${property("sqlite_jdbc_version")}")
}

// Compiled against as they are; relocated only in the jar.
configurations.compileOnly { extendsFrom(shaded) }
configurations.testImplementation { extendsFrom(shaded) }

tasks.shadowJar {
    archiveClassifier.set("")
    configurations = listOf(shaded)
    val libs = "net.loyalnetwork.coffeelib.libs"
    relocate("com.zaxxer.hikari", "$libs.hikari")
    relocate("org.mariadb.jdbc", "$libs.mariadb")
    relocate("org.postgresql", "$libs.postgresql")
    relocate("org.slf4j", "$libs.slf4j")
    relocate("org.h2", "$libs.h2")
    // java.sql.Driver and SLF4J's provider are found through META-INF/services; this
    // rewrites those files along with the classes they name.
    mergeServiceFiles()
    exclude("META-INF/maven/**", "META-INF/versions/**", "module-info.class",
        "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// The plain jar would be this module's classes without the libraries they call.
tasks.jar {
    enabled = false
}
tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Self-contained like the platform jars: the artifact alone, without a POM listing the
// libraries it already holds (under other names).
apply(plugin = "maven-publish")
extensions.configure<PublishingExtension> {
    publications.register<MavenPublication>("mavenJava") {
        artifact(tasks.shadowJar)
    }
}
