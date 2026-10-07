plugins {
    kotlin("jvm") version "2.4.20"
    // Generates a serializer for every @Serializable class at compile time, so
    // reading JSON needs no reflection. Must match the Kotlin version above.
    kotlin("plugin.serialization") version "2.4.20"
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
version = "1.2.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.formdev:flatlaf-intellij-themes:3.7.2")
    implementation("com.formdev:flatlaf-extras:3.7.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains:markdown:0.7.9")

    implementation("com.github.weisj:jsvg:2.2.0")
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

// The exe launchers put every JAR in dist/libs on the class path, so that
// folder must hold exactly the dependency versions Gradle resolved. Copying
// them by hand drifts: a stale jackson-annotations once made session saving
// throw NoClassDefFoundError, which stopped the window from closing.
//
// Sync (unlike Copy) also deletes anything in dist/libs that is no longer a
// dependency, so an old version cannot linger next to its replacement.
tasks.register<Sync>("syncDistLibraries") {
    group = "distribution"
    description = "Makes dist/libs contain exactly the runtime dependencies Gradle resolved"

    from(configurations.runtimeClasspath)
    into(layout.projectDirectory.dir("dist/libs"))
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

    // FlatLaf and Jackson keep newer-Java versions of some classes under
    // META-INF/versions (FlatLaf's HiDPI image support among them). Java only
    // looks there when the manifest says so; the libraries' own manifests said
    // it, but merging them into one jar replaces their manifests with this one.
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Multi-Release"] = "true"
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