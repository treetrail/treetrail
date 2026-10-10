// The modules published to Maven Central: license files in every jar, a CycloneDX SBOM, the API compatibility
// check against the last release, and the POM.

import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import java.time.Instant
import org.cyclonedx.gradle.CyclonedxDirectTask

plugins {
    id("treetrail.library-conventions")
    id("org.cyclonedx.bom")
    id("com.vanniktech.maven.publish")
}

val libs = versionCatalogs.named("libs")
val treetrail = extensions.getByType<TreetrailExtension>()

/**
 * The build's timestamp in seconds since the epoch: SOURCE_DATE_EPOCH if set, otherwise the commit time of
 * HEAD. Used where an output must contain a date, so that two builds of the same commit are identical.
 */
val sourceDateEpoch: Provider<String> = providers.environmentVariable("SOURCE_DATE_EPOCH")
    .orElse(providers.exec { commandLine("git", "log", "-1", "--format=%ct") }.standardOutput.asText.map { it.trim() })

// Every published jar (classes, sources, javadoc) carries the license and the notice. The javadoc jar is the
// publishing plugin's own task type.
val legalFiles = listOf("LICENSE", "NOTICE").map { rootProject.layout.projectDirectory.file(it) }
tasks.withType<Jar>().configureEach {
    metaInf { from(legalFiles) }
}
tasks.withType<com.vanniktech.maven.publish.tasks.JavadocJar>().configureEach {
    metaInf { from(legalFiles) }
}

// A CycloneDX SBOM of the runtime dependencies, published next to the jar (classifier cyclonedx).
val sbom = tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
    includeConfigs.set(listOf("runtimeClasspath"))
    includeBomSerialNumber.set(false)
    xmlOutput.unsetConvention()
    jsonOutput.set(layout.buildDirectory.file("sbom/${project.name}-cyclonedx.json"))

    // The plugin always writes the current time as metadata.timestamp and has no option for it.
    // Replace it with the commit time, so that the SBOM is reproducible like the jars.
    inputs.property("sourceDateEpoch", sourceDateEpoch)
    val sbomFile = jsonOutput
    val timestamp = sourceDateEpoch.map { Instant.ofEpochSecond(it.toLong()).toString() }
    doLast {
        val file = sbomFile.get().asFile
        val json = file.readText()
        val pattern = Regex("""("metadata" : \{\s*"timestamp" : ")[^"]*(")""")
        check(pattern.containsMatchIn(json)) { "No metadata.timestamp found in $file" }
        file.writeText(json.replaceFirst(pattern, "$1${timestamp.get()}$2"))
    }
}

// API compatibility: the module's public API must stay binary and source compatible with the release in
// `treetrail.apiBaselineVersion` (gradle.properties), apart from `treetrail.acceptedApiChanges`. The internal
// package is not exported and not compared. Modules listed in `treetrail.unreleasedModules` are not in that
// release yet and are skipped. See docs/releasing.md.
val apiBaselineVersion = providers.gradleProperty("treetrail.apiBaselineVersion").get()
val unreleasedModules = providers.gradleProperty("treetrail.unreleasedModules").getOrElse("")
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
if (name !in unreleasedModules) {
    val moduleName = name
    val runtimeAttributes = configurations["runtimeClasspath"].attributes

    // japicmp, which compares two versions of a jar.
    val japicmp = configurations.dependencyScope("japicmp")
    val japicmpClasspath = configurations.resolvable("japicmpClasspath") {
        extendsFrom(japicmp.get())
        attributes {
            for (key in runtimeAttributes.keySet()) {
                @Suppress("UNCHECKED_CAST")
                attribute(key as Attribute<Any>, runtimeAttributes.getAttribute(key)!!)
            }
        }
    }
    dependencies { "japicmp"(libs.findLibrary("japicmp").get()) }

    // The released module, in a detached configuration: its root has its own identity, so the project can depend
    // on an older version of itself.
    val baseline = configurations.detachedConfiguration(
        project.dependencies.create("io.github.treetrail:$moduleName:$apiBaselineVersion"),
    ).apply {
        attributes {
            for (key in runtimeAttributes.keySet()) {
                @Suppress("UNCHECKED_CAST")
                attribute(key as Attribute<Any>, runtimeAttributes.getAttribute(key)!!)
            }
        }
    }
    val baselineFiles = baseline.incoming.files
    val baselineJarName = "$moduleName-$apiBaselineVersion.jar"
    val compileClasspath = configurations["compileClasspath"].incoming.files
    val newJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    val report = layout.buildDirectory.file("reports/api-compatibility/$moduleName.html")
    val excludes = treetrail.acceptedApiChanges.map { (listOf("io.github.treetrail.jsonpath.internal") + it).joinToString(";") }
    val apiCompatibility = tasks.register<JavaExec>("apiCompatibility") {
        description = "Checks that the public API is compatible with $moduleName $apiBaselineVersion."
        group = "verification"
        classpath = files(japicmpClasspath)
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
                "--exclude", excludes.get(),
                "--only-modified",
                "--error-on-binary-incompatibility",
                "--error-on-source-incompatibility",
                "--html-file", report.get().asFile.path,
            )
        })
    }
    tasks.named("check") { dependsOn(apiCompatibility) }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(sbom.flatMap { it.jsonOutput }) {
            classifier = "cyclonedx"
            extension = "json"
        }
    }
}

mavenPublishing {
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
