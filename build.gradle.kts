plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("com.gradleup.shadow") version "9.4.1"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "io.github.Earth1283"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("net.dv8tion:JDA:5.3.0") {
        exclude(module = "opus-java")
    }
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("net.objecthunter:exp4j:0.4.8")
}

tasks {
    runServer {
        minecraftVersion("1.21")
    }

    shadowJar {
        // Relocate to avoid classpath conflicts with other plugins
        relocate("net.dv8tion.jda", "io.github.Earth1283.dogBerry.shade.jda")
        relocate("com.fasterxml.jackson", "io.github.Earth1283.dogBerry.shade.jackson")
        relocate("org.apache.commons.collections4", "io.github.Earth1283.dogBerry.shade.collections4")
        // org.sqlite must NOT be relocated — sqlite-jdbc uses JNI and native method names
        // are derived from the Java class name; relocating breaks the JNI binding.
        relocate("net.objecthunter.exp4j", "io.github.Earth1283.dogBerry.shade.exp4j")
        relocate("kotlinx.serialization", "io.github.Earth1283.dogBerry.shade.serialization")

        // JDA uses reflection extensively — must not minimize it
        minimize {
            exclude(dependency("net.dv8tion:JDA:.*"))
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
        }

        mergeServiceFiles()

        mergeServiceFiles()
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
        // Bundle Gradle wrapper for dev-tools plugin builds.
        // gradle-wrapper.jar is renamed to .data so ShadowJar doesn't try to merge it as a jar.
        // WritePluginTool renames it back to .jar on extraction.
        from("gradle/wrapper") {
            into("plugin-template/gradle/wrapper")
            rename("gradle-wrapper.jar", "gradle-wrapper.jar.data")
        }
        from("gradlew") { into("plugin-template") }
        from("gradlew.bat") { into("plugin-template") }
    }

    build {
        dependsOn("shadowJar")
    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}
