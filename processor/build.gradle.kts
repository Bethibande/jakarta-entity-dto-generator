plugins {
    id("java")
}

group = "de.bethibande.process"
version = "unspecified"

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