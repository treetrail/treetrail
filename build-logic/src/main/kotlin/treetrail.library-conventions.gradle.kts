// The library modules: null safety, tests on Java 17, 21 and 25, and coverage minimums.

import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("treetrail.java-conventions")
    jacoco
}

val libs = versionCatalogs.named("libs")
val treetrail = extensions.create<TreetrailExtension>("treetrail")

// Null safety: every package is @NullMarked (JSpecify), so types are non-null unless annotated @Nullable,
// and NullAway checks the main sources against that. JSpecify is a compile-time dependency only
// (`requires static` in the module descriptors); Gradle consumers get it on their compile class path,
// so that Kotlin and other tools see the nullness of the API.
dependencies {
    compileOnlyApi(libs.findLibrary("jspecify").get())
    testCompileOnly(libs.findLibrary("jspecify").get())
    errorprone(libs.findLibrary("nullaway").get())
}
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        if (name == "compileJava") {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:OnlyNullMarked", "true")
            // Checks nullness of type arguments too, e.g. JavaObjectModel as JsonModel<@Nullable Object>.
            option("NullAway:JSpecifyMode", "true")
        } else {
            disable("NullAway")
        }
    }
}

// The library targets Java 17, so its tests also run on Java 17 and 21; `test` itself runs on 25. `check` (and
// so `build`) includes these runs unless the module sets `treetrail.testOnOlderJdks = false`.
val olderJdkTests = listOf(17, 21).map { version ->
    tasks.register<Test>("testJava$version") {
        description = "Runs the unit tests on Java $version."
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(version) }
        useJUnitPlatform {
            // Like `test` in jsonpath-migration: the Jayway report is generated on request only.
            excludeTags("report")
        }
    }
}
tasks.named("check") {
    dependsOn(treetrail.testOnOlderJdks.map { if (it) olderJdkTests else emptyList() })
}

// Test coverage of each module by its own tests (`test`, on Java 25): an HTML and XML report after every
// test run, and `check` fails if the line or branch coverage falls below `treetrail.coverage(...)`.
jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}
val coverageReport = tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
}
tasks.named<Test>("test") { finalizedBy(coverageReport) }
// Only `test` counts; the runs on Java 17 and 21 and the fuzzing tasks run without the agent.
tasks.withType<Test>().configureEach {
    if (name != "test") {
        extensions.configure<JacocoTaskExtension> { isEnabled = false }
    }
}
val coverageCheck = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    // Locals, so that the action captures no script object (configuration cache).
    val coverageSet = treetrail.coverageSet
    val modulePath = project.path
    doFirst {
        check(coverageSet.get()) { "$modulePath sets no coverage minimum: add treetrail { coverage(line, branch) }" }
    }
}
tasks.named("check") { dependsOn(coverageCheck) }
