import com.github.javaparser.utils.ParserCollectionStrategy
import java.nio.file.Path

fun main(args: Array<String>) {
    println("Hello, ${args.joinToString(prefix = "[", postfix = "]")}")

    if (args.isEmpty())
        return

    val projectRoot = ParserCollectionStrategy().collect(Path.of(args[0]))
    println(projectRoot.sourceRoots.joinToString("\n"))
    println()

    val pom = projectRoot.root.resolve("pom.xml")
    val project = PomProject(pom)
    fun printModuleTests(module: PomProject) {
        println("${module.name}: ${module.allTests?.joinToString("\n\t")}")
    }
    printModuleTests(project)
    for (module in project.recursiveModules) {
        printModuleTests(module)
    }
}
