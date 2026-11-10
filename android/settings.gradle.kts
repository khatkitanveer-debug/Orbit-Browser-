pluginManagement {
    repositories {
        google()
        // Maven Central ka Google mirror — sandbox IP par repo.maven.apache.org 429 de raha hota hai
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        mavenCentral()
    }
}
rootProject.name = "OrbitBrowser"
include(":app")
