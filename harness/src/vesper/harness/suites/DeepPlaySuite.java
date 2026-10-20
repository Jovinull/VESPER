package vesper.harness.suites;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import vesper.*;
import vesper.campaign.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.harness.maps.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.ContainmentField.*;
import vesper.world.ResonanceDynamo.*;
import vesper.world.SignalNode.*;
import vesper.world.SignalTerminal.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Deep Signal played start to finish on the shipped map by the scripted {@link Bot}, without cheats, on a common
 * base: drill-fed turrets at the core and at the breach entrance of the north chamber; the core line of the
 * fabrication hall (as in Resonance Works) with its cores belted into the core; an unloader over the core feeding a
 * trunk up the shaft to a hub router and three branches, each ending in two routers that feed a relay and the
 * resonance dynamo powering it; a survey rig at the terminal, supplied by the bot's unit. Then one of three strategies:
 * <ul>
 * <li><b>stable</b>: three stabilizers, each on a combustion engine and a carbon drill of its own, keep the basin under the pulse level;</li>
 * <li><b>intensive</b>: nothing holds the basin down; a pulse turret at every relay for what climbs out of the vents;</li>
 * <li><b>redundant</b>: one stabilizer (on its own engine and drill), and at every relay a containment field (fed by the
 * relay's routers) and a power accumulator.</li>
 * </ul>
 * The synchronization starts as soon as every relay runs. Each strategy must reach the native victory (a game over
 * in a custom game, the sector's capture in the campaign); the suite reports what each got and paid: the time, the
 * synchronization's interruptions and slips, the cores burned by the relays, their dynamos and the fields, the
 * level, pulses and patrols, losses and the infrastructure. No time is imposed; the comparison is the result.
 */
public class DeepPlaySuite implements VesperHarness.Suite{
    final String mode, tag;
    final boolean campaign;
    final Bot bot = new Bot();
    final ObjectIntMap<String> lost = new ObjectIntMap<>();
    boolean listening;
    float ticks, sampleTimer, levelSum, maxLevel, syncStart, syncEnd, onlineAt, slipped, lastProgress;
    int pulsesSeen, pulsesAtSync, patrolsAtSync, coresAtOnline, interruptionsSeen, stockAtOnline;

    /** @param mode stable, intensive or redundant */
    public DeepPlaySuite(String mode, boolean campaign){
        this.mode = mode;
        this.campaign = campaign;
        tag = "deep-" + mode;
    }

    public DeepPlaySuite(String mode){
        this(mode, false);
    }

    /** The game declared Deep Signal won: a game over in a custom game, the sector's capture in the campaign. */
    boolean won(VesperHarness h){
        return campaign ? state.rules.sector != null && state.rules.sector.isCaptured() && Vesper.deep.victory : h.lastWinner == state.rules.defaultTeam;
    }

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            //circuits for the survey rig, as a player carries them
            Events.run(Trigger.update, this::supplyRig);
            Events.on(BlockDestroyEvent.class, e -> {
                if(bot.active && e.tile.team() == state.rules.defaultTeam) lost.increment(e.tile.block().name);
            });
            Events.run(Trigger.update, () -> {
                if(!bot.active || !state.isPlaying()) return;
                ticks += Time.delta;
                var a = Vesper.activity;
                levelSum += a.level * Time.delta;
                maxLevel = Math.max(maxLevel, a.level);
                var t = terminal();
                if(t != null && t.syncState == SyncState.syncing){
                    if(t.progress < lastProgress) slipped += lastProgress - t.progress;
                    lastProgress = t.progress;
                }
                if((sampleTimer += Time.delta) >= 30f * 60f){
                    sampleTimer = 0f;
                    Log.info("[vesper-harness] @ t=@ wave @ activity @ (@) industry @ ancient @ stabilization @ | @ | relays @ | core cores @ | enemies @ pulses @ patrols @",
                        tag, minutes(), state.wave, (int)a.level, a.stage, (int)a.industry, (int)a.ancient, Strings.fixed(a.stabilization, 1),
                        t == null ? "-" : DeepSignalSuite.terminalState(), DeepSignalSuite.nodeStates(), core(resonanceCore), state.enemies, a.pulses, a.patrols);
                    Log.info("[vesper-harness] @ factory: @", tag, factory());
                }
            });
        }
        var a = Vesper.activity;

        s.run(() -> {
            lost.clear();
            ticks = levelSum = maxLevel = sampleTimer = syncStart = syncEnd = onlineAt = slipped = lastProgress = 0f;
            pulsesSeen = pulsesAtSync = patrolsAtSync = coresAtOnline = interruptionsSeen = 0;
            if(!campaign) Worlds.play(Worlds.bundled(ContentSuite.deepName), Gamemode.survival);
            bot.active = true;
            bot.rebuild = true;
        });
        s.wait(0.5f);
        s.check(tag + ": no cheat, no infinite resources", () -> !state.rules.infiniteResources && !state.rules.teams.get(state.rules.defaultTeam).cheat, () -> "");
        s.check(tag + ": the map loads as authored", () -> Worlds.foreignTiles() == 0 && state.rules.winWave == 0 && state.rules.waves && state.rules.lighting,
            () -> Worlds.foreignTiles() + " foreign tiles");
        s.check(tag + ": scenario, activity and planetary response active" + (campaign ? ", in the Deep Signal sector" : "; not a campaign sector"),
            () -> Vesper.scenario() == Vesper.deep && a.active() && a.responses() && terminal() != null && terminal().nodes().size == 3
            && (campaign ? state.rules.sector == VSectors.deepSignal.sector : state.rules.sector == null), () -> "");
        s.check(tag + ": starting loadout", () -> core(ferroprism) == 1600 && core(resonanceCore) == 100 && core(recoveredCircuit) == 520 && core(structuralAlloy) == 1000, () -> coreSummary());

        // --- defense: ferroprism to the core through two router-fed turrets, drill-fed turrets at the breach entrance
        phase(h, s, "ferroprism to the core through two router-fed turrets", () -> {
            bot.build(surveyDrill, 63, 5);
            bot.build(surveyDrill, 65, 5);
            bot.line(transitBelt, 65, 7, 66, 7, 2);
            bot.build(cargoRouter, 64, 7);
            bot.build(transitBelt, 63, 7, 2);
            bot.build(cargoRouter, 62, 7);
            bot.build(transitBelt, 61, 7, 2);
            bot.line(transitBelt, 60, 7, 60, 9, 1);
            bot.build(kineticTurret, 64, 8);
            bot.build(kineticTurret, 62, 8);
        }, 240f);
        phase(h, s, "drill-fed turrets at the breach entrance of the north chamber", () -> {
            bot.build(surveyDrill, 70, 81);
            bot.build(surveyDrill, 72, 81);
            bot.build(kineticTurret, 68, 81);
            bot.build(kineticTurret, 70, 79);
            bot.build(kineticTurret, 72, 79);
            bot.build(kineticTurret, 74, 81);
        }, 360f);
        s.check(tag + ": defenses up before the first wave", () -> state.wave <= 1, () -> "wave " + state.wave + ", next in " + (int)(state.wavetime / 60f) + " s, t=" + minutes());

        // --- the survey: a rig at the terminal, its power and circuits carried by the unit
        phase(h, s, "a survey rig at the terminal and a dynamo for it", () -> {
            bot.build(surveyRig, 67, 48);
            bot.build(resonanceDynamo, 69, 47);
        }, 240f);
        s.run(() -> bot.deliver(Worlds.at(69, 47, 3), resonanceCore, i -> 6 - Worlds.at(69, 47, 3).items.get(resonanceCore)));
        s.waitFor(() -> !bot.delivering(), 120f);
        //circuits for the rig: carried again whenever it runs low, until the terminal is surveyed (see supplyRig)

        // --- the core line in the fabrication hall
        phase(h, s, "two combustion engines on the hall's carbon and the first node", () -> {
            bot.build(surveyDrill, 82, 16);
            bot.build(combustionEngine, 82, 18);
            bot.build(combustionEngine, 80, 16);
            bot.build(resonanceLink, 85, 18);
        }, 240f);
        phase(h, s, "the forge between its drills, the assembler and its scrap collectors", () -> {
            bot.build(surveyDrill, 86, 10);
            bot.build(surveyDrill, 88, 8);
            bot.build(surveyDrill, 90, 10);
            bot.build(structuralForge, 88, 10);
            bot.build(componentAssembler, 88, 12);
            bot.build(scrapCollector, 86, 12);
            bot.build(scrapCollector, 86, 14);
        }, 360f);
        phase(h, s, "the resonator, its crystal extractors and belt, two factory dynamos and the nodes", () -> {
            bot.build(experimentalResonator, 91, 12);
            for(int x : new int[]{97, 99, 101}) bot.build(crystalExtractor, x, 10);
            bot.line(transitBelt, 94, 12, 102, 12, 2);
            bot.build(resonanceDynamo, 94, 13);
            bot.build(resonanceDynamo, 89, 15);
            bot.build(resonanceLink, 90, 18);
            bot.build(resonanceLink, 97, 16);
        }, 420f);
        s.run(this::linkFactory);
        s.until(tag + ": the survey rig reveals the terminal and its relays", () -> terminal().siteState == SiteState.investigated && Vesper.deep.completed >= 1, 240f,
            () -> "survey " + (int)(terminal().investigation * 100) + "%");
        //the relays wake switched on; off until the network holds enough cores for a whole synchronization (a tap on each)
        s.run(() -> terminal().nodes().each(n -> n.configure(false)));
        s.until(tag + ": the resonator makes cores", () -> Worlds.at(91, 12, 3) != null && Worlds.at(91, 12, 3).efficiency > 0f, 240f,
            () -> "resonator " + (Worlds.at(91, 12, 3) == null ? "-" : Strings.fixed(Worlds.at(91, 12, 3).efficiency, 2)));
        phase(h, s, "the core belt from the resonator, through the doorway, into the core", () -> {
            bot.line(transitBelt, 92, 15, 92, 20, 1);
            bot.line(transitBelt, 92, 21, 77, 21, 2);
            bot.line(transitBelt, 76, 21, 76, 12, 3);
            bot.line(transitBelt, 76, 11, 61, 11, 2);
        }, 420f);

        // --- core logistics: the relays' dynamos first (they are built with cores the unloader would otherwise send away),
        //then the unloader, the trunk, the hub and the branches
        phase(h, s, "the relays' dynamos and the routers under them", () -> {
            for(int[] n : DeepSignalMap.nodes){
                bot.build(resonanceDynamo, n[0] - 3, n[1]);
                bot.build(cargoRouter, n[0], n[1] - 1);
                bot.build(cargoRouter, n[0] - 1, n[1] - 1);
            }
        }, 420f);
        //each strategy's own blocks before the unloader as well (fields are built with cores too)
        if(mode.equals("stable")){
            phase(h, s, "three stabilizers, each on an engine and a carbon drill of its own", () -> {
                for(int x : new int[]{102, 104, 106}){
                    bot.build(surveyDrill, x, 18);
                    bot.build(combustionEngine, x, 20);
                    bot.build(stabilizerPylon, x, 22);
                }
            }, 360f);
        }else if(mode.equals("redundant")){
            phase(h, s, "one stabilizer on its own engine and drill, and a field and an accumulator at every relay", () -> {
                bot.build(surveyDrill, 102, 18);
                bot.build(combustionEngine, 102, 20);
                bot.build(stabilizerPylon, 102, 22);
                bot.build(containmentField, 11, 42);
                bot.build(containmentField, 97, 44);
                bot.build(containmentField, 56, 76);
                bot.build(powerAccumulator, 11, 47);
                bot.build(powerAccumulator, 99, 47);
                bot.build(powerAccumulator, 56, 81);
            }, 420f);
        }else{
            phase(h, s, "a pulse turret at every relay", () -> {
                bot.build(pulseTurret, 11, 47);
                bot.build(pulseTurret, 99, 47);
                bot.build(pulseTurret, 56, 81);
            }, 300f);
        }

        phase(h, s, "an unloader over the core and the trunk up the shaft to a hub router", () -> {
            bot.build(cargoUnloader, 58, 13, 0, resonanceCore);
            bot.line(transitBelt, 58, 14, 58, 42, 1);
            bot.build(cargoRouter, 58, 43);
        }, 420f);
        phase(h, s, "three branches from the hub: west, east, north", () -> {
            bot.line(transitBelt, 57, 43, 15, 43, 2);
            if(mode.equals("redundant")){
                bot.line(transitBelt, 59, 43, 97, 43, 0);
                bot.build(cargoRouter, 98, 43);
                bot.line(transitBelt, 99, 43, 100, 43, 0);
            }else{
                bot.line(transitBelt, 59, 43, 100, 43, 0);
            }
            bot.line(transitBelt, 58, 44, 58, 76, 1);
        }, 600f);

        // --- stock first: the relays stay off until the core, the belts and the relay stores hold half again what a
        //synchronization burns at x1 (76 cores, the same for every strategy); then on, and it starts once all three run
        s.check(tag + ": the terminal says what a synchronization costs (51 cores for three relays at x1, fewer at a louder basin's pace)",
            () -> terminal().coresPerSync() <= 51 && terminal().coresPerSync() >= 26
            && terminal().infoText().contains(Core.bundle.format("vesper.terminal.cost", (int)terminal().syncSeconds(), terminal().coresPerSync())),
            () -> terminal().coresPerSync() + " cores at resonance x" + Strings.fixed(terminal().rate(), 2));
        s.until(tag + ": the network holds the cores for a synchronization and a half", () -> stock() >= 76, 900f,
            () -> stock() + " cores in the network, t=" + minutes());
        s.run(() -> {
            stockAtOnline = stock();
            terminal().nodes().each(n -> n.configure(true));
        });
        s.until(tag + ": every relay is operational on cores from the core", () -> terminal().allOperational(), 120f, () -> DeepSignalSuite.nodeStates());
        s.run(() -> {
            onlineAt = ticks;
            coresAtOnline = core(resonanceCore);
            terminal().configure(true);
            h.line("phase: synchronization (" + tag + ", t=" + minutes() + ", activity " + (int)a.level + ", core: " + coreSummary() + ")");
        });
        s.until(tag + ": the synchronization starts", () -> terminal().syncState != SyncState.idle, 30f, () -> DeepSignalSuite.terminalState());
        s.run(() -> {
            syncStart = ticks;
            pulsesAtSync = a.pulses;
            patrolsAtSync = a.patrols;
        });
        h.shot(tag + "-01-control", 60, 45, 2f, true);
        s.wait(60f);
        h.shot(tag + "-02-north", 60, 78, 2f, true);

        s.step(d -> won(h) || h.lastWinner != null || (ticks - syncStart) > 40f * 60f * 60f);
        s.until(tag + (campaign ? ": VICTORY - the game captures the Deep Signal sector" : ": VICTORY - the game declares Deep Signal won"), () -> won(h), 1f,
            () -> DeepSignalSuite.terminalState() + ", " + DeepSignalSuite.nodeStates() + " objectives " + Vesper.deep.completed + ", core "
                + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + " hp"));
        s.check(tag + ": the network is synchronized and the mission complete", () -> terminal().syncState == SyncState.synced && Vesper.deep.victory
            && Vesper.deep.completed == 3, () -> "");
        if(!campaign) s.check(tag + ": a custom game's victory leaves the campaign alone", () -> !Chapter.secured(VSectors.deepSignal) && state.rules.sector == null
            && !Chapter.Record.centralControl.found(), () -> "");
        s.run(() -> {
            syncEnd = ticks;
            var t = terminal();
            h.line("info: " + tag + " won after " + minutes() + " of game time; relays online at " + Strings.fixed(onlineAt / 3600f, 1) + "min, synchronization "
                + Strings.fixed((syncEnd - syncStart) / 3600f, 2) + " min (150 s with no interruption), interruptions " + t.interruptions + ", slipped "
                + Strings.fixed(slipped * 100f, 1) + "%, pulses during it " + (a.pulses - pulsesAtSync) + ", patrols during it " + (a.patrols - patrolsAtSync));
            h.line("info: " + tag + " cores: relays " + nodesSpent() + ", their dynamos " + burned(true) + ", factory dynamos " + burned(false) + ", fields " + fieldSpent()
                + "; in the network when the relays came online " + stockAtOnline + ", now " + stock() + "; mean activity " + Strings.fixed(levelSum / ticks, 1)
                + " (peak " + (int)maxLevel + "), pulses " + a.pulses + ", patrols " + a.patrols + ", awakened " + a.awakened + ", waves met " + (state.wave - 1)
                + ", core " + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + "/" + (int)state.rules.defaultTeam.core().maxHealth));
            h.line("info: " + tag + " buildings lost " + lost + ", rebuilt " + bot.rebuilt);
            h.line("info: " + tag + " infrastructure: " + FaultSuite.infrastructure());
        });
        h.shot(tag + "-03-victory", 60, 45, 2f, true);
        //the cost of the whole base this strategy built (custom games only: the timings update and reload the world)
        if(!campaign) s.run(() -> {
            bot.active = false;
            var builds = state.rules.defaultTeam.data().buildings.copy();
            float[] frame = IndustryPerfSuite.time(3, 7, 10, () -> {
                for(int i = 0; i < builds.size; i++) builds.get(i).update();
            });
            var file = h.out.child(tag + "-base.msav");
            float[] save = IndustryPerfSuite.time(1, 5, 1, () -> mindustry.io.SaveIO.save(file));
            long start = System.nanoTime();
            logic.reset();
            mindustry.io.SaveIO.load(file);
            state.set(mindustry.core.GameState.State.playing);
            float load = (System.nanoTime() - start) / 1e6f;
            h.line("info: " + tag + " performance: the base's " + builds.size + " buildings update in " + IndustryPerfSuite.ms(frame[0]) + " a frame (min "
                + IndustryPerfSuite.ms(frame[1]) + "); saving it " + IndustryPerfSuite.ms(save[0]) + ", loading it " + IndustryPerfSuite.ms(load));
            h.check(tag + ": the whole base updates in under 4 ms a frame", frame[0] < 4f, IndustryPerfSuite.ms(frame[0]));
        });
        s.run(() -> {
            bot.active = false;
            if(!headless && ui.restart.isShown()) ui.restart.hide();
        });
    }

    /** Keeps the rig in circuits while the terminal is unexplored (what a player does by hand); stops for good once it is surveyed. */
    void supplyRig(){
        if(!bot.active || !state.isPlaying()) return;
        var t = terminal();
        //the rig once it stands (not while it is being built)
        if(!(Worlds.at(67, 48, 2) instanceof SurveyRig.SurveyRigBuild rig)) return;
        if(t == null || t.siteState != SiteState.unexplored || bot.delivering() || rig.items.get(recoveredCircuit) >= 3) return;
        bot.deliver(rig, recoveredCircuit, i -> terminal() != null && terminal().siteState == SiteState.unexplored ? 10 - rig.items.get(recoveredCircuit) : 0);
    }

    /** The core line at a glance: efficiency and power of its machines, what the factory dynamos hold. */
    static String factory(){
        StringBuilder out = new StringBuilder();
        Object[][] parts = {{"forge", 88, 10, 2}, {"assembler", 88, 12, 3}, {"resonator", 91, 12, 3}, {"extractor", 97, 10, 2}, {"engine", 82, 18, 2},
            {"dynamo", 94, 13, 3}, {"dynamo", 89, 15, 3}};
        for(Object[] p : parts){
            Building b = Worlds.at((int)p[1], (int)p[2], (int)p[3]);
            out.append(p[0]).append(' ');
            if(b == null){
                out.append("- ");
                continue;
            }
            out.append("eff ").append(Strings.fixed(b.efficiency, 2));
            if(b.power != null) out.append(" pow ").append(Strings.fixed(b.power.status, 2)).append(" graph ").append(b.power.graph.all.size);
            if(b.items != null) out.append(" items ").append(b.items.total());
            out.append("; ");
        }
        return out.toString();
    }

    /** Links the nodes and the powered blocks around them (what a player's node does when placed). */
    void linkFactory(){
        FaultSuite.link(85, 18, 82, 18, 2);
        FaultSuite.link(85, 18, 80, 16, 2);
        FaultSuite.link(85, 18, 88, 12, 3);
        FaultSuite.link(85, 18, 90, 18, 1);
        FaultSuite.link(90, 18, 94, 13, 3);
        FaultSuite.link(90, 18, 89, 15, 3);
        FaultSuite.link(90, 18, 97, 16, 1);
        for(int x : new int[]{97, 99, 101}) FaultSuite.link(97, 16, x, 10, 2);
    }

    static @Nullable TerminalBuild terminal(){
        return SignalTerminal.find(state.rules.defaultTeam);
    }

    /** Cores that can still reach a relay: in the core, on the belts and routers, in the relays and in the dynamos that power them. */
    static int stock(){
        int sum = 0;
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(b.items == null) continue;
            boolean relaySide = b.block == transitBelt || b.block == cargoRouter || b.block == signalNode || b.block == coreFoothold
                || (b instanceof ResonanceDynamoBuild d && d.power.graph.all.contains(o -> o instanceof NodeBuild));
            if(relaySide) sum += b.items.get(resonanceCore);
        }
        return sum;
    }

    static int nodesSpent(){
        int sum = 0;
        for(var b : state.rules.defaultTeam.data().getBuildings(signalNode)) if(b instanceof NodeBuild n) sum += n.coresSpent;
        return sum;
    }

    /** Cores burned by the dynamos that power a relay (sharing its grid), or by the others. */
    static int burned(boolean relays){
        int sum = 0;
        for(var b : state.rules.defaultTeam.data().getBuildings(resonanceDynamo)){
            if(!(b instanceof ResonanceDynamoBuild d)) continue;
            boolean relay = d.power.graph.all.contains(o -> o instanceof NodeBuild);
            if(relay == relays) sum += d.coresBurned;
        }
        return sum;
    }

    static int fieldSpent(){
        int sum = 0;
        for(var b : state.rules.defaultTeam.data().getBuildings(containmentField)) if(b instanceof FieldBuild f) sum += f.coresSpent;
        return sum;
    }

    void phase(VesperHarness h, Script s, String name, Runnable queue, float timeout){
        s.run(() -> {
            queue.run();
            h.line("phase: " + name + " (" + tag + ", t=" + minutes() + ", wave " + state.wave + ", core: " + coreSummary() + ")");
        });
        s.until(tag + ": " + name, bot::idle, timeout, () -> bot.status + ", pending " + bot.pending() + ", t=" + minutes());
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
