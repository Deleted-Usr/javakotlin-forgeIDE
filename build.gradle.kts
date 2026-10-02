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
    implementation("tools.jackson.core:jackson-core:3.2.3")
    implementation("tools.jackson.core:jackson-databind:3.2.3")
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.build {
    dependsOn("buildFatJar")
}

tasks.register("buildAllJars") {
    group = "build"
    description = "Compiles and packages ForgeIDE and both language plugins"

    dependsOn(
        ":jar",
        ":modules:forge-lang-kotlin:jar",
        ":modules:forge-lang-cpp:jar"
    )
}

tasks.register<Copy>("buildLibraries") {
    group = "build"
    description = "Gathers all built library JARs into a distribution folder."

    // Explicitly wait for the java/kotlin jar tasks to complete
    dependsOn("jar")

    from(layout.buildDirectory.dir("libs"))
    into(layout.projectDirectory.dir("dist/libs"))
    include("**/*.jar")
}


// ForgeIDE loads Kotlin and C++ from ~/.forge/plugins, not from the build
// output, so a plugin change does nothing until its JAR is copied there. Old
// copies are removed first: two JARs for the same language would register it
// twice, which LanguageRegistry rejects at startup.
tasks.register("installPlugins") {
    group = "build"
    description = "Builds both language plugins and installs them into ~/.forge/plugins"

    val pluginJars = listOf(":modules:forge-lang-kotlin:jar", ":modules:forge-lang-cpp:jar")
    dependsOn(pluginJars)

    doLast {
        val pluginDirectory = file("${System.getProperty("user.home")}/.forge/plugins")

        delete(fileTree(pluginDirectory) { include("forge-lang-*.jar") })
        copy {
            from(pluginJars.map { tasks.getByPath(it).outputs.files })
            into(pluginDirectory)
        }
    }
}

tasks.register<Jar>("buildFatJar") {
    group = "build"
    description = "Compiles the main application and libraries into a singular fat jar file"

    archiveFileName.set("ForgeIDE-Fat.jar")

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

application {
    mainClass = "com.willclay.forgeide.Main"
    applicationName = "ForgeIDE"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}