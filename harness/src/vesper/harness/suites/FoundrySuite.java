package vesper.harness.suites;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.type.*;
import vesper.*;
import vesper.harness.*;
import vesper.harness.maps.*;
import vesper.mission.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.SurveyRig.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * The Buried Foundry played start to finish on the bundled map by the scripted {@link Bot}, without cheats, with one
 * of two archaeology strategies on a common foothold (ferroprism to the core, a forge on carbon, scrap, six engines,
 * six drill- and router-fed turrets, power down the trench into the hall):
 * <ul>
 * <li><b>salvage</b>: both buried machines surveyed and dismantled, their salvage carried to the core by the hall
 * belt and spent on a stronger conventional defense (two more turrets, heavy walls) before the archive is read;</li>
 * <li><b>restore</b>: the west machine surveyed and restored (alloy and circuits carried by the unit, crystal from its
 * own extractors), then fed by the drills against it to make resonance cores, which become an Ancient Platform once
 * the archive is read.</li>
 * </ul>
 * Both must reach the native victory. The suite reports the time, the salvage or the cores, the activity and its
 * events, the losses and what was built. A third run ({@code foundry-mixed}, a diagnostic outside {@code all})
 * restores the west machine and dismantles the east one, to see whether taking both rewards dominates.
 */
public class FoundrySuite implements VesperHarness.Suite{
    /** The west machine restored (or dismantled); the east one dismantled (or left buried). */
    final boolean restoreWest, dismantleEast;
    final String tag;
    final Bot bot = new Bot();
    final ObjectIntMap<String> lost = new ObjectIntMap<>();
    boolean listening;
    float ticks, sampleTimer, levelSum, maxLevel, decided, archiveRead;
    int circuitsBefore, coresStart;

    /** Played on The Buried Foundry sector (loaded by {@link CampaignSuite}); the victory is the native capture. */
    final boolean campaign;

    /** @param mode salvage, restore or mixed */
    public FoundrySuite(String mode){
        this(mode, false);
    }

    public FoundrySuite(String mode, boolean campaign){
        tag = "foundry-" + mode;
        restoreWest = !mode.equals("salvage");
        dismantleEast = !mode.equals("restore");
        this.campaign = campaign;
    }

