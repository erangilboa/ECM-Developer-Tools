plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "com.dctm.workbench"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testImplementation"("org.assertj:assertj-core:3.27.3")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

tasks.register("dist") {
    group = "distribution"
    description = "Build the installable Workbench package"
    dependsOn(":server:dist")
}

tasks.register("installLocal") {
    group = "distribution"
    description = "Install ECM-Dev-Workbench for this user"
    dependsOn(":server:installLocal")
}
