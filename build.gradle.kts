plugins {
    id("org.jetbrains.intellij") version "1.16.0"
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
}

group = "com.ordinarythinker"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("compiler-embeddable", "1.9.21"))
    implementation(kotlin("reflect"))
}

apply(plugin = "org.jetbrains.intellij")
apply(plugin = "kotlin")

intellij {
    //version.set("2023.3.4")
    version.set("2022.3.1")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf("android", "Kotlin"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    runIde {
        // Absolute path to installed target 3.5 Android Studio to use as
        // IDE Development Instance (the "Contents" directory is macOS specific):
        ideDir.set(file("C:\\Program Files\\Android\\Android Studio2"))
    }

    patchPluginXml {
        //sinceBuild.set("233")
        sinceBuild.set("223")
        untilBuild.set("241.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
