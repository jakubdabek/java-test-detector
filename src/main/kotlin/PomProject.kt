import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.codehaus.plexus.util.xml.Xpp3Dom
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

const val SUREFIRE_ARTIFACT: String = "org.apache.maven.plugins:maven-surefire-plugin"

class PomProject(val pom: Path, private val parent: PomProject? = null) {
    inner class InvalidPomException(message: String, cause: Throwable? = null) :
        RuntimeException("Invalid pom: $pom: $message", cause)

    private val model = pom.toFile().inputStream().use(MavenXpp3Reader()::read)
    private val modules = model.modules.orEmpty().map { it.toProjectPath() }.map {
        val modulePom = if (Files.isDirectory(it))
            it.resolve("pom.xml")
        else if (it.toString().endsWith(".xml") && Files.isRegularFile(it))
            it
        else
            throw InvalidPomException("invalid module specification: $it")
        PomProject(modulePom, this)
    }

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

    val sourceDirectory
        get() = model.build.sourceDirectory?.let { it.tryToProjectPath()?.existsOrNull() }
            ?: "src/main/java".tryToProjectPath()?.existsOrNull()!!
    val testSourceDirectory
        get() = model.build.testSourceDirectory?.let { it.tryToProjectPath()?.existsOrNull() }
            ?: "src/test/java".tryToProjectPath()?.existsOrNull()!!

    private val surefireConfig by lazy {
        model.build.pluginManagement.pluginsAsMap[SUREFIRE_ARTIFACT]?.configuration as? Xpp3Dom
    }
    val surefireIncludes: List<String> by lazy {
        surefireConfig?.getChild("includes")?.getChildren("include")?.map { it.value }
            ?: parent?.surefireIncludes.orEmpty()
    }
    val surefireExcludes: List<String> by lazy {
        surefireConfig?.getChild("excludes")?.getChildren("exclude")?.map { it.value }
            ?: parent?.surefireExcludes.orEmpty()
    }
}
