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
    private static final String OPTION_PACKAGE_NAME = "invoke.packageName";
    private static final String OPTION_CLIENT_SUFFIX = "invoke.clientSuffix";

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);
        InvokeExtension extension = project.getExtensions().create(
            "invoke", InvokeExtension.class
        );
        extension.getClientSuffix().convention("Client");

        project.getPluginManager().withPlugin("java", (_) ->
            project.getDependencies().add("annotationProcessor", processorDependency(project))
        );

        // configureEach fires when the task is realized during task-graph calculation, which is after the consuming
        // build's invoke { } block has run, so both properties hold real values here. Calling get() rather than
        // concatenating the Property is the whole fix: Gradle's Property.toString() is a debug label such as
        // "extension 'invoke' property 'packageName'", which is what the processor used to receive as the package name.
        //
        // The args go into getCompilerArgs() so they take part in the compile task's cache key. A lazy
        // CommandLineArgumentProvider would read the values just as correctly but would not invalidate correctly:
        // Gradle fingerprints such a provider by its implementation class, so flipping clientSuffix would replay a
        // stale generated source.
        project.getTasks().withType(JavaCompile.class).configureEach(task -> {
            task.getOptions().getCompilerArgs().add(
                "-A" + OPTION_PACKAGE_NAME + "=" + extension.getPackageName().get()
            );
            task.getOptions().getCompilerArgs().add(
                "-A" + OPTION_CLIENT_SUFFIX + "=" + extension.getClientSuffix().get()
            );
        });

        project.afterEvaluate(_ -> {
            if (!extension.getPackageName().isPresent() || extension.getPackageName().get().isBlank()) {
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