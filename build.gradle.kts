plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.6"
}

repositories {
    mavenCentral()
    maven("https://repo.viaversion.com")
}

dependencies {
    api("com.google.code.gson:gson:2.13.1")
    api("com.viaversion:nbt:5.1.1")
    api("it.unimi.dsi:fastutil:8.5.16")
    api("ch.qos.logback:logback-classic:1.5.16")
    compileOnly("org.jetbrains:annotations:26.0.2")
    // Uncomment to manually run mappings gen in ide
    // compileOnly(files("server.jar"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

group = "com.viaversion"
version = "4.1.0"
description = "MappingsGenerator"
java.sourceCompatibility = JavaVersion.VERSION_21

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
    }
    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
