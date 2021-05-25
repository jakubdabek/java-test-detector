plugins {
    kotlin("jvm") version "1.5.0"
}

group = "pl.edu.pwr.pbr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.22.1")
}
