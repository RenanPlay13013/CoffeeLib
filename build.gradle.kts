allprojects {
    group = "net.loyalnetwork"
    version = "1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

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

val distDir = layout.buildDirectory.dir("dist")

tasks.register<Copy>("assembleDist") {
    group = "distribution"
    description = "Builds every subproject and copies the resulting jars into build/dist."

    dependsOn(subprojects.map { it.tasks.named("jar") })

    from(subprojects.map { it.tasks.named("jar") })
    into(distDir)
}
