pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ciphwren-android"

include(":app")
// Git submodules (github.com/nikossvnk/*), built as part of this project
include(":ical4android")
include(":vcard4android")
include(":cert4android")
include(":etebase-client")
project(":etebase-client").projectDir = file("etebase-java/client")
