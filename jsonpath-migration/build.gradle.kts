description = "Compares JSONPath expressions between Jayway JsonPath and Treetrail (RFC 9535) and reports every difference, with rewrite hints."

dependencies {
    api(project(":jsonpath-core"))
    // Users bring their own Jayway JsonPath, so that the comparison runs against the version their
    // application uses. Tested against Jayway 3.x (test) and 2.x (testJayway2).
    compileOnly(libs.jayway.jsonpath)

    testImplementation(libs.jayway.jsonpath)
    testImplementation(testFixtures(project(":jsonpath-core")))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.jackson2.databind)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Dependabot updates both catalog entries of json-path to the same version (see libs.versions.toml).
check(libs.versions.jayway.get().startsWith("3.") && libs.versions.jayway2.get().startsWith("2.")) {
    "jayway must be a Jayway JsonPath 3.x version and jayway2 a 2.x version, " +
        "but they are ${libs.versions.jayway.get()} and ${libs.versions.jayway2.get()}"
}

tasks.test {
    useJUnitPlatform {
        excludeTags("report")
    }
}

// Tests that depend on Jayway's behaviour read the version under test; the 2.x tasks below override it.
tasks.withType<Test>().configureEach {
    systemProperty("jayway.version", libs.versions.jayway.get())
}

// The test runtime class path with Jayway JsonPath 2.x instead of 3.x.
val jayway2TestRuntimeClasspath = configurations.resolvable("jayway2TestRuntimeClasspath") {
    extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get())
    val reference = configurations.testRuntimeClasspath.get().attributes
    attributes {
        for (key in reference.keySet()) {
            @Suppress("UNCHECKED_CAST")
            attribute(key as Attribute<Any>, reference.getAttribute(key)!!)
        }
    }
    resolutionStrategy.force(libs.jayway2.jsonpath.get().toString())
}


val testJayway2 = tasks.register<Test>("testJayway2") {
    description = "Runs the tests against Jayway JsonPath ${libs.versions.jayway2.get()}."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().output + sourceSets.main.get().output + files(jayway2TestRuntimeClasspath)
    useJUnitPlatform {
        excludeTags("report")
    }
    systemProperty("jayway.version", libs.versions.jayway2.get())
}

tasks.check {
    dependsOn(testJayway2)
}

// Runs the Compliance Test Suite through Jayway JsonPath and writes build/reports/jayway-<version>-vs-rfc9535.md,
// for Jayway 3.x (jaywayReport) and 2.x (jaywayReport2).
fun registerJaywayReport(name: String, version: String, runtimeClasspath: FileCollection) =
    tasks.register<Test>(name) {
        description = "Compares Jayway JsonPath $version with RFC 9535 on the Compliance Test Suite."
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().output + sourceSets.main.get().output + runtimeClasspath
        useJUnitPlatform {
            includeTags("report")
        }
        systemProperty("jayway.version", version)
        outputs.upToDateWhen { false }
    }

registerJaywayReport("jaywayReport", libs.versions.jayway.get(), files(configurations.testRuntimeClasspath))
registerJaywayReport("jaywayReport2", libs.versions.jayway2.get(), files(jayway2TestRuntimeClasspath))

tasks.jar {
    manifest {
        // Jayway JsonPath is an automatic module, so this module stays on the class path or is automatic too.
        attributes("Automatic-Module-Name" to "io.github.treetrail.jsonpath.migration")
    }
}
