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

    // H2 is NOT relocated for use: H2 writes its own class names into the database file,
    // so a relocated H2 makes files no other tool can open, tied to CoffeeLib's package.
    // The H2 CoffeeLib uses is the published one, shipped beside it (nested on Forge and
    // NeoForge, a plugin library on Paper). This relocated copy is kept for one job only:
    // reading the files CoffeeLib 3.6 and 3.7 wrote with it, to convert them
    // (H2FileUpgrade). Every reference to org.h2 in this module's classes is rewritten to
    // the relocated copy, so code meant for the real H2 names it by string.
    shaded("com.h2database:h2:${property("h2_version")}")

    // SQLite is the exception: its native library binds to the org.sqlite package by name,
    // so relocating it breaks it. It is not in this jar at all - Paper servers ship it, and
    // the Forge and NeoForge jars nest it (META-INF/jarjar), where the loader picks one
    // copy among every mod that nests it.
    testImplementation("org.xerial:sqlite-jdbc:${property("sqlite_jdbc_version")}")

    // Hibernate, for CoffeeHibernate: compiled against only. The Forge and NeoForge jars
    // nest it and the Paper plugin downloads it as a library, so it is never in this jar.
    compileOnly("org.hibernate.orm:hibernate-core:${property("hibernate_version")}")
    compileOnly("org.hibernate.orm:hibernate-community-dialects:${property("hibernate_version")}")
    testImplementation("org.hibernate.orm:hibernate-core:${property("hibernate_version")}")
    testImplementation("org.hibernate.orm:hibernate-community-dialects:${property("hibernate_version")}")
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
    // The pool names its driver class, so nothing needs the relocated drivers registered
    // with the JVM-wide DriverManager - where they would answer another mod's
    // DriverManager.getConnection("jdbc:h2:...") with CoffeeLib's private copy.
    mergeServiceFiles {
        exclude("META-INF/services/java.sql.Driver")
    }
    exclude("META-INF/services/java.sql.Driver")
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
