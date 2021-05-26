import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.codehaus.plexus.util.xml.Xpp3Dom
import java.nio.file.*
import java.util.stream.Collectors

const val SUREFIRE_ARTIFACT: String = "org.apache.maven.plugins:maven-surefire-plugin"
val DEFAULT_SUREFIRE_INCLUDES = listOf(
    "**/Test*.java",
    "**/*Test.java",
    "**/*Tests.java",
    "**/*TestCase.java",
)

val MAVEN_PROPERTIES = mapOf(
    "basedir" to { proj: PomProject -> proj.pom.parent.toString() },
    "project.basedir" to { proj: PomProject -> proj.pom.parent.toString() },
)

class PomProject(val pom: Path, private val parent: PomProject? = null) {
    inner class InvalidPomException(message: String, cause: Throwable? = null) :
        RuntimeException("Invalid pom: $pom: $message", cause)

    val name get() = pom.parent.fileName.toString()

    private val model = pom.toFile().inputStream().use(MavenXpp3Reader()::read)
    val modules = model.modules.orEmpty().map { it.toProjectPath() }.map {
        val modulePom = if (Files.isDirectory(it))
            it.resolve("pom.xml")
        else if (it.toString().endsWith(".xml") && Files.isRegularFile(it))
            it
        else
            throw InvalidPomException("invalid module specification: $it")
        PomProject(modulePom, this)
    }

    val recursiveModules: List<PomProject> get() =
        modules.flatMap { it.recursiveModules } + modules

    private fun Path.existsOrNull(): Path? = if (Files.exists(this)) this else null

    private fun String.toProjectPath(): Path = try {
        this@PomProject.pom.resolveSibling(this)
    } catch (e: InvalidPathException) {
        throw InvalidPomException("invalid path at $pom", e)
    }

    private fun String.tryToProjectPath(): Path? = try {
        this@PomProject.pom.resolveSibling(this)
    } catch (e: InvalidPathException) {
        null
    }

    private fun String.substituteMavenProps(): String {
        var result = this
        MAVEN_PROPERTIES.forEach { (varName, subst) ->
            result = result.replace("\${$varName}", subst(this@PomProject))
        }
        return result
    }

    val sourceDirectory: Path?
        get() = model.build.sourceDirectory?.let { it.substituteMavenProps().tryToProjectPath()?.existsOrNull() }
            ?: "src/main/java".tryToProjectPath()?.existsOrNull()
    val testSourceDirectory: Path?
        get() = model.build.testSourceDirectory?.let { it.substituteMavenProps().tryToProjectPath()?.existsOrNull() }
            ?: "src/test/java".tryToProjectPath()?.existsOrNull()

    private val parentSurefireConfig by lazy {
        model.build.pluginManagement?.pluginsAsMap?.get(SUREFIRE_ARTIFACT)?.configuration as? Xpp3Dom
    }
    private val surefireConfig by lazy {
        model.build?.pluginsAsMap?.get(SUREFIRE_ARTIFACT)?.configuration as? Xpp3Dom
            ?: parent?.parentSurefireConfig
    }

    val surefireIncludes: List<String> by lazy {
        surefireConfig?.getChildren("test")?.map { it.value }.orEmpty().toList().ifEmpty {
            surefireConfig?.getChild("includes")?.getChildren("include")?.map { it.value }
                ?: parent?.surefireIncludes ?: DEFAULT_SUREFIRE_INCLUDES
        }
    }
    val surefireExcludes: List<String> by lazy {
        surefireConfig?.getChild("excludes")?.getChildren("exclude")?.map { it.value }
            ?: parent?.surefireExcludes.orEmpty()
    }

    val surefireIncludeMatchers by lazy { surefireIncludes.map { FileSystems.getDefault().getPathMatcher("glob:$it") } }
    val surefireExcludeMatchers by lazy { surefireExcludes.map { FileSystems.getDefault().getPathMatcher("glob:$it") } }

    private fun matchFile(p: Path, patterns: List<PathMatcher>): Boolean = patterns.any {
        it.matches(p)
    }

    private fun matchSurefireTest(p: Path): Boolean =
        matchFile(p, surefireIncludeMatchers) && !matchFile(p, surefireExcludeMatchers)

    val allTests: List<Path>? by lazy {
        testSourceDirectory?.let { testSourceDir ->
            Files.find(testSourceDir, Int.MAX_VALUE, { p, attr -> !attr.isDirectory && matchSurefireTest(testSourceDir.relativize(p)) })
                .collect(Collectors.toList())
        }
    }
}
