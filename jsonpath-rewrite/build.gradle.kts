dependencies {
    implementation(project(":jsonpath-core"))
    implementation(project(":jsonpath-migration"))
    implementation(platform(libs.rewrite.bom))
    implementation(libs.rewrite.java)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.rewrite.test)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.rewrite.java.jdk25)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.github.treetrail.jsonpath.rewrite")
    }
}
