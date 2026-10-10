import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.cyclonedx.gradle.CyclonedxDirectTask

buildscript {
    dependencies {
        // The CycloneDX plugin pulls Jackson 2.22.1 onto the build class path (GHSA-p6pp-m3f8-5c89,
        // GHSA-7hhh-6rmp-j9qf, fixed in 2.22.3). Build time only; remove when the plugin ships a fixed one.
        classpath(platform(libs.jackson2.bom))
    }
}

plugins {
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.cyclonedx) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
}

/** The modules published to Maven Central; jsonpath-benchmarks is a build tool. */
val publishedModules = setOf(
    "jsonpath-core", "jsonpath-jackson2", "jsonpath-jackson3", "jsonpath-gson", "jsonpath-jsonp",
    "jsonpath-migration", "jsonpath-rewrite", "jsonpath-assertj", "jsonpath-spring-test", "jsonpath-model-testkit",
)

/**
 * The build's timestamp in seconds since the epoch: SOURCE_DATE_EPOCH if set, otherwise the commit time of
 * HEAD. Used where an output must contain a date, so that two builds of the same commit are identical.
 */
val errorproneCore = libs.errorprone.core
val palantirJavaFormatVersion = libs.versions.palantir.java.format.get()
val nullaway = libs.nullaway
val jspecify = libs.jspecify

val sourceDateEpoch: Provider<String> = providers.environmentVariable("SOURCE_DATE_EPOCH")
    .orElse(providers.exec { commandLine("git", "log", "-1", "--format=%ct") }.standardOutput.asText.map { it.trim() })

/**
 * The release the public API is compared with (see `apiCompatibility` below). After each release, set it to the
 * new version and clear [acceptedApiChanges].
 */
val apiBaselineVersion = "0.2.0"

/**
 * Intended incompatible API changes since [apiBaselineVersion], in japicmp's exclude syntax
 * (`package.Class#member(ParameterType)`). Before 1.0 a minor release may still break the API; every entry
 * needs a note in CHANGELOG.md.
 */
val acceptedApiChanges = listOf<String>()

/** Published modules that are not in the release named by [apiBaselineVersion] yet; cleared after a release. */
val unreleasedModules = setOf("jsonpath-model-testkit")

// Gradle's JVM resolution rules for the configurations below, which the root project resolves itself (for
// example platform dependencies in the released modules' metadata).
apply(plugin = "jvm-ecosystem")

/** japicmp, which compares two versions of a jar. */
val japicmp = configurations.create("japicmp") {
    isCanBeConsumed = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(TargetJvmEnvironment.STANDARD_JVM))
    }
}
dependencies {
    japicmp(libs.japicmp)
}

allprojects {
    group = "io.github.treetrail"
    // Set by the release workflow from the Git tag (vX.Y.Z -> X.Y.Z).
    version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-SNAPSHOT")
}

val jacocoVersion = libs.versions.jacoco.get()

/**
 * Minimum line and branch coverage per module, a little below the coverage when it was last raised. Raise a
 * module's values when its coverage has grown, so that it does not quietly fall back.
 */
val coverageMinimum = mapOf(
    "jsonpath-core" to (0.96 to 0.91),
    "jsonpath-jackson2" to (0.90 to 0.87),
    "jsonpath-jackson3" to (0.90 to 0.87),
    "jsonpath-gson" to (0.94 to 0.75),
    "jsonpath-jsonp" to (0.84 to 0.87),
    "jsonpath-assertj" to (0.89 to 0.74),
    "jsonpath-spring-test" to (0.95 to 0.74),
    "jsonpath-migration" to (0.73 to 0.54),
    "jsonpath-rewrite" to (0.88 to 0.76),
    "jsonpath-model-testkit" to (0.92 to 0.82),
)

