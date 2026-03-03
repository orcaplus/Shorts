dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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

rootProject.name = "Repo"
include(":app") // Ganti dengan nama module Anda, misalnya ":DramaBox" atau ":GoodShort"
