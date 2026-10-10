plugins {
    kotlin("jvm")
}

group = "com.willclay"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // ForgeIDE supplies its API and shared libraries when loading the plugin.
    compileOnly(project(":"))
    compileOnly("com.formdev:flatlaf:3.7.2")

    // Tests run outside ForgeIDE, so they need the API on their own classpath.
    testImplementation(project(":"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Show println output in the console, so template previews are visible.
    testLogging.showStandardStreams = true
}
