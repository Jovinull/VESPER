import java.time.Duration
import java.util.concurrent.TimeUnit

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
    //test fixtures (a save written by 0.2.0-alpha)
    from("harness/assets")
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
    val window = project.findProperty("harnessWindow") as String?
    val locale = project.findProperty("harnessLocale") as String?
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
        if(window != null) jvmArgs("-Dvesper.harness.window=$window")
        //the game follows the JVM locale while its own language setting is "default"; give the full locale (pt_BR, not pt)
        if(locale != null){
            val parts = locale.split("_")
            jvmArgs("-Duser.language=${parts[0]}", "-Duser.country=${parts.getOrElse(1){ "" }}")
        }
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

tasks.register("runCampaign"){
    group = "vesper"
    description = "Plays Chapter One as a campaign in a profile of its own (run/campaign, recreated unless -PcampaignKeep): " +
        "one client start per phase (-PcampaignPhases=campaign-1,...), so each phase finds what the last one saved. " +
        "Reports and screenshots go to run/campaign-reports; fails if a phase fails. -PharnessSpeed, -PharnessWindow and -PharnessLocale as runClient. " +
        "-PcampaignSeed=dir starts the new profile as a copy of dir (an older version's profile, e.g. harness/profiles/campaign-0.6)."
    val modJar = tasks.jar.flatMap{ it.archiveFile }
    val harnessFile = harnessJar.flatMap{ it.archiveFile }
    dependsOn(tasks.jar, harnessJar, downloadGame)
    doLast{
        val phases = ((project.findProperty("campaignPhases") as String?) ?: "campaign-1,campaign-2,campaign-3,campaign-4,campaign-5,campaign-6").split(",").map{ it.trim() }
        val speed = (project.findProperty("harnessSpeed") as String?) ?: "1"
        val window = project.findProperty("harnessWindow") as String?
        val locale = project.findProperty("harnessLocale") as String?
        val profile = runDir.dir("campaign").asFile
        val reports = runDir.dir("campaign-reports").asFile
        if(!project.hasProperty("campaignKeep")){
            profile.deleteRecursively()
            (project.findProperty("campaignSeed") as String?)?.let{ file(it).copyRecursively(profile, true) }
        }
        reports.mkdirs()
        val mods = File(profile, "mods")
        mods.mkdirs()
        mods.listFiles()?.forEach{ it.delete() }
        modJar.get().asFile.copyTo(File(mods, "VesperDesktop.jar"), true)
        harnessFile.get().asFile.copyTo(File(mods, "VesperHarness.jar"), true)
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        for(phase in phases){
            val args = mutableListOf(java, "-Dmindustry.data.dir=${profile.absolutePath}", "-Dvesper.harness=$phase", "-Dvesper.harness.speed=$speed")
            if(window != null) args.add("-Dvesper.harness.window=$window")
            if(locale != null){
                val parts = locale.split("_")
                args.addAll(listOf("-Duser.language=${parts[0]}", "-Duser.country=${parts.getOrElse(1){ "" }}"))
            }
            args.addAll(listOf("-cp", gameJar.absolutePath, "mindustry.desktop.DesktopLauncher"))
            val log = File(reports, "$phase.log")
            val process = ProcessBuilder(args).directory(runDir.asFile).redirectErrorStream(true).redirectOutput(log).start()
            val finished = process.waitFor(60, TimeUnit.MINUTES)
            if(!finished) process.destroy()
            val out = File(profile, "vesper-harness")
            File(out, "report.txt").takeIf{ it.exists() }?.copyTo(File(reports, "$phase-report.txt"), true)
            File(out, "shots").takeIf{ it.exists() }?.copyRecursively(File(reports, "$phase-shots"), true)
            //the profile as this phase left it, for the interface runs (runCampaignUi) and for diagnosis
            val snapshot = runDir.dir("campaign-snapshots/$phase").asFile
            snapshot.deleteRecursively()
            profile.copyRecursively(snapshot, true)
            if(!finished) throw GradleException("Campaign phase $phase timed out; see $log")
            if(process.exitValue() != 0) throw GradleException("Campaign phase $phase failed; see ${File(reports, "$phase-report.txt")}")
        }
    }
}

