plugins {
    java
}

group = "com.willclay"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // ForgeIDE supplies these classes through the plugin loader's parent.
    compileOnly(project(":"))
    compileOnly("com.formdev:flatlaf:3.7.2")
}
