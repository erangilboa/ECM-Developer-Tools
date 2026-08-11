plugins {
    `java-library`
}

dependencies {
    api(project(":core"))
    api(project(":otds"))
    testImplementation(libs.wiremock)
}
