plugins {
    id("java")
}

group = "com.bethibande.process"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation("jakarta.persistence:jakarta.persistence-api:3.2.0")

    implementation(projects.annotations)
    annotationProcessor(projects.processor)
}

tasks.test {
    useJUnitPlatform()
}