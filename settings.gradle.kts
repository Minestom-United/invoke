pluginManagement {
    includeBuild("invoke-gradle-plugin")
}

rootProject.name = "invoke"

include(
    ":invoke-runtime",
    ":invoke-processor",
    ":examples"
)
