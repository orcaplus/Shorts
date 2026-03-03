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
        // Use Cloudstream3 fork of the gradle plugin on JitPack.
        classpath("com.github.recloudstream:gradle:cce1b8d84d")
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
    
    // Cloudstream dependencies - WAJIB
    cloudstream("com.lagradost:cloudstream3:3.0.0")

    // Hanya gunakan SATU JSON parser: Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.0")
    
    // Optional tapi sering dipakai
    implementation("org.jsoup:jsoup:1.17.2") // Untuk parsing HTML (optional)
    
    // HAPUS SEMUA INI:
    // implementation("com.github.Blatzar:NiceHttp:0.4.13") // Cloudstream sudah punya app.get
    // implementation("com.google.code.gson:gson:2.10.1") // KONFLIK dengan Jackson
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") // KONFLIK dengan Jackson
    // implementation("app.cash.quickjs:quickjs-android:0.9.2") // Tidak diperlukan
    // implementation("com.squareup.okhttp3:okhttp:4.12.0") // Cloudstream sudah punya
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1") // Udah include dari cloudstream
    // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1") // Udah include
    // implementation("com.faendir.rhino:rhino-android:1.6.0") // Tidak diperlukan
    // implementation("me.xdrop:fuzzywuzzy:1.4.0") // Tidak diperlukan
}

}
task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
