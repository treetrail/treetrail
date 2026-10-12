plugins {
    id("treetrail.published-conventions")
}

description = "OpenAPI Overlay (1.0, 1.1, 1.2) for Java: applies overlays to OpenAPI descriptions as Jackson 2 trees, with RFC 9535 JSONPath targets."

treetrail {
    coverage(line = 0.97, branch = 0.92)
}

dependencies {
    api(project(":jsonpath-jackson2"))
    api(libs.jackson2.databind)
    implementation(libs.jackson2.yaml)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.jazzer.junit)
    testImplementation(libs.jqwik)
    // Rfc9535TargetsTest: what Jayway JsonPath, which other Java overlay tools use, does with the same targets.
    testImplementation(libs.jayway.jsonpath)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    // The module name of jsonpath-jackson2 ends in a digit on purpose.
    options.compilerArgs.add("-Xlint:-module")
}

// Fuzzing (Jazzer), as in jsonpath-core: `test` replays the seeds and saved findings, `fuzz` fuzzes.
//   ./gradlew :openapi-overlay:fuzz -PfuzzDuration=10m
val fuzzTargets = listOf("applyAnyOverlay")
val fuzzDuration = providers.gradleProperty("fuzzDuration").orElse("15s")
val fuzzTasks = fuzzTargets.map { target ->
    tasks.register<Test>("fuzz" + target.replaceFirstChar { it.uppercase() }) {
        description = "Fuzzes OverlayFuzzTest.$target."
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }
        filter {
            includeTestsMatching("io.github.treetrail.openapi.overlay.OverlayFuzzTest.$target")
        }
        environment("JAZZER_FUZZ", "1")
        systemProperty("jazzer.max_duration", fuzzDuration.get())
        workingDir = layout.buildDirectory.dir("fuzz/$target").get().asFile
        doFirst { workingDir.mkdirs() }
        outputs.upToDateWhen { false }
    }
}
tasks.register("fuzz") {
    description = "Fuzzes every fuzz target for -PfuzzDuration (default 15s) each."
    group = "verification"
    dependsOn(fuzzTasks)
}
