import org.gradle.api.Plugin
import org.gradle.api.Project

class VericoreSharedPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.configureKotlin()
    }
}

