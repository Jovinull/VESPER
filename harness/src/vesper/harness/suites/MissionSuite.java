package vesper.harness.suites;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.type.*;
import vesper.*;
import vesper.harness.*;
import vesper.mission.*;
import vesper.systems.*;
import vesper.world.DerelictReactor.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * First Awakening played start to finish on the bundled map by the scripted {@link Bot}, without cheats: a
 * foothold economy, two forges and an assembler, six kinetic turrets fed straight from drills, the reactor
 * restored by carrying alloy and circuits, ignition once the waves hold, a save/load in the middle of the
 * awakening countdown, and the final waves until the game declares victory.
 */
public class MissionSuite implements VesperHarness.Suite{
    final Bot bot = new Bot();
    final ObjectIntMap<String> lost = new ObjectIntMap<>();
    int expectedIncursion, enemiesBeforeIncursion;
    float pendingBefore, levelBefore;
    int alloyBeforeRecovery;
    float maxActivity;
    String before = "";
    boolean listening;
    /** Game time since the mission started, in ticks (state.tick follows real time, not the harness speed). */
    static float gameTicks;
    float sampleTimer;

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            Events.on(BlockDestroyEvent.class, e -> {
                if(bot.active && e.tile.team() == state.rules.defaultTeam) lost.increment(e.tile.block().name);
            });
            Events.run(Trigger.update, () -> {
                if(!bot.active || !state.isPlaying()) return;
                gameTicks += Time.delta;
                //Time.run tasks die with logic.reset() (the save/load test), so sample from here
                if((sampleTimer += Time.delta) >= 30f * 60f){
                    sampleTimer = 0f;
                    sampleActivity();
                }
            });
        }
        Team team = Team.sharded;

        s.run(() -> {
            lost.clear();
            gameTicks = 0f;
            maxActivity = 0f;
            Worlds.play(Worlds.bundled(ContentSuite.basinName), Gamemode.survival);
            bot.active = true;
        });
        s.wait(0.5f);
        s.check("mission: no cheat, no infinite resources", () -> !state.rules.infiniteResources && !state.rules.teams.get(team).cheat
            && !state.rules.teams.get(team).infiniteResources, () -> "");
        s.check("mission: the map loads exactly as authored (no foreign ores or blocks)", () -> Worlds.foreignTiles() == 0,
            () -> Worlds.foreignTiles() + " tiles with content from outside Vesper");
        s.check("mission: limited loadout", () -> core(ferroprism) == 300 && core(conductiveScrap) == 120 && core(structuralAlloy) == 0,
            () -> "ferroprism " + core(ferroprism) + ", scrap " + core(conductiveScrap));
        s.check("mission: native waves on, victory not armed yet", () -> state.rules.waves && state.rules.waveTimer && state.rules.winWave == 0,
            () -> "first wave in " + (int)(state.wavetime / 60f) + " s, " + state.rules.spawns.size + " spawn groups");
        s.check("mission: Vesper systems active only here", () -> Vesper.activity.active() && Vesper.mission.active() && Vesper.mission.completed == 0, () -> "");
        s.check("mission: reactor marker present", () -> state.markers.size() >= 1, () -> "");
        if(!headless) s.check("mission: HUD panel present", () -> ui.hudGroup.find("vesper-basin") != null, () -> "");

        // --- foothold: ferroprism income first, then scrap, then the alloy line
        phase(h, s, "north ferroprism drills to the core", () -> {
            for(int x : new int[]{18, 20}) bot.build(surveyDrill, x, 35);
            for(int x : new int[]{18, 20, 22}) bot.build(surveyDrill, x, 37);
            bot.line(transitBelt, 18, 39, 23, 39, 2);
            bot.line(transitBelt, 17, 35, 17, 39, 3);
            bot.line(transitBelt, 17, 34, 25, 34, 0);
            bot.line(transitBelt, 26, 28, 26, 34, 3);
        }, 150f);
        s.until("mission: ferroprism flows into the core", () -> state.stats.coreItemCount.get(ferroprism) >= 10, 60f,
            () -> "delivered " + state.stats.coreItemCount.get(ferroprism));
        phase(h, s, "scrap collectors with a router at the core", () -> {
            for(int x : new int[]{9, 11, 13}) bot.build(scrapCollector, x, 27);
            bot.line(transitBelt, 9, 26, 14, 26, 0);
            bot.line(transitBelt, 15, 26, 15, 26, 3);
            bot.line(transitBelt, 15, 25, 23, 25, 0);
            bot.build(cargoRouter, 24, 25);
            bot.line(transitBelt, 24, 24, 29, 24, 0);
        }, 150f);
        phase(h, s, "east ferroprism, carbon and the first forge", () -> {
            for(int x : new int[]{37, 39, 41}) bot.build(surveyDrill, x, 23);
            bot.build(surveyDrill, 37, 21);
            bot.line(transitBelt, 37, 25, 42, 25, 2);
            bot.line(transitBelt, 36, 23, 36, 25, 3);
            bot.line(transitBelt, 35, 22, 36, 22, 2);
            bot.build(surveyDrill, 31, 15);
            bot.build(surveyDrill, 33, 15);
            bot.line(transitBelt, 31, 17, 32, 17, 0);
            bot.line(transitBelt, 33, 17, 33, 18, 1);
            bot.line(transitBelt, 34, 17, 34, 17, 2);
            bot.build(cargoJunction, 33, 19);
            bot.line(transitBelt, 33, 20, 33, 21, 1);
            bot.build(structuralForge, 33, 22);
            bot.line(transitBelt, 33, 24, 33, 25, 1);
            bot.line(transitBelt, 28, 26, 33, 26, 2);
        }, 240f);
        s.until("mission: structural alloy reaches the core", () -> state.stats.coreItemCount.get(structuralAlloy) >= 5, 120f, null);

        phase(h, s, "two kinetic turrets fed by the east drills", () -> {
            bot.build(surveyDrill, 41, 21);
            bot.build(kineticTurret, 43, 22);
            bot.build(kineticTurret, 43, 24);
        }, 180f);
        s.check("mission: first defense up before the first wave", () -> state.wave == 1, () -> "wave counter " + state.wave + ", next wave in "
            + (int)(state.wavetime / 60f) + " s, t=" + minutes());

        phase(h, s, "combustion engines on the west carbon", () -> {
            bot.build(surveyDrill, 14, 19);
            bot.build(surveyDrill, 16, 19);
            bot.build(surveyDrill, 15, 17);
            bot.build(combustionEngine, 12, 19);
            bot.build(combustionEngine, 18, 19);
            bot.build(combustionEngine, 17, 17);
            bot.build(resonanceLink, 20, 21);
            bot.build(resonanceLink, 27, 21);
        }, 240f);
        s.run(() -> link(20, 21, 18, 19));
        s.run(() -> link(20, 21, 27, 21));
        s.until("mission: engines burn carbon", () -> team.data().getBuildings(combustionEngine).contains(b -> b.efficiency > 0f), 60f, null);

        phase(h, s, "component assembler beside the forge", () -> bot.build(componentAssembler, 30, 22), 300f);
        s.run(() -> link(27, 21, 31, 23));
        phase(h, s, "second forge on the south carbon", () -> {
            bot.build(surveyDrill, 39, 21);
            bot.build(structuralForge, 39, 19);
            bot.line(transitBelt, 37, 20, 38, 20, 0);
            bot.line(transitBelt, 41, 20, 42, 20, 2);
            for(int x : new int[]{30, 32, 34}) bot.build(surveyDrill, x, 13);
            bot.line(transitBelt, 30, 12, 35, 12, 0);
            bot.line(transitBelt, 36, 12, 36, 17, 1);
            bot.line(transitBelt, 36, 18, 38, 18, 0);
            bot.line(transitBelt, 39, 18, 39, 18, 1);
            bot.line(transitBelt, 34, 19, 38, 19, 2);
            bot.line(transitBelt, 32, 19, 32, 19, 2);
            bot.line(transitBelt, 31, 19, 31, 21, 1);
        }, 300f);
        s.until("mission: recovered circuits reach the core", () -> state.stats.coreItemCount.get(recoveredCircuit) >= 3, 240f, null);
        h.shot("mission-01-foothold-economy", 30, 26, 2.5f, true);

        phase(h, s, "turrets covering the valley and the north-east alcove", () -> {
            bot.build(kineticTurret, 22, 35);
            bot.build(kineticTurret, 24, 37);
            bot.build(surveyDrill, 51, 49);
            bot.build(surveyDrill, 53, 49);
            bot.build(surveyDrill, 52, 47);
            bot.build(kineticTurret, 54, 47);
            bot.build(kineticTurret, 49, 49);
        }, 360f);
        phase(h, s, "walls in front of the turrets", () -> {
            bot.line(reinforcedWall, 57, 45, 57, 50, 0);
            bot.line(reinforcedWall, 46, 21, 46, 26, 0);
        }, 300f);
        s.check("mission: objectives advanced to the reactor", () -> Vesper.mission.completed >= 5, () -> "completed " + Vesper.mission.completed
            + "/" + Vesper.mission.objectives.size + ", t=" + minutes());

        // --- recovery: a raid wipes out the first forge and three of its drills; the core stock must rebuild them
        s.run(() -> {
            for(int[] b : new int[][]{{33, 22}, {37, 23}, {39, 23}, {41, 23}}){
                Building build = world.build(b[0], b[1]);
                if(build != null) build.kill();
            }
            h.line("recovery: destroyed the first forge and three drills (core: " + coreSummary() + ")");
        });
        s.wait(1f);
        s.check("recovery: the first forge is gone", () -> !(world.build(33, 22) != null && world.build(33, 22).block == structuralForge), () -> "");
        s.run(() -> alloyBeforeRecovery = state.stats.coreItemCount.get(structuralAlloy));
        phase(h, s, "rebuild the first forge and its drills from the core stock", () -> {
            bot.build(structuralForge, 33, 22);
            for(int x : new int[]{37, 39, 41}) bot.build(surveyDrill, x, 23);
        }, 180f);
        s.until("recovery: alloy production resumes", () -> state.stats.coreItemCount.get(structuralAlloy) >= alloyBeforeRecovery + 10
            && world.build(33, 22) != null && world.build(33, 22).block == structuralForge, 120f,
            () -> "alloy delivered since the raid: " + (state.stats.coreItemCount.get(structuralAlloy) - alloyBeforeRecovery));

        // --- the reactor: carry alloy and circuits from the core
        s.run(() -> bot.deliver(reactor(), structuralAlloy));
        s.until("mission: 300 structural alloy carried into the reactor", () -> reactor().remaining(structuralAlloy) == 0, 900f, () -> bot.status);
        h.shot("mission-02-reactor-restoring", 26, 74, 3f, true);
        s.run(() -> bot.deliver(reactor(), recoveredCircuit));
        s.until("mission: 100 circuits carried into the reactor", () -> reactor().remaining(recoveredCircuit) == 0, 600f, () -> bot.status);
        s.until("mission: reactor primed", () -> reactor().primed(), 10f, null);

        // --- the hold: the fifth wave waits for the awakening
        s.until("mission: waves 1-4 survived and the final waves hold", () -> state.wave >= FirstAwakening.holdWave && state.enemies == 0
            && !state.rules.waveTimer && !state.rules.waveSending, 900f, () -> "wave " + state.wave + ", enemies " + state.enemies + ", t=" + minutes());
        s.check("mission: the held fifth wave is reported to the HUD", () -> Vesper.mission.holding(), () -> "holding=" + Vesper.mission.holding());
        h.shot("mission-03a-hold-explained", 30, 30, 2f, true);
        //the ignite button, as the player sees it when tapping the primed reactor
        if(!headless){
            s.run(() -> control.input.config.showConfig(reactor()));
            h.shot("mission-03b-ignite-button", 26, 79, 4f, true);
            s.run(() -> control.input.config.hideConfig());
        }
        s.run(() -> h.line("info: base losses before the awakening: " + lost + ", activity " + (int)Vesper.activity.level + " " + Vesper.activity.stage));

        // --- ignition and the awakening
        s.run(() -> {
            reactor().configure(true);
        });
        s.wait(1f);
        h.shot("mission-03-ignition", 26, 78, 3f, true);
        s.check("mission: reactor ignited and generating", () -> reactor().ignited && reactor().productionEfficiency > 0f,
            () -> "output x" + Strings.fixed(reactor().productionEfficiency, 2) + " = " + (int)(reactor().getPowerProduction() * 60f) + " power/s");
        s.check("mission: the basin awakens", () -> Vesper.activity.awakened && Vesper.activity.stage == PlanetaryActivity.Stage.awakened,
            () -> "activity " + (int)Vesper.activity.level + ", industry " + (int)Vesper.activity.industry);
        s.check("mission: awakening response announced", () -> Vesper.activity.pending > 0f && Vesper.activity.pendingAwakening,
            () -> (Vesper.activity.pendingSentinels + Vesper.activity.pendingCrawlers) + " machines in " + (int)(Vesper.activity.pending / 60f) + " s");
        s.wait(1f);
        s.check("mission: final waves released and victory armed", () -> Vesper.mission.released && state.rules.winWave == FirstAwakening.attackWaves + 1
            && state.rules.waveTimer, () -> "winWave " + state.rules.winWave + ", next wave in " + (int)(state.wavetime / 60f) + " s");
        h.shot("mission-04-awakening-warning", 26, 84, 2.5f, true);

        // --- save and load in the middle of the countdown: nothing may be lost or fire twice
        s.run(() -> {
            expectedIncursion = Vesper.activity.pendingSentinels + Vesper.activity.pendingCrawlers;
            pendingBefore = Vesper.activity.pending;
            levelBefore = Vesper.activity.level;
            before = snapshot();
            Fi file = h.out.child("first-awakening-midgame.msav");
            SaveIO.save(file);
            h.line("saved mid-countdown: " + before);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
            bot.active = true;
        });
        s.wait(0.5f);
        s.check("persistence: state identical after save and load", () -> snapshot().equals(before), () -> "after: " + snapshot());
        //the countdown keeps running while the test waits for the reload; allow the time that passed
        s.check("persistence: countdown restored", () -> Math.abs(Vesper.activity.pending - pendingBefore) <= 2f * 60f,
            () -> "before " + Strings.fixed(pendingBefore / 60f, 1) + " s, after " + Strings.fixed(Vesper.activity.pending / 60f, 1) + " s");
        s.check("persistence: activity level restored", () -> Math.abs(Vesper.activity.level - levelBefore) <= 3f,
            () -> "before " + Strings.fixed(levelBefore, 1) + ", after " + Strings.fixed(Vesper.activity.level, 1));
        s.run(() -> enemiesBeforeIncursion = state.enemies);
        s.until("persistence: the awakening response arrives exactly once", () -> Vesper.activity.pending < 0f, 60f, null);
        s.wait(0.2f);
        s.check("mission: awakening response size", () -> state.enemies - enemiesBeforeIncursion == expectedIncursion,
            () -> "spawned " + (state.enemies - enemiesBeforeIncursion) + ", expected " + expectedIncursion);
        h.shot("mission-05-ancient-machines-emerge", 26, 80, 3f, true);

        // --- the end
        //if the last enemies take long, report where they are (stuck units are a bug, not a slow defense)
        s.run(() -> stragglerTimer = 0f);
        s.step(d -> {
            if(h.lastWinner != null) return true;
            if(state.wave >= FirstAwakening.attackWaves + 1 && state.enemies > 0 && (stragglerTimer += d) >= 120f * 60f){
                stragglerTimer = 0f;
                reportEnemies(h);
            }
            return state.wave >= FirstAwakening.attackWaves + 1 && state.enemies == 0 || h.lastWinner != null || (stuckChecks += d) > 300f * 60f;
        });
        s.until("mission: VICTORY - the game declares First Awakening won", () -> h.lastWinner == state.rules.defaultTeam, 900f,
            () -> "wave " + state.wave + ", enemies " + state.enemies + ", core " + (team.core() == null ? "lost" : (int)team.core().health + " hp"));
        s.run(() -> h.line("info: won after " + minutes() + " of game time; buildings lost " + lost
            + "; peak activity " + (int)maxActivity));
        s.check("mission: final objective marked complete", () -> Vesper.mission.victory && Vesper.mission.current() == null, () -> "");
        //the mission's own announcement first, then the native game over dialog (it opens 3 s after the win)
        h.shot("mission-06-victory-announcement", 30, 30, 2f, true);
        s.wait(4f);
        h.shot("mission-07-game-over", 30, 30, 2f, true);
        s.run(() -> {
            bot.active = false;
            if(!headless && ui.restart.isShown()) ui.restart.hide();
        });
    }

    float stragglerTimer, stuckChecks;

    static void reportEnemies(VesperHarness h){
        Groups.unit.each(u -> u.team == state.rules.waveTeam, u -> h.line("straggler: " + u.type.name + " at tile " + u.tileX() + "," + u.tileY()
            + " hp " + (int)u.health + " vel " + Strings.fixed(u.vel.len(), 2) + " controller " + u.controller().getClass().getSimpleName()
            + " grounded " + u.isGrounded() + " on " + u.tileOn().floor().name + "/" + u.tileOn().block().name));
        Unit first = Groups.unit.find(u -> u.team == state.rules.waveTeam);
        if(first != null) h.look(first.x / tilesize, first.y / tilesize, 3f);
    }

    void phase(VesperHarness h, Script s, String name, Runnable queue, float timeout){
        s.run(() -> {
            queue.run();
            h.line("phase: " + name + " (t=" + minutes() + ", core: " + coreSummary() + ")");
        });
        s.until("mission: " + name, bot::idle, timeout, () -> bot.status + ", t=" + minutes());
    }

    void sampleActivity(){
        maxActivity = Math.max(maxActivity, Vesper.activity.level);
        Log.info("[vesper-harness] t=@ wave @ activity @ (@) industry @ ancient @ shock @ | @", minutes(), state.wave, (int)Vesper.activity.level,
            Vesper.activity.stage, (int)Vesper.activity.industry, (int)Vesper.activity.ancient, (int)Vesper.activity.shock, coreSummary());
    }

    static String snapshot(){
        var a = Vesper.activity;
        var r = reactor();
        return Strings.format("wave=@ winWave=@ timer=@ sending=@ awakened=@ stage=@ pending=@ incoming=@/@ patrols=@ mission=@/@ ignited=@ delivered=@ buildings=@ enemies=@",
            state.wave, state.rules.winWave, state.rules.waveTimer, state.rules.waveSending, a.awakened, a.stage,
            a.pending > 0f, a.pendingSentinels, a.pendingCrawlers, a.patrols, Vesper.mission.completed, Vesper.mission.released,
            r != null && r.ignited, r == null ? "-" : r.delivered[0] + "+" + r.delivered[1], state.rules.defaultTeam.data().buildings.size, state.enemies);
    }

    static ReactorBuild reactor(){
        return Vesper.mission.reactor();
    }

    static int core(Item item){
        return state.rules.defaultTeam.core() == null ? -1 : state.rules.defaultTeam.core().items.get(item);
    }

    static String coreSummary(){
        return "F" + core(ferroprism) + " S" + core(conductiveScrap) + " C" + core(vitreousCarbon) + " A" + core(structuralAlloy) + " R" + core(recoveredCircuit);
    }

    static String minutes(){
        return Strings.fixed(gameTicks / 3600f, 1) + "min";
    }

    static void link(int x0, int y0, int x1, int y1){
        Building from = world.build(x0, y0), to = world.build(x1, y1);
        if(from != null && to != null && !from.power.links.contains(to.pos())) from.configure(to.pos());
    }
}
