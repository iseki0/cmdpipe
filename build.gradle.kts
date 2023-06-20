plugins {
    `kotlin-convention`
    signing
    `maven-publish`
}

allprojects {
    group = "space.iseki.cmdpipe"
    version = "0.3.4-SNAPSHOT"
}

java {
    withSourcesJar()
}

dependencies {
    compileOnly(libs.slf4j.api)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.logback.classic)
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
            val publication = this
            val javadocJar = tasks.register("${publication.name}JavadocJar", Jar::class) {
                archiveClassifier.set("javadoc")
                from(tasks.dokkaJavadoc)
                archiveBaseName.set("${archiveBaseName.get()}-${publication.name}")
            }
            artifact(javadocJar)
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
