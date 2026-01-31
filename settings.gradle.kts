pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "sca"

include(
    "sca-common",
    "sca-core",
    "sca-infra-persistence-jpa",
    "sca-infra-storage",
    "sca-infra-runner",
    "sca-tool-codeql",
    "sca-tool-mscan",
    "sca-tool-agent",
    "sca-app-api",
    "sca-app-worker",
)
