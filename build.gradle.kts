import xyz.jpenilla.resourcefactory.bukkit.Permission
import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml

plugins {
    java
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
    //id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
    id("com.gradleup.shadow") version "9.2.2"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven { url = uri("https://nexus.scarsz.me/content/groups/public/") }
    maven { url = uri("https://repo1.maven.org/maven2/") }

    maven { url = uri("https://repo.maven.apache.org/maven2/") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
}

group = "com.github.cinnamondev"
version = "1.0-SNAPSHOT"
description = "Capture the wool!"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks {
    compileJava {
        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release = 21
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    runServer {
        downloadPlugins {
            //modrinth("worldedit", "7.4.0-beta-01")
            //modrinth("discordsrv", "1.30.2") //  for discordsrv integration testing.
            modrinth("luckperms", "v5.5.17-bukkit")
        }
        runDirectory.set(layout.buildDirectory.dir("run"))
        minecraftVersion("1.21.10")
    }
}


paperPluginYaml {
    main = "com.github.cinnamondev.captureTheWool.CaptureTheWool"
    //bootstrapper = "com.github.cinnamondev.railworks.RailworksBootstrap"
    apiVersion = "1.21"
    authors.add("cinnamondev")
    dependencies {

    }
    permissions {

    }
}
