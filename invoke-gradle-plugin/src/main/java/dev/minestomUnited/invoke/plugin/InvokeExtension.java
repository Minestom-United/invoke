package dev.minestomUnited.invoke.plugin;

import org.gradle.api.provider.Property;

/**
 * The `invoke { }` block in a consuming build. packageName is required and controls where generated clients land;
 * clientSuffix renames them when the default "Client" clashes with an existing class.
 */

public abstract class InvokeExtension {

    /**
     * REQUIRED
     * Package for generated clients, e.g. "com.example.shop".
     */
    public abstract Property<String> getPackageName();

    /**
     * Suffix appended to the service name for the client name. "Client" by default.
     */
    public abstract Property<String> getClientSuffix();
}
