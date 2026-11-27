import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    kotlin("multiplatform") version "2.1.21"
    kotlin("plugin.compose") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
}

/*
 * Barkeep for the browser (installable PWA).
 *
 * The app's Kotlin sources are shared with the Android app: this build compiles app/src/main/java
 * directly and only swaps the few platform files (storage, HTTP, share, camera …) for the web versions
 * in src/wasmJsMain/kotlin. String resources are generated from the Android strings.xml
 * (plus a few web-specific texts in src/res).
 */

val appDir = rootDir.resolve("../app/src/main")
val versionName = providers.gradleProperty("versionName")
    .orElse(provider { rootDir.resolve("../VERSION").readText().trim() })

/** Android-only files that have a web counterpart in src/wasmJsMain/kotlin. */
val androidOnly = listOf(
    "de/schnubbirunkel/barkeep/MainActivity.kt",
    "de/schnubbirunkel/barkeep/data/Http.kt",
    "de/schnubbirunkel/barkeep/data/Stores.kt",
    "de/schnubbirunkel/barkeep/data/Sources.kt",
    "de/schnubbirunkel/barkeep/ui/Platform.kt",
)

val generateResources by tasks.registering {
    val out = layout.buildDirectory.dir("generated/barkeep/kotlin")
    val en = appDir.resolve("res/values/strings.xml")
    val de = appDir.resolve("res/values-de/strings.xml")
    val webEn = file("src/res/values/strings.xml")
    val webDe = file("src/res/values-de/strings.xml")
    val fonts = appDir.resolve("res/font")
    val version = versionName
    inputs.files(en, de, webEn, webDe)
    inputs.dir(fonts)
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        fun read(f: File): Map<String, String> {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).associate { i ->
                val e = nodes.item(i) as org.w3c.dom.Element
                var t = e.textContent
                if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length - 1)
                t = Regex("""\\(.)""").replace(t) { m -> when (val c = m.groupValues[1]) { "n" -> "\n"; "t" -> "\t"; else -> c } }
                e.getAttribute("name") to t
            }
        }
        fun lit(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t").replace("$", "\\$") + "\""
        val enMap = read(en) + read(webEn)
        val deMap = read(de) + read(webDe)
        val keys = enMap.keys.sorted()
        val fontNames = fonts.listFiles()!!.map { it.nameWithoutExtension }.sorted()
        val dir = out.get().asFile.resolve("de/schnubbirunkel/barkeep").apply { mkdirs() }
        dir.resolve("R.kt").writeText(buildString {
            appendLine("// Generated from app/src/main/res – do not edit.")
            appendLine("package de.schnubbirunkel.barkeep\n")
            appendLine("object R {")
            appendLine("    object string {")
            keys.forEachIndexed { i, k -> appendLine("        const val $k = $i") }
            appendLine("    }")
            appendLine("    object font {")
            fontNames.forEachIndexed { i, k -> appendLine("        const val $k = $i") }
            appendLine("    }")
            appendLine("}\n")
            appendLine("object BuildConfig {")
            appendLine("    const val VERSION_NAME = ${lit(version.get())}")
            appendLine("}\n")
            appendLine("internal val FONT_FILES = arrayOf(" + fontNames.joinToString { lit("fonts/$it.ttf") } + ")\n")
            appendLine("internal val STRINGS_EN = arrayOf(")
            keys.forEach { appendLine("    ${lit(enMap.getValue(it))},") }
            appendLine(")\n")
            appendLine("internal val STRINGS_DE = arrayOf(")
            keys.forEach { appendLine("    ${lit(deMap[it] ?: enMap.getValue(it))},") }
            appendLine(")")
        })
    }
}

val copyAssets by tasks.registering(Sync::class) {
    from(appDir.resolve("assets"))
    from(appDir.resolve("res/font")) { into("fonts") }
    into(layout.buildDirectory.dir("generated/barkeep/resources"))
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "barkeep"
        browser {
            commonWebpackConfig { outputFileName = "barkeep.js" }
        }
        binaries.executable()
    }
    sourceSets {
        val wasmJsMain by getting {
            kotlin.srcDir(appDir.resolve("java"))
            kotlin.exclude(androidOnly)
            kotlin.srcDir(generateResources)
            resources.srcDir(copyAssets)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.0-beta03")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
                implementation("io.coil-kt.coil3:coil-compose:3.2.0")
                implementation("io.coil-kt.coil3:coil-network-ktor3:3.2.0")
                implementation("io.ktor:ktor-client-js:3.1.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
    }
}

// A new cache name per build lets the service worker drop files of older versions.
tasks.named<ProcessResources>("wasmJsProcessResources") {
    val build = providers.environmentVariable("GITHUB_SHA").map { it.take(10) }.orElse(versionName)
    inputs.property("build", build)
    filesMatching("sw.js") { filter { it.replace("@BUILD@", build.get()) } }
}
