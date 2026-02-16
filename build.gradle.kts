plugins {
    id("com.android.application") version "9.0.1" apply false
    id("com.android.library") version "9.0.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.3.10" apply false
    id("com.google.devtools.ksp") version "2.3.5" apply false
    id("com.github.triplet.play") version "4.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10" apply false
    alias(libs.plugins.spotless) apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("${projectDir}/config/detekt/detekt.yml")
    baseline = file("${projectDir}/config/detekt/baseline.xml")
    source.setFrom("app/src/main/java", "app/src/main/kotlin")
}

dependencies {
    "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}
