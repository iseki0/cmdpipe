import org.graalvm.buildtools.gradle.dsl.GraalVMExtension

plugins {
    `java-library`
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.2"
    id("org.jetbrains.kotlinx.kover") version "0.7.2"
    id("org.graalvm.buildtools.native") version "0.10.1"
    signing
    `maven-publish`
}

allprojects {
    group = "space.iseki.cmdpipe"
    version = "0.6.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.checker.qual)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.logback.classic)
    testImplementation(libs.kotlinx.coroutine.core)
    compileOnly("org.graalvm.sdk:graal-sdk:24.0.0")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs += "-Xcontext-receivers"
    kotlinOptions.freeCompilerArgs += "-Xassertions=jvm"
    kotlinOptions.freeCompilerArgs += "-Xlambdas=indy"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=all-compatibility"
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    repositories {
        maven {
            name = "Central"
            url = if (version.toString().endsWith("SNAPSHOT")) {
                // uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
                uri("https://oss.sonatype.org/content/repositories/snapshots")
            } else {
                // uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2")
                uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
            }
            credentials {
                username = properties["ossrhUsername"]?.toString() ?: System.getenv("OSSRH_USERNAME")
                password = properties["ossrhPassword"]?.toString() ?: System.getenv("OSSRH_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("cmdpipe")
                description.set("Utils for commandline call")
                url.set("https://github.com/iseki0/cmdpipe")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("iseki0")
                        name.set("iseki zero")
                        email.set("iseki@iseki.space")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/iseki0/cmdpipe.git")
                    developerConnection.set("scm:git:https://github.com/iseki0/cmdpipe.git")
                    url.set("https://github.com/iseki0/cmdpipe")
                }
            }
        }
    }
}

afterEvaluate {
    signing {
        // To use local gpg command, configure gpg options in ~/.gradle/gradle.properties
        // reference: https://docs.gradle.org/current/userguide/signing_plugin.html#example_configure_the_gnupgsignatory
        useGpgCmd()
        publishing.publications.forEach { sign(it) }
    }
}

graalvmNative {
    toolchainDetection.set(true)
    binaries {
        named("test") {
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(22))
                vendor.set(JvmVendorSpec.GRAAL_VM)
            })
            buildArgs.add("-Ob")
            buildArgs.add("-H:+UnlockExperimentalVMOptions")
            buildArgs.add("-march=native")
            buildArgs.add("-H:+AddAllCharsets")
            buildArgs.add("--verbose")
            buildArgs.add("-H:Log=registerResource:5")
        }
    }
}

tasks.nativeTestCompile {
    doFirst {
        val d =
            project.extensions.getByType(GraalVMExtension::class).binaries["test"].asCompileOptions().javaLauncher.get().executablePath.asFile.parentFile
        val ni = File(d, "native-image")
        if (ni.exists()) {
            ni.setExecutable(true)
        }
    }
}