tasks.register("runCampaignUi"){
    group = "vesper"
    description = "The campaign's screens in the profiles runCampaign left (run/campaign-snapshots): after First Awakening, after the " +
        "chapter and after Deep Signal, in English and Portuguese, at the default window size and at 1280x720. Screenshots and reports in run/campaign-ui-reports."
    val modJar = tasks.jar.flatMap{ it.archiveFile }
    val harnessFile = harnessJar.flatMap{ it.archiveFile }
    dependsOn(tasks.jar, harnessJar, downloadGame)
    doLast{
        val reports = runDir.dir("campaign-ui-reports").asFile
        reports.deleteRecursively()
        reports.mkdirs()
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val failed = mutableListOf<String>()
        for((state, snapshotName) in listOf("campaign-ui-a" to "campaign-1", "campaign-ui-b" to "campaign-3", "campaign-ui-c" to "campaign-5")){
            val snapshot = runDir.dir("campaign-snapshots/$snapshotName").asFile
            if(!snapshot.exists()) throw GradleException("No $snapshotName profile in $snapshot: run runCampaign first")
            for(locale in listOf("en_US", "pt_BR")){
                for(window in listOf("default", "1280x720")){
                    val profile = runDir.dir("campaign-ui").asFile
                    profile.deleteRecursively()
                    snapshot.copyRecursively(profile, true)
                    val mods = File(profile, "mods")
                    mods.listFiles()?.forEach{ it.delete() }
                    modJar.get().asFile.copyTo(File(mods, "VesperDesktop.jar"), true)
                    harnessFile.get().asFile.copyTo(File(mods, "VesperHarness.jar"), true)
                    val parts = locale.split("_")
                    val args = mutableListOf(java, "-Dmindustry.data.dir=${profile.absolutePath}", "-Dvesper.harness=$state", "-Dvesper.harness.speed=1",
                        "-Duser.language=${parts[0]}", "-Duser.country=${parts[1]}")
                    if(window != "default") args.add("-Dvesper.harness.window=$window")
                    args.addAll(listOf("-cp", gameJar.absolutePath, "mindustry.desktop.DesktopLauncher"))
                    val name = "$state-$locale-$window"
                    val process = ProcessBuilder(args).directory(runDir.asFile).redirectErrorStream(true).redirectOutput(File(reports, "$name.log")).start()
                    val finished = process.waitFor(15, TimeUnit.MINUTES)
                    if(!finished) process.destroy()
                    val out = File(profile, "vesper-harness")
                    File(out, "report.txt").takeIf{ it.exists() }?.copyTo(File(reports, "$name-report.txt"), true)
                    File(out, "shots").takeIf{ it.exists() }?.copyRecursively(File(reports, "shots"), true)
                    if(!finished || process.exitValue() != 0) failed.add(name)
                }
            }
        }
        if(failed.isNotEmpty()) throw GradleException("Campaign interface checks failed: " + failed.joinToString() + " (see $reports)")
    }
}

