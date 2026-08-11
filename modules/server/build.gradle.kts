import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":otds"))
    implementation(project(":dfc-mock"))
    implementation(project(":dfc-adapter"))
    implementation(project(":otcs-mock"))
    implementation(project(":otcs-adapter"))
    implementation(project(":rest-adapter"))
    implementation(project(":dfs-adapter"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    testImplementation(libs.spring.boot.starter.test)
}

val uiDir = rootProject.layout.projectDirectory.dir("modules/ui")
val packagingDir = rootProject.layout.projectDirectory.dir("packaging")
val distDir = rootProject.layout.buildDirectory.dir("dist")
val portableDir = rootProject.layout.buildDirectory.dir("dist/ECM-Dev-Workbench-portable")
val jpackageInput = layout.buildDirectory.dir("jpackage-input")
val jlinkDir = layout.buildDirectory.dir("jlink-runtime")
val appImageDir = rootProject.layout.buildDirectory.dir("dist/ECM-Dev-Workbench")
val windows = System.getProperty("os.name").lowercase().contains("win")
val appVersion = version.toString().substringBefore("-")

fun npmCmd(vararg args: String): List<String> =
    if (windows) listOf("cmd.exe", "/c", "npm", *args) else listOf("npm", *args)

fun javaBin(name: String): File {
    val home = System.getProperty("java.home")
    val exe = if (windows) "$name.exe" else name
    return File(home, "bin/$exe")
}

val npmInstall by tasks.registering(Exec::class) {
    group = "build"
    workingDir = uiDir.asFile
    inputs.files(uiDir.file("package.json"), uiDir.file("package-lock.json"))
    outputs.dir(uiDir.dir("node_modules"))
    commandLine(npmCmd("install"))
}

val npmBuild by tasks.registering(Exec::class) {
    group = "build"
    dependsOn(npmInstall)
    workingDir = uiDir.asFile
    inputs.dir(uiDir.dir("src"))
    inputs.files(
        uiDir.file("index.html"),
        uiDir.file("package.json"),
        uiDir.file("vite.config.ts"),
        uiDir.file("tsconfig.json"),
        uiDir.file("public/logo.png"),
    )
    outputs.dir(uiDir.dir("dist"))
    commandLine(npmCmd("run", "build"))
}

val generatedUi = layout.buildDirectory.dir("generated-ui")

val copyUi by tasks.registering(Copy::class) {
    group = "build"
    dependsOn(npmBuild)
    from(uiDir.dir("dist"))
    into(generatedUi.map { it.dir("static") })
}

sourceSets.named("main") {
    resources.srcDir(generatedUi)
}

tasks.named("processResources") {
    dependsOn(copyUi)
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("ECM-Dev-Workbench.jar")
}

val packagePortable by tasks.registering(Copy::class) {
    group = "distribution"
    description = "Portable folder: jar + start scripts (requires Java 17+)"
    dependsOn(tasks.named("bootJar"))
    into(portableDir)
    from(tasks.named("bootJar"))
    from(packagingDir) {
        include(
            "start-workbench.bat",
            "start-workbench.sh",
            "install-windows.ps1",
            "install-windows.cmd",
            "uninstall-windows.ps1",
            "README.txt",
            "app-icon.ico",
            "app-icon.png",
        )
    }
}

val distZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Zip of the portable package"
    dependsOn(packagePortable)
    archiveFileName.set("ECM-Dev-Workbench-$appVersion.zip")
    destinationDirectory.set(distDir)
    from(portableDir)
}

val jlinkModules = listOf(
    "java.base",
    "java.desktop",
    "java.instrument",
    "java.logging",
    "java.management",
    "java.management.rmi",
    "java.naming",
    "java.net.http",
    "java.prefs",
    "java.rmi",
    "java.scripting",
    "java.security.jgss",
    "java.security.sasl",
    "java.sql",
    "java.sql.rowset",
    "java.transaction.xa",
    "java.xml",
    "java.xml.crypto",
    "jdk.charsets",
    "jdk.crypto.cryptoki",
    "jdk.crypto.ec",
    "jdk.localedata",
    "jdk.management",
    "jdk.naming.dns",
    "jdk.net",
    "jdk.unsupported",
    "jdk.zipfs",
).joinToString(",")

val prepareJpackageInput by tasks.registering(Copy::class) {
    dependsOn(tasks.named("bootJar"))
    from(tasks.named("bootJar"))
    into(jpackageInput)
}

val jlinkRuntime by tasks.registering(Exec::class) {
    group = "distribution"
    onlyIf { javaBin("jlink").isFile }
    outputs.dir(jlinkDir)
    commandLine(
        javaBin("jlink").absolutePath,
        "--add-modules", jlinkModules,
        "--no-header-files",
        "--no-man-pages",
        "--strip-debug",
        "--output", jlinkDir.get().asFile.absolutePath,
    )
    doFirst { jlinkDir.get().asFile.deleteRecursively() }
}

val jpackageImage by tasks.registering(Exec::class) {
    group = "distribution"
    dependsOn(prepareJpackageInput, jlinkRuntime)
    onlyIf { javaBin("jpackage").isFile && javaBin("jlink").isFile }
    outputs.dir(appImageDir)
    commandLine(
        javaBin("jpackage").absolutePath,
        "--type", "app-image",
        "--name", "ECM-Dev-Workbench",
        "--app-version", appVersion,
        "--vendor", "ECM-Dev-Workbench",
        "--description", "Documentum and Extended ECM developer workbench",
        "--dest", distDir.get().asFile.absolutePath,
        "--input", jpackageInput.get().asFile.absolutePath,
        "--main-jar", "ECM-Dev-Workbench.jar",
        "--main-class", "org.springframework.boot.loader.launch.JarLauncher",
        "--runtime-image", jlinkDir.get().asFile.absolutePath,
        "--java-options", "-Dworkbench.desktop=true",
        "--java-options", "-Dworkbench.open-browser=true",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--icon", packagingDir.file("app-icon.ico").asFile.absolutePath,
    )
    doFirst {
        distDir.get().asFile.mkdirs()
        appImageDir.get().asFile.deleteRecursively()
    }
}

val packageAppImage by tasks.registering(Copy::class) {
    group = "distribution"
    description = "App image with a bundled Java runtime (jpackage)"
    dependsOn(jpackageImage)
    onlyIf { javaBin("jpackage").isFile && javaBin("jlink").isFile }
    from(
        packagingDir.file("start-workbench.bat"),
        packagingDir.file("install-windows.ps1"),
        packagingDir.file("install-windows.cmd"),
        packagingDir.file("uninstall-windows.ps1"),
        packagingDir.file("app-icon.ico"),
    )
    into(appImageDir)
}

val dist by tasks.registering {
    group = "distribution"
    description = "Build UI, boot JAR, portable zip, and app image when jpackage is available"
    dependsOn(distZip, packageAppImage)
}

val installLocal by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Install for this Windows user (Start Menu + Desktop shortcuts)"
    dependsOn(packagePortable, packageAppImage, distZip)
    onlyIf { windows }
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        packagingDir.file("install-windows.ps1").asFile.absolutePath, "-SourceDir", ".")
    doFirst {
        val image = appImageDir.get().asFile
        val src =
            if (image.resolve("ECM-Dev-Workbench.exe").isFile || image.resolve("ECM-Dev-Workbench").isFile) {
                image
            } else {
                portableDir.get().asFile
            }
        commandLine(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", packagingDir.file("install-windows.ps1").asFile.absolutePath,
            "-SourceDir", src.absolutePath,
        )
    }
}
