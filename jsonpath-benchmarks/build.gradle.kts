// JMH benchmarks comparing this library with Jayway JsonPath. Not published.
//
//   ./gradlew :jsonpath-benchmarks:jmh                       all benchmarks (about 45 minutes)
//   ./gradlew :jsonpath-benchmarks:jmh -PjmhArgs="Query.*"   a subset (JMH command line)
//   ./gradlew :jsonpath-benchmarks:test                      checks that both libraries select the same values

dependencies {
    implementation(project(":jsonpath-core"))
    implementation(project(":jsonpath-jackson2"))
    implementation(libs.jayway.jsonpath)
    implementation(libs.jmh.core)
    annotationProcessor(libs.jmh.generator)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // JMH's annotation processor triggers lint warnings we cannot fix.
    options.compilerArgs.remove("-Werror")
}

// The benchmark jar is a build tool, not a library.
tasks.withType<Javadoc>().configureEach { enabled = false }

tasks.register<JavaExec>("jmh") {
    description = "Runs the JMH benchmarks and writes build/jmh-results.json."
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    // Ten forks: some benchmarks run at one of two speeds per JVM, depending on what the JIT compiler happens to
    // compile first (Jayway's wildcard query: about 49 or 69 µs), so a single fork can land on either.
    val extra = providers.gradleProperty("jmhArgs").map { it.split(" ") }.getOrElse(emptyList())
    args = listOf("-f", "10", "-wi", "3", "-w", "1s", "-i", "5", "-r", "1s",
        "-rf", "json", "-rff", layout.buildDirectory.file("jmh-results.json").get().asFile.path) + extra
}