tasks.register("runCampaignMultiplayer"){
    group = "vesper"
    description = "A campaign sector hosted by a player: the host starts from the profile campaign-1 left (run/campaign-snapshots, " +
        "run runCampaign first), plays Resonance Fault and hosts it; a second client with a clean profile joins (run/campaign-mp). " +
        "Each side records its own checks; fails if either fails. Only the processes started here are stopped."
    val modJar = tasks.jar.flatMap{ it.archiveFile }
    val harnessFile = harnessJar.flatMap{ it.archiveFile }
    dependsOn(tasks.jar, harnessJar, downloadGame)
    doLast{
        val snapshot = runDir.dir("campaign-snapshots/campaign-1").asFile
        if(!snapshot.exists()) throw GradleException("No campaign-1 profile in $snapshot: run runCampaign first")
        val mp = runDir.dir("campaign-mp").asFile
        val speed = (project.findProperty("harnessSpeed") as String?) ?: "3"
        val port = "6578"
        mp.deleteRecursively()
        val host = File(mp, "host")
        val guest = File(mp, "guest")
        snapshot.copyRecursively(host, true)
        for(profile in listOf(host, guest)){
            val mods = File(profile, "mods")
            mods.mkdirs()
            mods.listFiles()?.forEach{ it.delete() }
            modJar.get().asFile.copyTo(File(mods, "VesperDesktop.jar"), true)
            harnessFile.get().asFile.copyTo(File(mods, "VesperHarness.jar"), true)
        }
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val started = mutableListOf<Process>()
        fun launch(profile: File, phase: String, log: String, vararg extra: String): Process =
            //two clients at once: a bounded heap keeps them within a 16 GB machine running other programs
            ProcessBuilder(java, "-Xmx1g", "-Dmindustry.data.dir=${profile.absolutePath}", "-Dvesper.harness=$phase", "-Dvesper.harness.speed=$speed",
                "-Dvesper.mp.port=$port", *extra, "-cp", gameJar.absolutePath, "mindustry.desktop.DesktopLauncher")
                .directory(runDir.asFile).redirectErrorStream(true).redirectOutput(File(mp, log)).start().also{ started.add(it) }
        try{
            val hostProcess = launch(host, "campaign-mp-host", "host.log")
            //the guest keeps trying to connect until the host has landed and hosts
            Thread.sleep(20_000)
            val guestProcess = launch(guest, "campaign-mp-client", "guest.log", "-Dvesper.mp.name=Bravo")
            if(!hostProcess.waitFor(45, TimeUnit.MINUTES)) throw GradleException("The campaign host timed out; see ${File(mp, "host.log")}")
            guestProcess.waitFor(120, TimeUnit.SECONDS)
            val failed = mutableListOf<String>()
            if(hostProcess.exitValue() != 0) failed.add("host (${File(host, "vesper-harness/report.txt")})")
            if(guestProcess.isAlive || guestProcess.exitValue() != 0) failed.add("guest (${File(guest, "vesper-harness/report.txt")})")
            if(failed.isNotEmpty()) throw GradleException("Campaign multiplayer checks failed: " + failed.joinToString())
        }finally{
            started.filter{ it.isAlive }.forEach{ it.destroy() }
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

tasks.register("runMultiplayer"){
    group = "vesper"
    description = "Local multiplayer test: the dedicated server hosts First Awakening and two clients join (run/mp). " +
        "The server records the checks; fails if any check fails. Only the processes started here are stopped."
    val modJar = tasks.jar.flatMap{ it.archiveFile }
    val harnessFile = harnessJar.flatMap{ it.archiveFile }
    dependsOn(tasks.jar, harnessJar, downloadGame, downloadServer)
    doLast{
        val mp = runDir.dir("mp").asFile
        val port = "6577"
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        mp.deleteRecursively()
        val serverWork = File(mp, "server")
        val modDirs = listOf(File(serverWork, "config/mods"), File(mp, "alpha/mods"), File(mp, "bravo/mods"))
        modDirs.forEach{ dir ->
            dir.mkdirs()
            modJar.get().asFile.copyTo(File(dir, "VesperDesktop.jar"), true)
            harnessFile.get().asFile.copyTo(File(dir, "VesperHarness.jar"), true)
        }
        val started = mutableListOf<Process>()
        fun launch(log: String, dir: File, vararg args: String): Process =
            ProcessBuilder(java, *args).directory(dir).redirectErrorStream(true).redirectOutput(File(mp, log)).start().also{ started.add(it) }
        try{
            val server = launch("server.log", serverWork, "-Dvesper.harness=mp-host", "-Dvesper.mp.port=$port",
                "-cp", serverJar.absolutePath, "mindustry.server.ServerLauncher")
            val serverLog = File(mp, "server.log")
            val hostBy = System.currentTimeMillis() + 180_000
            while(!(serverLog.exists() && serverLog.readText().contains("mp: hosting"))){
                if(!server.isAlive || System.currentTimeMillis() > hostBy) throw GradleException("The server did not start hosting; see ${serverLog}")
                Thread.sleep(500)
            }
            for(name in listOf("Alpha", "Bravo")){
                //a bounded heap keeps three game processes within a 16 GB machine running other programs
                launch("${name.lowercase()}.log", runDir.asFile, "-Xmx1g", "-Dmindustry.data.dir=${File(mp, name.lowercase()).absolutePath}",
                    "-Dvesper.harness=mp-client", "-Dvesper.mp.name=$name", "-Dvesper.mp.port=$port",
                    "-cp", gameJar.absolutePath, "mindustry.desktop.DesktopLauncher")
                Thread.sleep(8_000)
            }
            if(!server.waitFor(30, TimeUnit.MINUTES)) throw GradleException("The multiplayer test timed out")
            started.drop(1).forEach{ it.waitFor(60, TimeUnit.SECONDS) }
            if(server.exitValue() != 0) throw GradleException("Multiplayer checks failed; see ${File(serverWork, "config/vesper-harness/report.txt")}")
        }finally{
            started.filter{ it.isAlive }.forEach{ it.destroy() }
        }
    }
}

tasks.register<Copy>("importMaps"){
    group = "vesper"
    description = "Copies the maps generated by the harness maps suite into assets/maps (-Pmaps=name,name for only some)."
    //a shipped map is only replaced on purpose: regenerating one changes its saved building data even when the layout is the same
    val names = (project.findProperty("maps") as String?)?.split(",")?.map{ it.trim() + ".msav" } ?: listOf("*.msav")
    from(runDir.dir("data/vesper-harness/maps")){ include(names) }
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