// Shared setup for all library modules.
subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    // Compile with the JDK 25 toolchain, but stay usable on Java 17.
    tasks.withType<JavaCompile>().configureEach {
        options.release = 17
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    // One formatting for all Java sources, so that reviews need no style comments: `spotlessApply` formats,
    // and `check` fails on unformatted code.
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/*/java/**/*.java")
            palantirJavaFormat(palantirJavaFormatVersion)
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    // Error Prone checks every compilation; with -Werror its warnings fail the build too.
    apply(plugin = "net.ltgt.errorprone")
    dependencies { "errorprone"(errorproneCore) }

    // Null safety: every package is @NullMarked (JSpecify), so types are non-null unless annotated @Nullable,
    // and NullAway checks the main sources against that. JSpecify is a compile-time dependency only
    // (`requires static` in the module descriptors); Gradle consumers get it on their compile class path,
    // so that Kotlin and other tools see the nullness of the API.
    if (name != "jsonpath-benchmarks") {
        dependencies {
            "compileOnlyApi"(jspecify)
            "testCompileOnly"(jspecify)
            "errorprone"(nullaway)
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
    }
    tasks.withType<JavaCompile>().configureEach {
        options.errorprone {
            disableWarningsInGeneratedCode = true
            // JMH's generated benchmark classes carry no @Generated annotation.
            excludedPaths = ".*/build/generated/.*"
        }
    }

    tasks.withType<Javadoc>().configureEach {
        // Each module only exports its API package, so javadoc only documents that package.
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
        // No generation date and a fixed language (not the system locale), so that the javadoc jar is
        // reproducible on every machine.
        (options as StandardJavadocDocletOptions).addBooleanOption("notimestamp", true)
        options.locale = "en"
        options.jFlags("-Duser.language=en", "-Duser.country=US")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // The library targets Java 17, so its tests also run on Java 17 and 21; `test` itself runs on 25.
    // `check` (and so `build`) includes these runs. The OpenRewrite recipe needs JDK 25 to parse Java 25
    // sources, and the benchmarks have no tests.
    if (name != "jsonpath-rewrite" && name != "jsonpath-benchmarks") {
        val javaToolchains = extensions.getByType<JavaToolchainService>()
        val testSourceSet = extensions.getByType<SourceSetContainer>()["test"]
        for (version in listOf(17, 21)) {
            val testOnJava = tasks.register<Test>("testJava$version") {
                description = "Runs the unit tests on Java $version."
                group = "verification"
                testClassesDirs = testSourceSet.output.classesDirs
                classpath = testSourceSet.runtimeClasspath
                javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(version) }
                useJUnitPlatform {
                    // Like `test` in jsonpath-migration: the Jayway report is generated on request only.
                    excludeTags("report")
                }
            }
            tasks.named("check") { dependsOn(testOnJava) }
        }
    }

    // Test coverage of each module by its own tests (`test`, on Java 25): an HTML and XML report after every
    // test run, and `check` fails if a module's line or branch coverage falls below its minimum.
    if (name != "jsonpath-benchmarks") {
        apply(plugin = "jacoco")
        extensions.configure<JacocoPluginExtension> {
            toolVersion = jacocoVersion
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
        val minimum = coverageMinimum.getValue(name)
        val coverageCheck = tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        this.minimum = minimum.first.toBigDecimal()
                    }
                    limit {
                        counter = "BRANCH"
                        this.minimum = minimum.second.toBigDecimal()
                    }
                }
            }
        }
        tasks.named("check") { dependsOn(coverageCheck) }
    }

    if (name in publishedModules) {
        // Every published jar (classes, sources, javadoc) carries the license and the notice. The javadoc jar
        // is the publishing plugin's own task type.
        val legalFiles = listOf("LICENSE", "NOTICE").map { rootProject.layout.projectDirectory.file(it) }
        tasks.withType<Jar>().configureEach {
            metaInf { from(legalFiles) }
        }
        tasks.withType<com.vanniktech.maven.publish.tasks.JavadocJar>().configureEach {
            metaInf { from(legalFiles) }
        }

        // A CycloneDX SBOM of the runtime dependencies, published next to the jar (classifier cyclonedx).
        apply(plugin = "org.cyclonedx.bom")
        val sbom = tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
            includeConfigs.set(listOf("runtimeClasspath"))
            includeBomSerialNumber.set(false)
            xmlOutput.unsetConvention()
            jsonOutput.set(layout.buildDirectory.file("sbom/${project.name}-cyclonedx.json"))

            // The plugin always writes the current time as metadata.timestamp and has no option for it.
            // Replace it with the commit time, so that the SBOM is reproducible like the jars.
            inputs.property("sourceDateEpoch", sourceDateEpoch)
            val sbomFile = jsonOutput
            val timestamp = sourceDateEpoch.map { java.time.Instant.ofEpochSecond(it.toLong()).toString() }
            doLast {
                val file = sbomFile.get().asFile
                val json = file.readText()
                val pattern = Regex("""("metadata" : \{\s*"timestamp" : ")[^"]*(")""")
                check(pattern.containsMatchIn(json)) { "No metadata.timestamp found in $file" }
                file.writeText(json.replaceFirst(pattern, "$1${timestamp.get()}$2"))
            }
        }

        if (name !in unreleasedModules) {
            // API compatibility: the module's public API must stay binary and source compatible with the last
            // release, apart from acceptedApiChanges. The internal package is not exported and not compared.
            // The released module is resolved in the root project: a project cannot depend on an older version of
            // itself.
            val moduleName = name
            val baseline = rootProject.configurations.create("apiBaseline-$moduleName") {
                isCanBeConsumed = false
                val reference = configurations["runtimeClasspath"].attributes
                attributes {
                    for (key in reference.keySet()) {
                        @Suppress("UNCHECKED_CAST")
                        attribute(key as Attribute<Any>, reference.getAttribute(key)!!)
                    }
                }
            }
            rootProject.dependencies.add(baseline.name, "io.github.treetrail:$moduleName:$apiBaselineVersion")
            val baselineFiles = baseline.incoming.files
            val baselineJarName = "$moduleName-$apiBaselineVersion.jar"
            val compileClasspath = configurations["compileClasspath"].incoming.files
            val newJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
            val report = layout.buildDirectory.file("reports/api-compatibility/$moduleName.html")
            val excludes = (listOf("io.github.treetrail.jsonpath.internal") + acceptedApiChanges).joinToString(";")
            val apiCompatibility = tasks.register<JavaExec>("apiCompatibility") {
                description = "Checks that the public API is compatible with $moduleName $apiBaselineVersion."
                group = "verification"
                classpath = japicmp
                mainClass = "japicmp.JApiCmp"
                inputs.files(baselineFiles)
                inputs.file(newJar)
                inputs.files(compileClasspath)
                inputs.property("excludes", excludes)
                outputs.file(report)
                argumentProviders.add(CommandLineArgumentProvider {
                    val (baselineJar, baselineClasspath) = baselineFiles.files.partition { it.name == baselineJarName }
                    listOf(
                        "--old", baselineJar.single().path,
                        "--new", newJar.get().asFile.path,
                        // compileOnly dependencies such as Spring are not in the released POM, so the old class path
                        // also gets the current compile class path, after the released dependencies.
                        "--old-classpath", (baselineClasspath + compileClasspath.files).joinToString(File.pathSeparator),
                        "--new-classpath", compileClasspath.asPath,
                        "--exclude", excludes,
                        "--only-modified",
                        "--error-on-binary-incompatibility",
                        "--error-on-source-incompatibility",
                        "--html-file", report.get().asFile.path,
                    )
                })
            }
            tasks.named("check") { dependsOn(apiCompatibility) }
        }

        apply(plugin = "com.vanniktech.maven.publish")
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                artifact(sbom.flatMap { it.jsonOutput }) {
                    classifier = "cyclonedx"
                    extension = "json"
                }
            }
        }
        extensions.configure<MavenPublishBaseExtension> {
            configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))
            publishToMavenCentral()
            signAllPublications()
            pom {
                name.set(project.name)
                description.set(provider { project.description })
                url.set("https://github.com/treetrail/treetrail")
                inceptionYear.set("2026")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("christoph-sens")
                        name.set("Christoph Sens")
                        url.set("https://github.com/christoph-sens")
                    }
                }
                scm {
                    url.set("https://github.com/treetrail/treetrail")
                    connection.set("scm:git:https://github.com/treetrail/treetrail.git")
                    developerConnection.set("scm:git:ssh://git@github.com/treetrail/treetrail.git")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/treetrail/treetrail/issues")
                }
            }
        }
    }
}
