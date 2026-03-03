import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    configurations.classpath {
        resolutionStrategy {
            // Avoid flaky JitPack metadata fetch for jadb:master-SNAPSHOT in CI.
            force("com.github.vidstige:jadb:1.2.1")
        }
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        // PERBAIKAN: Gunakan versi terbaru yang tersedia, bukan commit hash
        classpath("com.github.recloudstream:gradle:3.0.1") // atau versi terbaru
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/orcaplus/Shorts")
        authors = listOf("orcaplus")
    }

    android {
        namespace = "repo.plusorca.cloudstream"

        defaultConfig {
            minSdk = 21
            compileSdkVersion(36)
            targetSdk = 36
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        val cloudstream by configurations
        val implementation by configurations
        
        // Cloudstream dependencies - Gunakan versi spesifik, bukan pre-release
        cloudstream("com.lagradost:cloudstream3:3.0.1") // ganti dengan versi stabil terbaru
        
        // PERBAIKAN: Hanya gunakan SATU JSON parser: Gson (lebih ringan untuk Cloudstream)
        implementation("com.google.code.gson:gson:2.10.1")
        
        // Optional
        implementation("org.jsoup:jsoup:1.17.2")
        
        // HAPUS semua komentar yang tidak perlu
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
