plugins {
    id("java")
    `java-library`
    `maven-publish`
    signing
}

group = "com.bethibande.process"
version = "1.5"

description = "Annotations for DTO generation"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")
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