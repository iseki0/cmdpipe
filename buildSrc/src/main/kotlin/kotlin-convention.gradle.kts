plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
//    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    idea
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "11"
    javaCompiler.set(javaToolchains.compilerFor { configure() })
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
    kotlinOptions.jvmTarget = "11"
    kotlinJavaToolchain.toolchain.use(javaToolchains.launcherFor { configure() })
}

tasks.test {
    useJUnitPlatform()
}

fun JavaToolchainSpec.configure() {
    languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
