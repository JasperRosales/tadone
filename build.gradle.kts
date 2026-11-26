import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "com.tadone"
// CI passes -PappVersion=1.0.<run_number>; MSI needs MAJOR.MINOR.BUILD
val appVersion: String = (findProperty("appVersion") as String?) ?: "1.0.0"
version = appVersion

kotlin { jvmToolchain(17) }

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.slf4j:slf4j-nop:2.0.16")
    // Local document -> Markdown conversion (no API calls)
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.poi:poi-scratchpad:5.3.0")
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.23.1")
}

compose.desktop {
    application {
        mainClass = "com.tadone.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Tadone"
            packageVersion = appVersion
            description = "Tadone - your magic task helper"
            vendor = "Tadone"
            modules("java.sql", "java.naming", "jdk.crypto.ec", "java.net.http", "java.xml", "java.management", "java.instrument", "java.security.jgss", "java.xml.crypto", "jdk.unsupported")
            windows {
                iconFile.set(project.file("packaging/tadone.ico"))
                menuGroup = "Tadone"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6b0f3c7e-3c1a-4c55-9a8e-7a1d2f5e9c41"
            }
            linux { iconFile.set(project.file("src/main/resources/tadone.png")) }
        }
    }
}

// Dev-only: renders screenshots headlessly to /tmp/tadone-shots (./gradlew preview)
sourceSets {
    create("preview") {
        compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
        runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
    }
}

tasks.register<JavaExec>("preview") {
    classpath = sourceSets["preview"].runtimeClasspath
    mainClass.set("com.tadone.PreviewKt")
    environment("APPDATA", "/tmp/tadone-preview")
    jvmArgs("-Djava.awt.headless=true")
}

tasks.register<JavaExec>("importCheck") {
    classpath = sourceSets["preview"].runtimeClasspath
    mainClass.set("com.tadone.ImportCheckKt")
    args(project.findProperty("dir") ?: "/tmp")
    jvmArgs("-Djava.awt.headless=true")
}
