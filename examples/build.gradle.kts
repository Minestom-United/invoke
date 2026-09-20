plugins {
    java
    id("dev.minestom-united.invoke")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":invoke-runtime"))
    implementation(libs.minestom.codec)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.toolchain {
    languageVersion = JavaLanguageVersion.of(25)
}

invoke {
    packageName = "dev.minestomunit.examples.example"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
