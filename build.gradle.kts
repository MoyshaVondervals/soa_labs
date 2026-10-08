plugins { base }

subprojects {
    apply(plugin = "java")
    apply(plugin = "war")
    repositories { mavenCentral() }
    extensions.configure<JavaPluginExtension> { toolchain.languageVersion.set(JavaLanguageVersion.of(17)) }
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    dependencies {
        "compileOnly"("jakarta.platform:jakarta.jakartaee-api:10.0.0")
        "implementation"("com.fasterxml.jackson.core:jackson-databind:2.18.3")
        "implementation"("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.3")
        "testImplementation"("jakarta.platform:jakarta.jakartaee-api:10.0.0")
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

val npmFile: File = run {
    val name = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
    val path = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    (path + listOf("/opt/homebrew/bin", "/usr/local/bin")).map { File(it, name) }.firstOrNull { it.canExecute() }
        ?: File(name)
}
val npm = npmFile.path
val npmPath = listOfNotNull(npmFile.parent, System.getenv("PATH")).joinToString(File.pathSeparator)

tasks.register<Exec>("frontendInstall") {
    workingDir("frontend")
    environment("PATH", npmPath)
    commandLine(npm, "ci", "--no-audit", "--no-fund")
    inputs.files("frontend/package.json", "frontend/package-lock.json")
    outputs.dir("frontend/node_modules")
}

tasks.register<Exec>("frontendBuild") {
    dependsOn("frontendInstall")
    workingDir("frontend")
    environment("PATH", npmPath)
    commandLine(npm, "run", "build")
    inputs.dir("frontend/src")
    inputs.dir("frontend/public")
    inputs.files("frontend/index.html", "frontend/tsconfig.json", "frontend/vite.config.ts")
    outputs.dir("frontend/dist")
}
