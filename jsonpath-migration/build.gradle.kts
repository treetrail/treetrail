dependencies {
    api(project(":jsonpath-core"))
    // Users bring their own Jayway version; 3.0.0 is what we test against.
    api(libs.jayway.jsonpath)

    testImplementation(testFixtures(project(":jsonpath-core")))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.jackson2.databind)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("report")
    }
}

// Runs the Compliance Test Suite through Jayway JsonPath and writes build/reports/jayway-vs-rfc9535.md.
val jaywayReport by tasks.registering(Test::class) {
    description = "Compares Jayway JsonPath with RFC 9535 on the Compliance Test Suite."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("report")
    }
    outputs.upToDateWhen { false }
}

tasks.jar {
    manifest {
        // Jayway JsonPath is an automatic module, so this module stays on the class path or is automatic too.
        attributes("Automatic-Module-Name" to "io.github.treetrail.jsonpath.migration")
    }
}
