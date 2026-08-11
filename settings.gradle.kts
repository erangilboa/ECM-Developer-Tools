rootProject.name = "dctm-admin-tool"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "core",
    "otds",
    "dfc-mock",
    "dfc-adapter",
    "otcs-mock",
    "otcs-adapter",
    "rest-adapter",
    "dfs-adapter",
    "server"
)

project(":core").projectDir = file("modules/core")
project(":otds").projectDir = file("modules/otds")
project(":dfc-mock").projectDir = file("modules/dfc-mock")
project(":dfc-adapter").projectDir = file("modules/dfc-adapter")
project(":otcs-mock").projectDir = file("modules/otcs-mock")
project(":otcs-adapter").projectDir = file("modules/otcs-adapter")
project(":rest-adapter").projectDir = file("modules/rest-adapter")
project(":dfs-adapter").projectDir = file("modules/dfs-adapter")
project(":server").projectDir = file("modules/server")
