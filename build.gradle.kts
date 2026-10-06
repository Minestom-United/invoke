import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.vanniktech.publish) apply false
}

subprojects {
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            publishToMavenCentral()
            signAllPublications()

            pom {
                name = project.name
                description =
                    "Typed JSON RPC for the JVM: plain interfaces in, HTTP plus JSON plus error mapping out."
                url = "https://github.com/Minestom-United/invoke"

                licenses {
                    license {
                        name = "MIT"
                        url = "https://github.com/Minestom-United/invoke/blob/master/LICENSE"
                    }
                }

                developers {
                    developer {
                        id = "Webhead1104"
                        url = "https://github.com/Webhead1104"
                    }
                }

                issueManagement {
                    system = "Github"
                    url = "https://github.com/Minestom-United/invoke/issues"
                }

                scm {
                    url = "https://github.com/Minestom-United/invoke"
                    connection = "scm:git:git://github.com/Minestom-United/invoke.git"
                    developerConnection = "scm:git:git@github.com:Minestom-United/invoke.git"
                }
            }
        }
        extensions.configure<PublishingExtension> {
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
    }
}
