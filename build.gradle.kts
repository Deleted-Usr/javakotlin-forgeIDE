plugins {
    kotlin("jvm") version "2.4.20"
    application
}

sourceSets {
    main {
        resources {
            srcDirs("src/main/resources")
        }
    }
}

group = "com.willclay"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.formdev:flatlaf-intellij-themes:3.7.2")
    implementation("com.formdev:flatlaf-extras:3.7.2")

    implementation("com.fasterxml.jackson.core:jackson-annotations:2.21")
    implementation("tools.jackson.core:jackson-core:3.2.2")
    implementation("tools.jackson.core:jackson-databind:3.2.2")
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

application {
    mainClass = "com.willclay.forgeide.Main"
    applicationName = "ForgeIDE"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}