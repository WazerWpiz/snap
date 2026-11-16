// SPDX-License-Identifier: GPL-3.0-or-later
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "snapgent-android"

include(":app")
include(":webview-host")
include(":mcp-core")
include(":tools-workspace")
include(":mcp-remote-client")
include(":settings")
