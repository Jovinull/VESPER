import java.time.Duration

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

//test harness: a second mod that generates the maps, runs the in-game test suites and captures screenshots
val harness: SourceSet by sourceSets.creating{
    java.setSrcDirs(listOf("harness/src"))
}

dependencies{
    "harnessCompileOnly"("Anuken:Mindustry:$mindustryVersion")
    //suites reference Vesper content directly; at run time the Vesper mod provides it
    "harnessCompileOnly"(sourceSets.main.get().output)
}

val harnessJar = tasks.register<Jar>("harnessJar"){
    archiveFileName.set("VesperHarness.jar")
    from(harness.output)
    from("harness"){ include("mod.hjson") }
}

val runDir = layout.projectDirectory.dir("run")
val gameJar = runDir.file("Mindustry-$mindustryVersion.jar").asFile

val downloadGame = tasks.register("downloadGame"){
    group = "vesper"
    description = "Downloads the official desktop client for $mindustryVersion into run/ (gitignored)."
    outputs.file(gameJar)
    onlyIf{ !gameJar.exists() }
    doLast{
        gameJar.parentFile.mkdirs()
        uri("https://github.com/Anuken/Mindustry/releases/download/$mindustryVersion/Mindustry.jar").toURL().openStream().use{ input ->
            gameJar.outputStream().use{ input.copyTo(it) }
        }
    }
}

tasks.register<JavaExec>("runClient"){
    group = "vesper"
    description = "Runs the official client with Vesper in an isolated data directory (run/data). " +
        "-Pharness[=suite,suite] runs the in-game test suites and exits with status 1 if a check fails; -PharnessSpeed=N speeds up game time."
    val withHarness = project.hasProperty("harness")
    val suites = (project.findProperty("harness") as String?)?.takeIf{ it.isNotBlank() } ?: "all"
    val speed = (project.findProperty("harnessSpeed") as String?) ?: "1"
    val dataDir = runDir.dir("data").asFile
    val modJar = tasks.jar.flatMap{ it.archiveFile }
    val harnessFile = harnessJar.flatMap{ it.archiveFile }
    dependsOn(tasks.jar, downloadGame)
    if(withHarness) dependsOn(harnessJar)
    classpath = files(gameJar)
    mainClass.set("mindustry.desktop.DesktopLauncher")
    workingDir = runDir.asFile
    jvmArgs("-Dmindustry.data.dir=${dataDir.absolutePath}")
    if(withHarness){
        jvmArgs("-Dvesper.harness=$suites", "-Dvesper.harness.speed=$speed")
        //a stuck client must not hang the build
        timeout.set(Duration.ofMinutes(90))
    }
    doFirst{
        val mods = File(dataDir, "mods")
        mods.mkdirs()
        try{
            mods.listFiles()?.forEach{ it.delete() }
            modJar.get().asFile.copyTo(File(mods, "VesperDesktop.jar"), true)
            if(withHarness) harnessFile.get().asFile.copyTo(File(mods, "VesperHarness.jar"), true)
        }catch(e: java.io.IOException){
            throw GradleException("Could not replace the mods in ${mods}: a Vesper test client is probably still running " +
                "(java.exe with -Dmindustry.data.dir=${dataDir}). Close it and run again.", e)
        }
    }
}

//dedicated server: the headless suites (also run in CI) and the local multiplayer test host
val serverDir = runDir.dir("server")
val serverJar = serverDir.file("server-release-$mindustryVersion.jar").asFile

val downloadServer = tasks.register("downloadServer"){
    group = "vesper"
    description = "Downloads the official dedicated server for $mindustryVersion into run/server (gitignored)."
    outputs.file(serverJar)
    onlyIf{ !serverJar.exists() }
    doLast{
        serverJar.parentFile.mkdirs()
        uri("https://github.com/Anuken/Mindustry/releases/download/$mindustryVersion/server-release.jar").toURL().openStream().use{ input ->
            serverJar.outputStream().use{ input.copyTo(it) }
        }
    }
}

tasks.register<JavaExec>("runServer"){
    group = "vesper"
    description = "Runs the headless harness suites on the official dedicated server (-Pharness[=suites], -PharnessSpeed=N); " +
        "exits with status 1 if a check fails. Data stays in run/server/config."
    val suites = (project.findProperty("harness") as String?)?.takeIf{ it.isNotBlank() } ?: "all"
    val speed = (project.findProperty("harnessSpeed") as String?) ?: "1"
    val modJar = tasks.jar.flatMap{ it.archiveFile }
    val harnessFile = harnessJar.flatMap{ it.archiveFile }
    dependsOn(tasks.jar, harnessJar, downloadServer)
    classpath = files(serverJar)
    mainClass.set("mindustry.server.ServerLauncher")
    workingDir = serverDir.asFile
    jvmArgs("-Dvesper.harness=$suites", "-Dvesper.harness.speed=$speed")
    timeout.set(Duration.ofMinutes(60))
    doFirst{
        val mods = File(serverDir.asFile, "config/mods")
        mods.mkdirs()
        mods.listFiles()?.forEach{ it.delete() }
        modJar.get().asFile.copyTo(File(mods, "VesperDesktop.jar"), true)
        harnessFile.get().asFile.copyTo(File(mods, "VesperHarness.jar"), true)
    }
}

tasks.register<Copy>("importMaps"){
    group = "vesper"
    description = "Copies the maps generated by the harness maps suite into assets/maps."
    from(runDir.dir("data/vesper-harness/maps")){ include("*.msav") }
    into("assets/maps")
}

//the jar packs assets/, which importMaps writes to
tasks.jar{ mustRunAfter("importMaps") }

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
