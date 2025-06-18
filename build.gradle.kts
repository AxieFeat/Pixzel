plugins {
    kotlin("jvm") version "2.1.20"
}

group = "net.pixzel"
version = "1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}