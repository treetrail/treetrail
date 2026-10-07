description = "JSONPath (RFC 9535) assertions for Spring MockMvc and WebTestClient, a replacement for Spring's Jayway-based jsonPath(...) matchers."

dependencies {
    api(project(":jsonpath-core"))
    // Every user of these matchers already has spring-test (and, for WebTestClient, spring-webflux) with
    // their own Spring version; the module imposes none. Tested with Spring Framework 7. A one-time run
    // with 6.2.19 passed too, but the 6.x line has no public security fixes, so CI does not test it.
    compileOnly(platform(libs.spring.bom))
    compileOnly(libs.spring.test)
    compileOnly(libs.jakarta.servlet.api)

    testImplementation(platform(libs.spring.bom))
    testImplementation(libs.spring.test)
    testImplementation(libs.spring.webmvc)
    testImplementation(libs.spring.webflux)
    testImplementation(libs.jakarta.servlet.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    manifest {
        // Spring's jars are automatic modules, so this module stays on the class path or is automatic too.
        attributes("Automatic-Module-Name" to "io.github.treetrail.jsonpath.spring")
    }
}
