/*
 * Copyright (c) 2004-2026 The mzmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import org.gradle.internal.os.OperatingSystem

plugins {
    id("io.github.mzmine.java-app-conv")
    id("io.github.mzmine.javafx-conv")
    alias(libs.plugins.beryx.runtime)
}

repositories {
    mavenCentral()
    maven { url = uri(layout.projectDirectory.dir("../local-repo/")) }

    // sirius sdk (required for mzmine-community dependency)
    maven {
        url = uri("https://gitlab.com/api/v4/projects/66031889/packages/maven")
        name = "BG GitLab Common Public"
    }
}

dependencies {
    implementation(project(":mzmine-community"))
    implementation(project(":javafx-framework"))
    implementation(project(":utils"))
    implementation(project(":taskcontroller"))
    implementation("io.mzio:memory-management:1.0.0")
    implementation(libs.guava)
    implementation(libs.bundles.cdk)
    implementation(libs.bundles.jfreechart)
}

val appName = "spectral-library-editor"
val operatingSystem = OperatingSystem.current()
val iconDir = layout.projectDirectory.dir("../mzmine-community/src/main/resources")
val runtimeModules = mutableListOf(
    "java.desktop",
    "java.logging",
    "java.net.http",
    "java.rmi",
    "java.sql",
    "java.datatransfer",
    "java.management",
    "java.xml",
    "java.xml.crypto",
    "jdk.xml.dom",
    "java.naming",
    "java.transaction.xa",
    "java.scripting",
    "java.compiler",
    "jdk.jsobject",
    "jdk.jfr",
    "java.security.sasl",
    "java.security.jgss",
    "jdk.unsupported",
    "jdk.unsupported.desktop",
)
if (operatingSystem.isWindows) {
    // Required on Windows to access the trust store for root certificates.
    runtimeModules.add("jdk.crypto.mscapi")
}

application {
    mainClass.set("io.github.mzmine.speclibeditor.SpectralLibraryEditorLauncher")
    applicationName = appName
    applicationDefaultJvmArgs = listOf(
        "--add-exports=javafx.base/com.sun.javafx.event=org.controlsfx.controls",
        "--add-exports=javafx.graphics/com.sun.javafx.scene=org.controlsfx.controls",
        "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=org.controlsfx.controls",
        "--add-opens=javafx.controls/javafx.scene.control.skin=org.controlsfx.controls",
        "--add-exports=javafx.graphics/com.sun.javafx.css=org.controlsfx.controls",
        "--add-opens=java.logging/java.util.logging=ALL-UNNAMED",
    )
}

runtime {
    options = listOf(
        "--compress=zip-6",
        "--vm=server",
        "--no-header-files",
        "--no-man-pages",
        "--output",
        "jre/jre",
    )
    modules = runtimeModules

    jpackage {
        if (operatingSystem.isWindows) {
            installerType = "msi"
            installerName = "${appName}_Windows_installer"
            imageOptions = listOf("--icon", iconDir.file("mzmineIcon.ico").asFile.absolutePath)
            installerOptions = listOf(
                "--vendor", "mzio GmbH",
                "--win-menu",
                "--win-menu-group", appName,
                "--win-shortcut",
                "--win-dir-chooser",
            )
        }

        if (operatingSystem.isMacOsX) {
            installerType = "dmg"
            installerName = "${appName}_macOS_installer"
            imageOptions = listOf(
                "--icon", iconDir.file("mzmineIcon.icns").asFile.absolutePath,
                "--mac-package-name", appName,
                "--mac-package-identifier", "io.github.mzmine.speclibeditor",
            )
            installerOptions = listOf("--vendor", "mzio GmbH")
        }

        if (operatingSystem.isLinux) {
            installerName = "${appName}_Linux_installer"
            imageOptions = listOf("--icon", iconDir.file("mzmineIcon.png").asFile.absolutePath)
            installerOptions = listOf(
                "--vendor", "mzio GmbH",
                "--linux-package-name", appName,
                "--linux-shortcut",
                "--linux-menu-group", appName,
            )
        }

        imageName = appName
        jvmArgs = listOf(
            "-showversion",
            "-XX:MinHeapFreeRatio=50",
            "-XX:MaxHeapFreeRatio=75",
            "-XX:InitialRAMPercentage=5",
            "-XX:MinRAMPercentage=75",
            "-XX:MaxRAMPercentage=80",
            "-enableassertions",
            "--add-opens=java.logging/java.util.logging=ALL-UNNAMED",
            "--enable-preview",
        )
    }
}
