description = "JSONPath (RFC 9535) assertions for Spring MockMvc and WebTestClient, a replacement for Spring's Jayway-based jsonPath(...) matchers."

dependencies {
    api(project(":jsonpath-core"))
    // Every user of these matchers already has spring-test (and, for WebTestClient, spring-webflux) with
    // their own Spring version; the module works with Spring Framework 6 and 7 and imposes neither.
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

// The test runtime class path with Spring Framework 6 (Spring Boot 3) instead of 7.
val spring6TestRuntimeClasspath = configurations.resolvable("spring6TestRuntimeClasspath") {
    extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get())
    val reference = configurations.testRuntimeClasspath.get().attributes
    attributes {
        for (key in reference.keySet()) {
            @Suppress("UNCHECKED_CAST")
            attribute(key as Attribute<Any>, reference.getAttribute(key)!!)
        }
    }
    resolutionStrategy.eachDependency {
        if (requested.group == "org.springframework") {
            useVersion(libs.versions.spring6.get())
            because("tests the matchers with Spring Framework 6")
        }
    }
}

val testSpring6 by tasks.registering(Test::class) {
    description = "Runs the tests against Spring Framework ${libs.versions.spring6.get()}."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().output + sourceSets.main.get().output + files(spring6TestRuntimeClasspath)
}

tasks.check {
    dependsOn(testSpring6)
}

tasks.jar {
    manifest {
        // Spring's jars are automatic modules, so this module stays on the class path or is automatic too.
        attributes("Automatic-Module-Name" to "io.github.treetrail.jsonpath.spring")
    }
}
