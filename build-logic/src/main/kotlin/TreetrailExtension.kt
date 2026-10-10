import javax.inject.Inject
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskContainer
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

/** Settings of a module for the convention plugins, as `treetrail { ... }` in its build file. */
abstract class TreetrailExtension @Inject constructor(private val tasks: TaskContainer) {

    /** Whether the tests also run on Java 17 and 21, as part of `check`; true unless a module needs a newer JDK. */
    abstract val testOnOlderJdks: Property<Boolean>

    /**
     * Intended incompatible changes to the module's API since the release in `treetrail.apiBaselineVersion`, in
     * japicmp's exclude syntax (`package.Class#member(ParameterType)`). Each needs a note in CHANGELOG.md; cleared
     * after a release.
     */
    abstract val acceptedApiChanges: ListProperty<String>

    /** Whether [coverage] was called; the coverage check fails otherwise. */
    abstract val coverageSet: Property<Boolean>

    init {
        testOnOlderJdks.convention(true)
        coverageSet.convention(false)
    }

    /**
     * Minimum line and branch coverage of the module by its own tests, a little below the coverage when it was
     * last raised. Raise them when the coverage has grown, so that it does not quietly fall back.
     */
    fun coverage(line: Double, branch: Double) {
        coverageSet.set(true)
        tasks.named("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java) {
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        minimum = line.toBigDecimal()
                    }
                    limit {
                        counter = "BRANCH"
                        minimum = branch.toBigDecimal()
                    }
                }
            }
        }
    }
}
