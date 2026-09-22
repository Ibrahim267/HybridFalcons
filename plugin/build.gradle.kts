// ============================================================
//  CrunchGuard — IntelliJ Platform Plugin
//  "Help the Developer" — 42AD x JetBrains hackathon
//  Build:  gradle buildPlugin   -> build/distributions/CrunchGuard-<v>.zip
//  Run:    gradle runIde        -> sandbox IDE with the plugin loaded
// ============================================================
plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.crunchguard"
version = "2.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        // auto-downloaded if missing (foojay resolver in settings.gradle.kts)
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

intellij {
    version.set("2024.2.4")   // target platform: IntelliJ IDEA Community 2024.2
    type.set("IC")            // works in IC / IU / PyCharm / WebStorm (JCEF + platform only)
    updateSinceUntilBuild.set(false)
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    // searchable options are useless for this plugin and slow the build a lot
    buildSearchableOptions {
        enabled = false
    }
}
