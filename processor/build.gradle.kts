plugins {
    id("java")
    `java-library`
    `maven-publish`
    signing
}

group = "com.bethibande.process"
version = "1.0"

description = "DTO generator for JPA entities"

repositories {
    mavenCentral()
}

dependencies {
    implementation(projects.annotations)

    implementation("com.palantir.javapoet:javapoet:0.8.0")
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")

    implementation("com.google.auto.service:auto-service:1.1.1")
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name = project.name
                description = project.description

                url = "https://github.com/Bethibande/jakarta-entity-dto-generator"

                licenses {
                    license {
                        name = "Apache-2"
                        url = "https://github.com/Bethibande/jakarta-entity-dto-generator/blob/master/LICENSE"
                    }
                }

                developers {
                    developer {
                        id = "bethibande"
                        name = "Max Bethmann"
                        email = "contact@bethibande.com"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/Bethibande/jakarta-entity-dto-generator.git"
                    developerConnection = "scm:git:ssh://github.com/Bethibande/jakarta-entity-dto-generator.git"
                    url = "https://github.com/Bethibande/jakarta-entity-dto-generator"
                }
            }
        }
    }

    repositories {
        maven {
            name = "Maven-Releases"
            url = uri("https://pckg.bethibande.com/repository/maven-releases/")
            credentials {
                if (providers.gradleProperty("mavenUsername").isPresent) {
                    username = providers.gradleProperty("mavenUsername").get()
                    password = providers.gradleProperty("mavenPassword").get()
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}