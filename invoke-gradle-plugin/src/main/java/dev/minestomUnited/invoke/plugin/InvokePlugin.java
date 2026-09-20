package dev.minestomUnited.invoke.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class InvokePlugin implements Plugin<Project> {

    private static final String PROCESSOR_PROJECT_PATH = ":invoke-processor";
    private static final String PUBLISHED_PROCESSOR = "dev.minestom-united:invoke-processor:%s";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        InvokeExtension extension = project.getExtensions().create(
            "invoke", InvokeExtension.class
        );
        extension.getClientSuffix().convention("Client");

        project.getPluginManager().withPlugin("java", (_) -> {
            project.getDependencies().add(
                "annotationProcessor",
                processorDependency(project)
            );
            project.getTasks().withType(JavaCompile.class).configureEach(task -> {
                task.getOptions().getCompilerArgs().add("-Ainvoke.packageName=" + extension.getPackageName());
                task.getOptions().getCompilerArgs().add("-Ainvoke.clientSuffix=" + extension.getClientSuffix());
            });
        });

        project.afterEvaluate(_ -> {
            if (!extension.getPackageName().isPresent()) {
                throw new GradleException("invoke { packageName } must be set");
            }
        });
    }

    private Object processorDependency(Project project) {
        Project processorProject = project.getRootProject().findProject(PROCESSOR_PROJECT_PATH);

        if (processorProject != null && project.getRootProject().findProject(":invoke-runtime") != null) {
            return processorProject;
        }
        return PUBLISHED_PROCESSOR.formatted(BuildConstants.VERSION);
    }
}
