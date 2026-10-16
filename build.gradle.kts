plugins{
    java
}

sourceSets.main{
    java.setSrcDirs(listOf("src"))
}

// Pinned stable release. The game API is not stable across builds, so never use "latest" or "be" here.
val mindustryVersion = "v160.7"
val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val sdkRoot: String? = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
val projectName = project.name

repositories{
    mavenCentral()

    //Downloads the dependencies JAR file from Mindustry releases; does not use any real repository.
    ivy{
        url = uri("https://github.com/")
        patternLayout{
            artifact("/[organisation]/[module]/releases/download/[revision]/dependencies.jar")
        }
        metadataSources{ artifact() }
        content{ includeVersion("Anuken", "Mindustry", mindustryVersion) }
    }
}

java{
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach{
    options.encoding = "UTF-8"
}

dependencies{
    //the game provides Mindustry and Arc at runtime; they must never be bundled into the mod jar
    compileOnly("Anuken:Mindustry:$mindustryVersion")
}

tasks.jar{
    archiveFileName.set("${projectName}Desktop.jar")

    from(configurations.runtimeClasspath.map{ files ->
        files.map{ if(it.isDirectory) it else zipTree(it) }
    })

    from(projectDir){
        include("mod.hjson")
        include("icon.png")
    }

    from("assets/"){
        include("**")
    }
}

val jarAndroid = tasks.register("jarAndroid"){
    dependsOn(tasks.jar)

    doLast{
        val sdk = sdkRoot?.let{ File(it) }
        if(sdk == null || !sdk.exists()){
            throw GradleException("No valid Android SDK found. Ensure that ANDROID_HOME is set to your Android SDK directory.")
        }

        val platformRoot = File(sdk, "platforms").listFiles()
            ?.sortedDescending()
            ?.firstOrNull{ File(it, "android.jar").exists() }
            ?: throw GradleException("No android.jar found. Ensure that you have an Android platform installed.")

        //collect dependencies needed for desugaring
        val classpath = (configurations.compileClasspath.get().files +
                configurations.runtimeClasspath.get().files +
                File(platformRoot, "android.jar"))

        val d8 = if(isWindows) "d8.bat" else "d8"

        val command = buildList{
            add(d8)
            classpath.forEach{ addAll(listOf("--classpath", it.path)) }
            addAll(listOf("--min-api", "21"))
            addAll(listOf("--output", "${projectName}Android.jar"))
            add("${projectName}Desktop.jar")
        }

        //dex and desugar files - this requires d8 in your PATH
        val exitCode = ProcessBuilder(command)
            .directory(layout.buildDirectory.dir("libs").get().asFile)
            .inheritIO()
            .start()
            .waitFor()

        if(exitCode != 0) throw GradleException("d8 failed with exit code $exitCode")
    }
}

// --- development tooling: never part of the mod artifact ---

tasks.register<Exec>("validateSprites"){
    group = "verification"
    description = "Validates generated sprites and checks they match a fresh generation (needs Python 3 + Pillow)."
    commandLine(if(isWindows) "python" else "python3", "tools/spritegen/validate.py", "--stale")
}

tasks.register<Jar>("deploy"){
    dependsOn(jarAndroid, tasks.jar)
    archiveFileName.set("$projectName.jar")

    val libs = layout.buildDirectory.dir("libs")
    from(provider{
        listOf(
            zipTree(libs.get().file("${projectName}Desktop.jar")),
            zipTree(libs.get().file("${projectName}Android.jar"))
        )
    })

    doLast{
        libs.get().file("${projectName}Android.jar").asFile.delete()
    }
}