    /** The game declared The Buried Foundry won: a game over in a custom game, the sector's capture in the campaign. */
    boolean won(VesperHarness h){
        return campaign ? state.rules.sector != null && state.rules.sector.isCaptured() && Vesper.foundry.victory : h.lastWinner == state.rules.defaultTeam;
    }

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            Events.on(BlockDestroyEvent.class, e -> {
                if(bot.active && e.tile.team() == state.rules.defaultTeam) lost.increment(e.tile.block().name);
            });
            Events.run(Trigger.update, () -> {
                if(!bot.active || !state.isPlaying()) return;
                ticks += Time.delta;
                levelSum += Vesper.activity.level * Time.delta;
                maxLevel = Math.max(maxLevel, Vesper.activity.level);
                if((sampleTimer += Time.delta) >= 30f * 60f){
                    sampleTimer = 0f;
                    var a = Vesper.activity;
                    Log.info("[vesper-harness] @ t=@ wave @ activity @ (@) x@ industry @ ancient @ shock @ | west @ east @ archive @ | core @ | enemies @ pulses @ patrols @ | objective @/@",
                        tag, minutes(), state.wave, (int)a.level, a.stage, Strings.fixed(a.resonanceMultiplier(), 2), (int)a.industry, (int)a.ancient, (int)a.shock,
                        west().siteState, east().siteState, archive().siteState, coreSummary(), state.enemies, a.pulses, a.patrols,
                        Vesper.foundry.completed, Vesper.foundry.objectives.size);
                }
            });
        }
        var a = Vesper.activity;

        s.run(() -> {
            lost.clear();
            ticks = levelSum = maxLevel = sampleTimer = decided = archiveRead = 0f;
            if(!campaign) Worlds.play(Worlds.bundled(ContentSuite.foundryName), Gamemode.survival);
            bot.active = true;
            bot.rebuild = true;
        });
        s.wait(0.5f);
        s.check(tag + ": no cheat, no infinite resources", () -> !state.rules.infiniteResources && !state.rules.teams.get(state.rules.defaultTeam).cheat, () -> "");
        s.check(tag + ": the map loads as authored", () -> Worlds.foreignTiles() == 0 && state.rules.winWave == 0 && state.rules.waves,
            () -> Worlds.foreignTiles() + " foreign tiles");
        s.check(tag + ": scenario, archaeology and activity active; installations unexplored", () -> Vesper.scenario() == Vesper.foundry && Vesper.foundry.active()
            && a.responses() && west().siteState == SiteState.unexplored && east().siteState == SiteState.unexplored && archive().siteState == SiteState.unexplored, () -> "");
        s.check(tag + ": the Ancient Platform is locked until the archive is read", () -> state.rules.isBanned(ancientPlatform) && !state.rules.isBanned(surveyRig), () -> "");

        // --- the foothold
        phase(h, s, "ferroprism drills to the core, two router-fed turrets", () -> {
            for(int x : new int[]{29, 31, 33, 35}) bot.build(surveyDrill, x, 12);
            bot.line(transitBelt, 27, 14, 36, 14, 2);
            bot.build(cargoRouter, 26, 14);
            bot.build(transitBelt, 25, 14, 2);
            bot.build(cargoRouter, 24, 14);
            bot.build(transitBelt, 23, 14, 2);
            bot.build(transitBelt, 22, 14, 1);
            bot.build(transitBelt, 22, 15, 2);
            bot.build(kineticTurret, 26, 15);
            bot.build(kineticTurret, 24, 12);
        }, 240f);
        phase(h, s, "drill-fed turrets at the east entrance", () -> {
            for(int x : new int[]{29, 31, 33}) bot.build(surveyDrill, x, 10);
            bot.build(kineticTurret, 30, 8);
            bot.build(kineticTurret, 32, 8);
            bot.build(kineticTurret, 35, 10);
            bot.build(kineticTurret, 37, 12);
        }, 240f);
        s.check(tag + ": defenses up before the first wave", () -> state.wave <= 1, () -> "next wave in " + (int)(state.wavetime / 60f) + " s, t=" + minutes());
        phase(h, s, "scrap collectors to the core", () -> {
            bot.build(scrapCollector, 4, 20);
            bot.build(scrapCollector, 6, 20);
            bot.line(transitBelt, 4, 19, 19, 19, 0);
            bot.build(transitBelt, 20, 19, 3);
            bot.build(transitBelt, 20, 18, 3);
        }, 240f);
        phase(h, s, "combustion engines on both carbon patches and the power line down the trench", () -> {
            for(int x : new int[]{7, 9, 11}){
                bot.build(surveyDrill, x, 11);
                bot.build(combustionEngine, x, 13);
            }
            for(int x : new int[]{26, 28, 30}){
                bot.build(surveyDrill, x, 24);
                bot.build(combustionEngine, x, 26);
            }
            for(int[] n : nodes) bot.build(resonanceLink, n[0], n[1]);
        }, 480f);
        s.run(this::linkGrid);
        s.until(tag + ": the engines run", () -> state.rules.defaultTeam.data().getBuildings(combustionEngine).count(b -> b.efficiency > 0f) >= 4, 60f, null);
        //the forge: ferroprism from the router above it, carbon by a belt from a drill of its own
        phase(h, s, "a structural forge on carbon", () -> {
            bot.build(surveyDrill, 10, 9);
            bot.line(transitBelt, 12, 10, 25, 10, 0);
            bot.line(transitBelt, 26, 10, 26, 11, 1);
            bot.build(structuralForge, 26, 12);
        }, 300f);
        s.until(tag + ": the forge makes alloy", () -> state.stats.coreItemCount.get(structuralAlloy) >= 5, 120f, () -> "alloy in " + state.stats.coreItemCount.get(structuralAlloy));

        // --- the hall belt (salvage or cores to the core), then the survey of the west machine
        phase(h, s, "the hall belt down the trench", () -> {
            bot.line(transitBelt, 33, 51, 39, 51, 0);
            bot.line(transitBelt, 40, 33, 40, 51, 3);
            bot.build(transitBelt, 40, 32, 2);
            bot.line(transitBelt, 34, 32, 39, 32, 2);
            bot.line(transitBelt, 33, 18, 33, 32, 3);
            bot.build(transitBelt, 33, 17, 2);
            bot.line(transitBelt, 22, 17, 32, 17, 2);
        }, 360f);
        phase(h, s, "a survey rig at the west machine", () -> bot.build(surveyRig, 35, 52), 240f);
        s.run(this::linkGrid);
        survey(s, 35, 52, FoundrySuite::west, "the west machine", 150f);
        s.check(tag + ": the survey revealed the machine's two options", () -> ((AncientSite)west().block).hasChoices() && Vesper.foundry.completed >= 4,
            () -> "completed " + Vesper.foundry.completed);

        if(!restoreWest){
            // --- salvage: the west machine dismantled first, its salvage down the hall belt
            s.run(() -> {
                circuitsBefore = state.stats.coreItemCount.get(recoveredCircuit);
                decided = ticks;
                west().configure(AncientSite.chooseDismantle);
            });
            s.wait(0.5f);
            s.check(tag + ": the west machine is being dismantled", () -> west().siteState == SiteState.dismantling, () -> "" + west().siteState);
        }else{
            // --- restore: the west machine brought back, fed by the drills against it
            phase(h, s, "extractors and collectors against the west machine", () -> {
                bot.build(crystalExtractor, 30, 48);
                bot.build(crystalExtractor, 32, 48);
                bot.build(scrapCollector, 30, 53);
                bot.build(scrapCollector, 32, 53);
            }, 300f);
            s.run(this::linkGrid);
            s.run(() -> {
                decided = ticks;
                west().configure(AncientSite.chooseRestore);
            });
            s.wait(0.5f);
            s.check(tag + ": the west machine is being restored", () -> west().siteState == SiteState.restoring, () -> "" + west().siteState);
            s.run(() -> bot.deliver(west(), structuralAlloy, west()::remaining));
            s.until(tag + ": the alloy carried into the west machine", () -> west().remaining(structuralAlloy) == 0, 600f, () -> bot.status + ", t=" + minutes());
            s.run(() -> bot.deliver(west(), recoveredCircuit, west()::remaining));
            s.until(tag + ": the west machine is restored (circuits carried, crystal from its own extractors)", () -> west().siteState == SiteState.restored, 300f,
                () -> bot.status + ", restoration " + (int)(west().restoration() * 100) + "%");
            s.run(() -> {
                coresStart = state.stats.coreItemCount.get(resonanceCore);
                h.line("info: " + tag + " restored after " + Strings.fixed((ticks - decided) / 3600f, 1) + " min of delivery, activity " + (int)a.level + ", core: " + coreSummary());
            });
            h.shot(tag + "-01-restored", 33, 50, 3f, true);
            s.until(tag + ": the restored machine sends resonance cores down the hall belt", () -> state.stats.coreItemCount.get(resonanceCore) - coresStart >= 5, 150f,
                () -> "cores " + (state.stats.coreItemCount.get(resonanceCore) - coresStart) + ", machine efficiency " + Strings.fixed(west().efficiency, 2));
            s.until(tag + ": 20 cores ready for a platform", () -> core(resonanceCore) >= 20, 400f, () -> "cores " + core(resonanceCore) + ", t=" + minutes());
        }
        if(dismantleEast){
            // --- the east machine dismantled too, its salvage down the hall belt, then spent on defense
            int salvaged = restoreWest ? 1 : 2;
            if(restoreWest) s.run(() -> circuitsBefore = state.stats.coreItemCount.get(recoveredCircuit));
            phase(h, s, "the east belt and a survey rig at the east machine", () -> {
                bot.build(transitBelt, 81, 49, 3);
                bot.line(transitBelt, 41, 48, 81, 48, 2);
                bot.build(surveyRig, 77, 53);
            }, 420f);
            s.run(this::linkGrid);
            survey(s, 77, 53, FoundrySuite::east, "the east machine", 150f);
            s.run(() -> east().configure(AncientSite.chooseDismantle));
            s.until(tag + ": " + (salvaged == 2 ? "both machines" : "the east machine") + " dismantled, the salvage out", () -> east().siteState == SiteState.dismantled
                && (restoreWest || west().siteState == SiteState.dismantled), 240f,
                () -> "west " + west().siteState + " " + (int)(west().dismantle * 100) + "%, east " + east().siteState + " " + (int)(east().dismantle * 100) + "%");
            s.waitFor(() -> state.stats.coreItemCount.get(recoveredCircuit) - circuitsBefore >= 50 * salvaged, 60f);
            s.wait(10f);
            //the bot builds nothing that makes circuits: what reaches the core is the salvage. Never more (no duplication); a belt
            //destroyed by the waves loses what it carries, so a few may be missing
            s.check(tag + ": no salvage duplicated: at most the circuits of " + salvaged + " machine(s) reached the core (50 each)",
                () -> state.stats.coreItemCount.get(recoveredCircuit) - circuitsBefore <= 50 * salvaged,
                () -> (state.stats.coreItemCount.get(recoveredCircuit) - circuitsBefore) + " circuits");
            s.check(tag + ": the salvage reached the core by belt", () -> state.stats.coreItemCount.get(recoveredCircuit) - circuitsBefore >= 45 * salvaged,
                () -> (state.stats.coreItemCount.get(recoveredCircuit) - circuitsBefore) + " of " + 50 * salvaged + " circuits, belts lost so far " + lost.get("vesper-transit-belt", 0));
            s.check(tag + ": nothing left to salvage, nothing stored", () -> east().salvageRemaining() == 0 && east().items.total() == 0
                && (restoreWest || (west().salvageRemaining() == 0 && west().items.total() == 0)), () -> "");
            phase(h, s, "the salvage turned into defense: two more turrets and heavy walls", () -> {
                bot.build(cargoRouter, 29, 14);
                bot.build(cargoRouter, 33, 14);
                bot.build(kineticTurret, 29, 15);
                bot.build(kineticTurret, 33, 15);
                for(int y = 12; y <= 20; y += 2) bot.build(heavyWall, 44, y);
            }, 360f);
        }
        s.check(tag + ": objectives advanced to the archive", () -> Vesper.foundry.completed == 5, () -> "completed " + Vesper.foundry.completed);
        s.run(() -> h.line("info: " + tag + " decided at " + Strings.fixed(decided / 3600f, 1) + " min; now t=" + minutes() + ", wave " + state.wave
            + (Vesper.foundry.holding() ? " (holding)" : "") + ", activity " + (int)a.level + ", core: " + coreSummary()));
        h.shot(tag + "-02-hall", 44, 46, 1.6f, true);

        // --- the archive: unlocks the platform, releases the final waves
        phase(h, s, "a survey rig at the archive", () -> bot.build(surveyRig, 52, 74), 300f);
        s.run(this::linkGrid);
        survey(s, 52, 74, FoundrySuite::archive, "the archive", 240f);
        s.wait(1f);
        s.run(() -> archiveRead = ticks);
        s.check(tag + ": the Ancient Platform is unlocked and the final waves released", () -> !state.rules.isBanned(ancientPlatform) && Vesper.foundry.released
            && state.rules.winWave == BuriedFoundry.attackWaves + 1 && state.rules.waveTimer, () -> "winWave " + state.rules.winWave);
        if(restoreWest){
            phase(h, s, "an Ancient Platform at the east entrance, with accumulators", () -> {
                bot.build(powerAccumulator, 35, 22);
                bot.build(powerAccumulator, 38, 15);
                bot.build(ancientPlatform, 37, 18);
            }, 300f);
            s.run(this::linkGrid);
        }

        // --- the end
        s.step(d -> won(h) || h.lastWinner != null || ticks > 45f * 60f * 60f);
        s.until(tag + (campaign ? ": VICTORY - the game captures The Buried Foundry sector" : ": VICTORY - the game declares The Buried Foundry won"), () -> won(h), 1f,
            () -> "wave " + state.wave + ", enemies " + state.enemies + ", core " + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + " hp"));
        s.check(tag + ": the mission is complete", () -> Vesper.foundry.victory && Vesper.foundry.current() == null, () -> "");
        if(!campaign) s.check(tag + ": a custom game's victory leaves the campaign alone", () -> !vesper.campaign.Chapter.complete()
            && !vesper.campaign.Chapter.archiveRead() && !vesper.content.VSectors.buriedFoundry.sector.hasSave(), () -> "");
        s.run(() -> {
            h.line("info: " + tag + " won after " + minutes() + " of game time (archive read at " + Strings.fixed(archiveRead / 3600f, 1) + " min); mean activity "
                + Strings.fixed(levelSum / Math.max(ticks, 1f), 1) + " (peak " + (int)maxLevel + "), pulses " + a.pulses + ", patrols " + a.patrols + ", awakened " + a.awakened
                + ", core " + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + "/" + (int)state.rules.defaultTeam.core().maxHealth));
            h.line("info: " + tag + " into the core: alloy " + state.stats.coreItemCount.get(structuralAlloy) + ", circuits " + state.stats.coreItemCount.get(recoveredCircuit)
                + ", scrap " + state.stats.coreItemCount.get(conductiveScrap) + ", cores " + state.stats.coreItemCount.get(resonanceCore) + "; left: " + coreSummary());
            h.line("info: " + tag + " west " + west().siteState + ", east " + east().siteState + "; buildings lost " + lost + ", rebuilt " + bot.rebuilt);
            h.line("info: " + tag + " infrastructure: " + FaultSuite.infrastructure());
        });
        h.shot(tag + "-03-victory", 30, 20, 2f, true);
        s.run(() -> {
            bot.active = false;
            if(!headless && ui.restart.isShown()) ui.restart.hide();
        });
    }

    /** Power nodes: the engines, the trench, the west machine, the hall to the east machine, the gallery to the archive, the east entrance. */
    static final int[][] nodes = {{13, 15}, {17, 20}, {22, 24}, {25, 29}, {30, 33}, {34, 38}, {38, 43}, {36, 49},
        {44, 45}, {50, 44}, {56, 44}, {62, 44}, {68, 45}, {74, 47}, {78, 47},
        {58, 50}, {58, 56}, {57, 62}, {56, 68}, {55, 73},
        {23, 19}, {29, 19}, {35, 20}};
    static final int[][] trench = {{13, 15}, {17, 20}, {22, 24}, {25, 29}, {30, 33}, {34, 38}, {38, 43}, {36, 49}};
    static final int[][] hall = {{38, 43}, {44, 45}, {50, 44}, {56, 44}, {62, 44}, {68, 45}, {74, 47}, {78, 47}};
    static final int[][] gallery = {{56, 44}, {58, 50}, {58, 56}, {57, 62}, {56, 68}, {55, 73}};
    static final int[][] front = {{17, 20}, {23, 19}, {29, 19}, {35, 20}};

    /** Links the chains and the powered blocks next to them (what a player's node does when placed). */
    void linkGrid(){
        for(int[][] chain : new int[][][]{trench, hall, gallery, front}){
            for(int i = 0; i + 1 < chain.length; i++) FaultSuite.link(chain[i][0], chain[i][1], chain[i + 1][0], chain[i + 1][1], 1);
        }
        for(int x : new int[]{7, 9, 11}) FaultSuite.link(13, 15, x, 13, 2);
        for(int x : new int[]{26, 28, 30}) FaultSuite.link(25, 29, x, 26, 2);
        FaultSuite.link(36, 49, BuriedFoundryMap.westX, BuriedFoundryMap.westY, 3);
        FaultSuite.link(36, 49, 35, 52, 2);
        FaultSuite.link(36, 49, 30, 48, 2);
        FaultSuite.link(36, 49, 32, 48, 2);
        FaultSuite.link(78, 47, BuriedFoundryMap.eastX, BuriedFoundryMap.eastY, 3);
        FaultSuite.link(78, 47, 77, 53, 2);
        FaultSuite.link(55, 73, 52, 74, 2);
        FaultSuite.link(35, 20, 35, 22, 2);
        FaultSuite.link(35, 20, 38, 15, 2);
        FaultSuite.link(35, 20, 37, 18, 3);
    }

    /**
     * Keeps the survey rig at x,y stocked with circuits carried from the core until the site is investigated, as a
     * player reading the site's panel would: no more than the circuits it says are left (and the rig holds 10).
     * Fails after the timeout.
     */
    void survey(Script s, int x, int y, Prov<SiteBuild> site, String name, float timeout){
        float[] t = {0f};
        s.step(d -> {
            if(site.get().siteState != SiteState.unexplored || (t[0] += d) >= timeout * 60f) return true;
            if(Worlds.at(x, y, 2) instanceof SurveyRigBuild rig && !bot.delivering() && rig.items.get(recoveredCircuit) < Math.min(3, wanted(site.get()))){
                bot.deliver(rig, recoveredCircuit, item -> site.get().siteState == SiteState.unexplored ? wanted(site.get()) - rig.items.get(item) : 0);
            }
            return false;
        });
        s.until(tag + ": " + name + " is investigated", () -> site.get().siteState != SiteState.unexplored, 1f,
            () -> "survey " + (int)(site.get().investigation * 100) + "%, " + bot.status + ", t=" + minutes());
    }

    /** Circuits a rig should hold for what is left of the survey (the site's panel shows the number). */
    static int wanted(SiteBuild site){
        return Math.min(10, site.circuitsToReveal());
    }

    static SiteBuild west(){
        return (SiteBuild)Worlds.at(BuriedFoundryMap.westX, BuriedFoundryMap.westY, 3);
    }

    static SiteBuild east(){
        return (SiteBuild)Worlds.at(BuriedFoundryMap.eastX, BuriedFoundryMap.eastY, 3);
    }

    static SiteBuild archive(){
        return (SiteBuild)Worlds.at(BuriedFoundryMap.archiveX, BuriedFoundryMap.archiveY, 2);
    }

    void phase(VesperHarness h, Script s, String name, Runnable queue, float timeout){
        s.run(() -> {
            queue.run();
            h.line("phase: " + name + " (" + tag + ", t=" + minutes() + ", wave " + state.wave + ", core: " + coreSummary() + ")");
        });
        s.until(tag + ": " + name, bot::idle, timeout, () -> bot.status + ", t=" + minutes());
    }

    static int core(Item item){
        return state.rules.defaultTeam.core() == null ? -1 : state.rules.defaultTeam.core().items.get(item);
    }

    static String coreSummary(){
        return "F" + core(ferroprism) + " S" + core(conductiveScrap) + " A" + core(structuralAlloy) + " R" + core(recoveredCircuit) + " X" + core(resonanceCrystal)
            + " C" + core(resonanceCore);
    }

    String minutes(){
        return Strings.fixed(ticks / 3600f, 1) + "min";
    }
}
