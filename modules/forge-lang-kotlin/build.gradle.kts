plugins {
    kotlin("jvm")
}

group = "com.willclay"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // ForgeIDE supplies its API and shared libraries when loading the plugin.
    compileOnly(project(":"))
}
