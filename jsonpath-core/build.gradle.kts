plugins {
    `java-test-fixtures`
}

description = "JSONPath (RFC 9535) for Java: passes the JSONPath Compliance Test Suite, no dependencies, works on any JSON tree through a small JsonModel interface."

dependencies {
    // The core has no runtime dependencies on purpose.

    // Test fixtures: the Compliance Test Suite runner, shared with the adapter modules.
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter.api)
    testFixturesImplementation(libs.assertj)
    // Only used to read the compliance test suite.
    testFixturesImplementation(libs.jackson2.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.jackson2.databind)
    testImplementation(libs.jazzer.junit)
    testImplementation(libs.jqwik)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The test fixtures are for this build only and are never published.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
// The sources jar of the test fixtures exists once the publishing plugin adds sources jars.
configurations.matching { it.name == "testFixturesSourcesElements" }.configureEach {
    javaComponent.withVariantsFromConfiguration(this) { skip() }
}

// Fuzzing (Jazzer). `test` replays the seeds and saved findings of every fuzz target (regression mode).
// `fuzz` fuzzes each target in turn; Jazzer fuzzes one target per JVM, hence one task per target.
//   ./gradlew :jsonpath-core:fuzz -PfuzzDuration=10m
val fuzzTargets = listOf("compileAnyExpression", "queryAnyDocument", "matchAnyRegularExpression", "parseAnyJson")
val fuzzDuration = providers.gradleProperty("fuzzDuration").orElse("15s")
val fuzzTasks = fuzzTargets.map { target ->
    tasks.register<Test>("fuzz" + target.replaceFirstChar { it.uppercase() }) {
        description = "Fuzzes JsonPathFuzzTest.$target."
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }
        filter {
            includeTestsMatching("io.github.treetrail.jsonpath.JsonPathFuzzTest.$target")
        }
        environment("JAZZER_FUZZ", "1")
        systemProperty("jazzer.max_duration", fuzzDuration.get())
        // Findings are written next to the build so that CI can upload them.
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

// Differential testing against Python's jsonpath-rfc9535; see scripts/differential/README.md.
//   ./gradlew :jsonpath-core:generateDifferentialCases -PdifferentialSeed=1 -PdifferentialOut=build/differential/cases.json
tasks.register<JavaExec>("generateDifferentialCases") {
    description = "Writes random documents and queries for the differential test."
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "io.github.treetrail.jsonpath.DifferentialCases"
    args = listOf(
        providers.gradleProperty("differentialOut").orElse(layout.buildDirectory.file("differential/cases.json").get().asFile.path).get(),
        providers.gradleProperty("differentialSeed").orElse("9535").get(),
        providers.gradleProperty("differentialDocuments").orElse("200").get(),
        providers.gradleProperty("differentialQueries").orElse("10").get(),
    )
}
tasks.withType<Test>().configureEach {
    // A freshly generated reference file for DifferentialTest instead of the committed one, for checking a
    // regenerated corpus before replacing it; see scripts/differential/README.md.
    providers.gradleProperty("differentialExpected").orNull?.let { systemProperty("treetrail.differential.expected", it) }
}

// Mutation testing (PIT): changes the core's bytecode in small ways and checks that some test fails for each
// change. Too slow for every build, so it runs in its own task, every night and on request:
//   ./gradlew :jsonpath-core:pitest
// The report is in build/reports/pitest. The task fails if the mutation score drops below the threshold.
// PIT runs from its command line: the Gradle plugin uses Gradle API that is deprecated.
val pitestTool = configurations.dependencyScope("pitestTool")
val pitestClasspath = configurations.resolvable("pitestClasspath") {
    extendsFrom(pitestTool.get())
}
dependencies {
    "pitestTool"(libs.pitest.command.line)
    "pitestTool"(libs.pitest.junit5.plugin)
}
tasks.register<JavaExec>("pitest") {
    description = "Runs mutation testing (PIT) on the core and fails below the mutation threshold."
    group = "verification"
    val testClasspath = sourceSets.test.get().runtimeClasspath
    val mainClasses = sourceSets.main.get().output.classesDirs
    val sourceDirs = sourceSets.main.get().java.sourceDirectories
    val reportDir = layout.buildDirectory.dir("reports/pitest")
    val classpathFile = layout.buildDirectory.file("pitest/classpath.txt")
    val threads = Runtime.getRuntime().availableProcessors()
    // PIT runs on its own class path and loads the code and tests from classpathFile; it mutates mainClasses.
    classpath = pitestClasspath.get()
    mainClass = "org.pitest.mutationtest.commandline.MutationCoverageReport"
    inputs.files(testClasspath, mainClasses)
    outputs.dir(reportDir)
    doFirst {
        classpathFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText((testClasspath.files + mainClasses.files).joinToString("\n"))
        }
    }
    argumentProviders.add(CommandLineArgumentProvider {
        listOf(
            "--classPathFile", classpathFile.get().asFile.path,
            "--includeLaunchClasspath=false",
            "--mutableCodePaths", mainClasses.files.joinToString(","),
            "--reportDir", reportDir.get().asFile.path,
            "--targetClasses", "io.github.treetrail.jsonpath.*",
            "--targetTests", "io.github.treetrail.jsonpath.*",
            // Fuzzing replays and the concurrency stress test add run time but no mutation coverage of their own.
            "--excludedTestClasses",
            "io.github.treetrail.jsonpath.JsonPathFuzzTest,io.github.treetrail.jsonpath.ConcurrencyTest",
            "--sourceDirs", sourceDirs.files.joinToString(","),
            "--threads", threads.toString(),
            "--verbosity", "NO_SPINNER",
            "--outputFormats", "HTML,XML",
            "--timestampedReports=false",
            // 87 % when it was introduced (1,399 mutations); raise it when the score has grown.
            "--mutationThreshold", "85",
        )
    })
}
