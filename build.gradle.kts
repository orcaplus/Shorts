import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/recloudstream/cloudstream") {
            credentials {
                username = project.findProperty("gpr.user") as? String ?: System.getenv("GITHUB_USER")
                password = project.findProperty("gpr.key") as? String ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }

    configurations.classpath {
        resolutionStrategy {
            // Avoid flaky JitPack metadata fetch for jadb:master-SNAPSHOT in CI.
            force("com.github.vidstige:jadb:1.2.1")
        }
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        
        // Gunakan versi yang TERSEDIA di JitPack
        // Cek di: https://jitpack.io/#recloudstream/gradle
        classpath("com.github.recloudstream:gradle:v1.0.3") // atau versi terbaru yang available
        
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20") // versi stabil
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/recloudstream/cloudstream") {
            credentials {
                username = project.findProperty("gpr.user") as? String ?: System.getenv("GITHUB_USER")
                password = project.findProperty("gpr.key") as? String ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) = 
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) = 
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "org.jetbrains.kotlin.android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/orcaplus/Shorts")
        authors = listOf("orcaplus")
    }

    android {
        namespace = "repo.plusorca.cloudstream"

        compileSdkVersion(36)
        
        defaultConfig {
            minSdk = 21
            targetSdk = 36
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions {
                jvmTarget = "11"
                freeCompilerArgs = freeCompilerArgs + listOf(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions"
                )
            }
        }
    }

    dependencies {
        // Cloudstream API - versi stabil
        compileOnly("com.lagradost:cloudstream3:3.0.0") // atau versi terbaru
        
        // JSON parsing - Gson sudah include di Cloudstream, tapi kita tambahkan untuk keamanan
        implementation("com.google.code.gson:gson:2.10.1")
        
        // HTML parsing (optional)
        implementation("org.jsoup:jsoup:1.18.3")
        
        // Coroutines (sudah include di Cloudstream)
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
        
        // Testing (optional)
        testImplementation("junit:junit:4.13.2")
    }
}

task<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
