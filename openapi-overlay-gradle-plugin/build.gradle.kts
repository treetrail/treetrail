plugins {
    id("treetrail.library-conventions")
    `java-gradle-plugin`
}

description = "Gradle plugin that applies OpenAPI Overlays to OpenAPI descriptions, for example before a code generator."

// Not published yet: neither to Maven Central nor to the Gradle Plugin Portal (that needs an explicit decision).
treetrail {
    // Only the unit tests count: the task and worker action run in the Gradle daemons of the functional tests,
    // outside the coverage agent. The functional tests cover them.
    coverage(line = 0.70, branch = 0.75)
    // The functional tests start their own Gradle builds; the unit tests need no other JDKs.
    testOnOlderJdks = false
}

gradlePlugin {
    plugins {
        register("openApiOverlay") {
            id = "io.github.treetrail.openapi-overlay"
            implementationClass = "io.github.treetrail.openapi.overlay.gradle.OpenApiOverlayPlugin"
            displayName = "OpenAPI Overlay"
            description = project.description
            tags = listOf("openapi", "overlay", "jsonpath", "rfc9535", "codegen")
        }
    }
}

dependencies {
    // The overlay library runs in an isolated class loader with its own class path (openApiOverlayRuntime), so
    // that its Jackson does not meet the Jackson of other plugins, such as openapi-generator's.
    compileOnly(project(":openapi-overlay"))

    testImplementation(project(":openapi-overlay"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The version of the overlay library the plugin resolves: the plugin's own.
val versionResource = tasks.register<WriteProperties>("versionResource") {
    destinationFile = layout.buildDirectory.file("generated/version/io/github/treetrail/openapi/overlay/gradle/version.properties")
    property("version", project.version.toString())
}
sourceSets.main {
    resources.srcDir(versionResource.map { layout.buildDirectory.dir("generated/version").get() })
}

// The functional tests resolve the overlay library and its dependencies from Maven Local, where this build
// puts the current version.
tasks.test {
    dependsOn(":openapi-overlay:publishToMavenLocal", ":jsonpath-jackson2:publishToMavenLocal", ":jsonpath-core:publishToMavenLocal")
    systemProperty("treetrail.version", project.version.toString())
    systemProperty("treetrail.codegenFixtures", rootProject.layout.projectDirectory.dir("openapi-overlay/src/test/resources/codegen").asFile.path)
    // Functional tests download Gradle distributions and plugins; keep them between runs.
    systemProperty("treetrail.testKitDir", rootProject.layout.projectDirectory.dir(".gradle/testkit").asFile.path)
    // Gradle 8 cannot run on Java 25; the test on Gradle 8 runs it on Java 21.
    val java21 = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(21) }
        .map { it.metadata.installationPath.asFile.path }
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dtreetrail.java21=" + java21.get()) })
}
