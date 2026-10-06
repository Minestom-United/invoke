plugins {
    id("java-gradle-plugin")
    id("net.kyori.blossom") version "2.2.0"
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "dev.minestom-united"
version = "0.0.2"

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
    withJavadocJar()
}

gradlePlugin {
    website = "https://github.com/Minestom-United/invoke"
    vcsUrl = "https://github.com/Minestom-United/invoke"
    plugins {
        create("invoke") {
            id = "dev.minestom-united.invoke"
            displayName = "Invoke codegen plugin"
            description = "Generates typed JSON-RPC clients for @InvokeService Java interfaces."
            tags.set(listOf("json-rpc", "codegen", "java"))
            implementationClass = "dev.minestomUnited.invoke.plugin.InvokePlugin"
        }
    }
}

sourceSets {
    main {
        blossom {
            javaSources {
                property("version", project.version.toString())
            }
        }
    }
}

publishing {
    repositories {
        maven {
            name = "MinestomUnitedRepository"
            url = uri(
                if (version.toString().endsWith("-SNAPSHOT"))
                    "https://repo.minestom-united.dev/snapshots"
                else "https://repo.minestom-united.dev/releases"
            )
            credentials {
                username = providers.gradleProperty("MinestomUnitedRepositoryUsername")
                    .orElse(providers.environmentVariable("REPO_USERNAME")).orNull
                password = providers.gradleProperty("MinestomUnitedRepositoryPassword")
                    .orElse(providers.environmentVariable("REPO_PASSWORD")).orNull
            }
        }
    }
}
