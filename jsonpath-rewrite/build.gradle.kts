description = "OpenRewrite recipe that finds Jayway JsonPath expressions in a code base and assesses each one against RFC 9535."

dependencies {
    implementation(project(":jsonpath-core"))
    implementation(project(":jsonpath-migration"))
    // The recipe asks Jayway JsonPath whether a path is definite; jsonpath-migration leaves Jayway to its users.
    implementation(libs.jayway.jsonpath)
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.java)

    // The OpenRewrite BOM pins Jackson 2.21.6 and rewrite-core pulls Micrometer 1.9.17, both with known
    // vulnerabilities (GitHub advisories for jackson-core/-databind <= 2.21.6, micrometer-core <= 1.9.17).
    // Raise them until OpenRewrite ships fixed versions.
    implementation(platform(libs.jackson2.bom))
    constraints {
        implementation(libs.micrometer.core) {
            because("micrometer-core <= 1.9.17 has a known high-severity vulnerability")
        }
    }

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.rewrite.test)
    testImplementation(libs.rewrite.kotlin)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.rewrite.java.jdk25)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.treetrail.jsonpath.rewrite")
    }
}
