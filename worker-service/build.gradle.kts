dependencies {
    testImplementation("org.hibernate.validator:hibernate-validator:8.0.2.Final")
    testRuntimeOnly("org.glassfish.expressly:expressly:5.0.0")
}

tasks.war {
    archiveFileName.set("worker.war")
    dependsOn(rootProject.tasks.named("frontendBuild"))
    from(rootProject.file("frontend/dist"))
    from(rootProject.file("docs/openapi")) {
        into("docs")
        filesMatching("*-service.yaml") {
            val serviceUrl = if (name.startsWith("worker")) "https://localhost:8543" else "https://localhost:18081"
            filter { line: String -> if (line.startsWith("  - url: ")) "  - url: $serviceUrl" else line }
        }
    }
}
