// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    // Add the dependency for the Google services Gradle plugin
    id("com.google.gms.google-services") version "4.4.2" apply false
}

buildscript {
    dependencies {
        classpath ("com.android.tools.build:gradle:7.0.2")
        classpath ("com.google.gms:google-services:4.3.10")
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}