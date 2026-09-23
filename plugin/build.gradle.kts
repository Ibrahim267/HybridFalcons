// ============================================================
//  FitDeveloper — IntelliJ Platform Plugin
//  "Help the Developer" — 42AD x JetBrains hackathon
//  Build:  gradle buildPlugin   -> build/distributions/FitDeveloper-<v>.zip
//  Run:    gradle runIde        -> sandbox IDE with the plugin loaded
//
//  Uses the IntelliJ Platform Gradle Plugin 2.x (required for 2024.2+).
//
//  LOCAL-BUILD MODE (offline-friendly, no ~1 GB IDE download):
//  the platform dependency points at the installed Android Studio
//  (IntelliJ Platform build AI-243), which is also the IDE the plugin
//  gets tested in. For a "clean" IC-2024.2.4 build, swap the local(...)
//  line for:  intellijIdeaCommunity("2024.2.4")
// ============================================================
plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.fitdeveloper"
version = "3.4.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        // Compiles with the locally available JDK 21 (Android Studio's JBR,
        // pinned via org.gradle.java.home in gradle.properties) and emits
        // Java 17 bytecode — the baseline for the 2024.2+ platform.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    intellijPlatform {
        // Android Studio (IntelliJ Platform 2024.3, AI-243.x) as local SDK.
        local("C:/Program Files/Android/Android Studio")
        // intellijIdeaCommunity("2024.2.4")   // <- upstream CI variant
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("242")
            untilBuild.set("299.*")
        }
    }
    // No forms/ searchable options to build — skip the headless IDE run.
    buildSearchableOptions = false
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
}
