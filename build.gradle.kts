plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com")
}

dependencies {
    api("com.google.code.gson:gson:2.10.1")
    api("com.viaversion:nbt:3.0.0")
    api("it.unimi.dsi:fastutil:8.5.12")
    api("ch.qos.logback:logback-classic:1.4.14")
    compileOnly("org.jetbrains:annotations:24.0.1")
    // Uncomment to manually run mappings gen in ide
    // compileOnly(files("server.jar"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

group = "com.viaversion"
version = "3.3.1"
description = "MappingsGenerator"
java.sourceCompatibility = JavaVersion.VERSION_17

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    withType<Javadoc> {
        options.encoding = "UTF-8"
    }

    jar {
        manifest {
            attributes["Main-Class"] = "com.viaversion.mappingsgenerator.MappingsGenerator"
        }
    }


    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("MappingsGenerator-${project.version}.jar")

        relocate("com.google.gson", "com.viaversion.viaversion.libs.gson")
        relocate("com.github.steveice10.opennbt", "com.viaversion.viaversion.libs.opennbt")
        relocate("it.unimi.dsi.fastutil", "com.viaversion.viaversion.libs.fastutil")

        // FastUtil - we only want object and int maps
        // Object types
        exclude("it/unimi/dsi/fastutil/*/*Reference*")
        exclude("it/unimi/dsi/fastutil/*/*Boolean*")
        exclude("it/unimi/dsi/fastutil/*/*Byte*")
        exclude("it/unimi/dsi/fastutil/*/*Short*")
        exclude("it/unimi/dsi/fastutil/*/*Float*")
        exclude("it/unimi/dsi/fastutil/*/*Double*")
        exclude("it/unimi/dsi/fastutil/*/*Long*")
        exclude("it/unimi/dsi/fastutil/*/*Char*")
        // Map types
        exclude("it/unimi/dsi/fastutil/*/*Custom*")
        exclude("it/unimi/dsi/fastutil/*/*Tree*")
        exclude("it/unimi/dsi/fastutil/*/*Heap*")
        exclude("it/unimi/dsi/fastutil/*/*Queue*")
        // Crossing fingers
        exclude("it/unimi/dsi/fastutil/*/*Big*")
        exclude("it/unimi/dsi/fastutil/*/*Synchronized*")
        exclude("it/unimi/dsi/fastutil/*/*Unmodifiable*")
        exclude("it/unimi/dsi/fastutil/io/*")
    }
    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
