plugins {
    `java-library`
    alias(libs.plugins.vanniktech.publish)
}

group = "dev.minestom-united.invoke"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.minestom.codec)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}
