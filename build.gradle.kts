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
    version.set("2022.3.1")
    type.set("IC")

    plugins.set(listOf("android", "Kotlin"))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    runIde {
        ideDir.set(file("C:\\Program Files\\Android\\Android Studio2"))
    }

    patchPluginXml {
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
