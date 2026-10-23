plugins {
    id("java")
    // IntelliJ Platform Gradle Plugin 2.x (2.11.x is the last line compatible with Gradle 8.x).
    // Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
    mavenCentral()
    // Required by the IntelliJ Platform Gradle Plugin to resolve platform artifacts.
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        // Resolve the platform SDK from the locally installed IntelliJ IDEA 2026.1.
        local("/Applications/IntelliJ IDEA.app/Contents")
    }

    implementation("com.github.promeg:tinypinyin:2.0.3")
    implementation("cn.hutool:hutool-all:5.7.10")
    implementation("com.google.guava:guava:30.1-jre")
    implementation("org.quartz-scheduler:quartz:2.3.2") {
        exclude(group = "com.zaxxer")
        exclude(group = "org.slf4j")
        exclude(group = "com.mchange")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
    pluginConfiguration {
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261"
            // No upper bound: compatible with 2026.1 and every later build.
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = System.getenv("CERTIFICATE_CHAIN")
        privateKey = System.getenv("PRIVATE_KEY")
        password = System.getenv("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = System.getenv("PUBLISH_TOKEN")
    }
}
