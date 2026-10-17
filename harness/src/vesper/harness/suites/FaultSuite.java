package vesper.harness.suites;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import vesper.*;
import vesper.harness.*;
import vesper.mission.*;
import vesper.systems.PlanetaryActivity.*;
import vesper.world.DerelictReactor.*;
import vesper.world.Stabilizer.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Resonance Fault played start to finish on the bundled map by the scripted {@link Bot}, without cheats, with one
 * of two strategies on a common base (six engines on carbon, six crystal extractors on two fault deposits, one belt
 * to the core, six drill- and router-fed kinetic turrets):
 * <ul>
 * <li><b>stable</b>: two stabilizers hold the basin down and the reactor stays unlit; with nothing coming out of
 * the fault, the base can spread: a third deposit (three more extractors) and three more engines on the fault's
 * carbon;</li>
 * <li><b>loud</b>: the reactor is wired in and ignited, no stabilizers, and three pulse turrets guard the fault
 * where the patrols emerge.</li>
 * </ul>
 * Both must reach the native victory. The suite reports what each strategy got and paid: the time, the level,
 * the resonance multiplier, pulses, patrols, losses and the infrastructure it built. A third, diagnostic run
 * ({@code fault-greedy}, not part of {@code all}) is loud and spreads like the stable one, to see whether high
 * activity wins outright once a base also grows.
 */
public class FaultSuite implements VesperHarness.Suite{
    /** Loud: reactor lit, no stabilizers, pulse turrets. Spread: the extension to the third deposit. */
    final boolean loud, spread;
    final String tag;
    final Bot bot = new Bot();
    final ObjectIntMap<String> lost = new ObjectIntMap<>();
    boolean listening;
    float ticks, sampleTimer, levelSum, resonanceSum, maxLevel, productionStart, crystalAtProduction;
    int samples, patrolsSeen;

    public FaultSuite(boolean loud, boolean spread, String tag){
        this.loud = loud;
        this.spread = spread;
        this.tag = tag;
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
                var a = Vesper.activity;
                levelSum += a.level * Time.delta;
                resonanceSum += a.resonanceMultiplier() * Time.delta;
                maxLevel = Math.max(maxLevel, a.level);
                if((sampleTimer += Time.delta) >= 30f * 60f){
                    sampleTimer = 0f;
                    Log.info("[vesper-harness] @ t=@ wave @ activity @ (@) x@ industry @ ancient @ shock @ stabilization @ | crystal @/@ enemies @ pulses @ patrols @",
                        tag, minutes(), state.wave, (int)a.level, a.stage, Strings.fixed(a.resonanceMultiplier(), 2), (int)a.industry, (int)a.ancient, (int)a.shock,
                        Strings.fixed(a.stabilization, 1), Vesper.fault.stored(), ResonanceFault.goal, state.enemies, a.pulses, a.patrols);
                }
            });
        }
        var a = Vesper.activity;

        s.run(() -> {
            lost.clear();
            ticks = levelSum = resonanceSum = maxLevel = sampleTimer = 0f;
            Worlds.play(Worlds.bundled(ContentSuite.faultName), Gamemode.survival);
            bot.active = true;
            bot.rebuild = true;
        });
        s.wait(0.5f);
        s.check(tag + ": no cheat, no infinite resources", () -> !state.rules.infiniteResources && !state.rules.teams.get(state.rules.defaultTeam).cheat, () -> "");
        s.check(tag + ": the map loads as authored", () -> Worlds.foreignTiles() == 0 && state.rules.winWave == 0 && state.rules.waves,
            () -> Worlds.foreignTiles() + " foreign tiles");
        s.check(tag + ": scenario, activity and planetary response active", () -> Vesper.fault.active() && Vesper.scenario() == Vesper.fault && a.active() && a.responses(), () -> "");
        s.check(tag + ": the reactor starts primed and unlit", () -> reactor() != null && reactor().primed() && !reactor().ignited, () -> "");
        s.check(tag + ": starting loadout", () -> core(ferroprism) == 900 && core(recoveredCircuit) == 150 && core(resonanceCrystal) == 0, () -> coreSummary());

        // --- a foothold: ferroprism to the core, then the drill-fed turrets at the east entrance
        phase(h, s, "ferroprism drills to the core", () -> {
            for(int x : new int[]{33, 35, 37}) bot.build(surveyDrill, x, 14);
            //two routers on the way feed the turrets beside the core
            bot.line(transitBelt, 31, 16, 38, 16, 2);
            bot.build(cargoRouter, 30, 16);
            bot.line(transitBelt, 27, 16, 29, 16, 2);
            bot.build(cargoRouter, 26, 16);
            bot.line(transitBelt, 23, 16, 25, 16, 2);
            bot.build(transitBelt, 22, 16, 1);
        }, 150f);
        phase(h, s, "kinetic turrets fed by drills at the east entrance", () -> {
            bot.build(surveyDrill, 35, 12);
            bot.build(surveyDrill, 37, 12);
            bot.build(surveyDrill, 39, 14);
            bot.build(kineticTurret, 41, 14);
            bot.build(kineticTurret, 39, 12);
            bot.build(kineticTurret, 37, 10);
            bot.build(kineticTurret, 35, 10);
            bot.build(kineticTurret, 30, 17);
            bot.build(kineticTurret, 26, 14);
        }, 300f);
        s.check(tag + ": defenses up before the first wave", () -> state.wave <= 1, () -> "wave " + state.wave + ", next in " + (int)(state.wavetime / 60f) + " s, t=" + minutes());

        // --- power: combustion engines on both carbon patches, and the node chain up the valley
        phase(h, s, "combustion engines on the west carbon and the node chain", () -> {
            for(int x : new int[]{10, 12, 14}){
                bot.build(surveyDrill, x, 12);
                bot.build(combustionEngine, x, 14);
            }
            for(int[] n : nodes) bot.build(resonanceLink, n[0], n[1]);
        }, 300f);
        s.run(this::linkGrid);
        s.until(tag + ": engines burn carbon", () -> state.rules.defaultTeam.data().getBuildings(combustionEngine).count(b -> b.efficiency > 0f) >= 2, 60f, null);

        if(!loud){
            // --- stable: two stabilizers near the foothold, before the extractors start
            phase(h, s, "two stabilizers", () -> {
                bot.build(stabilizerPylon, 24, 22);
                bot.build(stabilizerPylon, 24, 26);
            }, 240f);
            s.run(() -> {
                link(25, 19, 24, 22, 2);
                link(27, 25, 24, 26, 2);
            });
            s.until(tag + ": the stabilizers work", () -> a.stabilization > 18f, 30f, () -> "stabilization " + Strings.fixed(a.stabilization, 1));
        }

        // --- the fault: six extractors on two deposits, one belt down the valley to the core
        phase(h, s, "crystal extractors and the belt to the core", () -> {
            bot.line(transitBelt, 28, 19, 28, 60, 3);
            bot.line(transitBelt, 24, 18, 28, 18, 2);
            bot.line(transitBelt, 29, 55, 47, 55, 2);
            for(int[] e : extractors) bot.build(crystalExtractor, e[0], e[1]);
        }, 420f);
        s.run(this::linkGrid);
        phase(h, s, "combustion engines on the south carbon", () -> {
            for(int x : new int[]{28, 30, 32}){
                bot.build(surveyDrill, x, 8);
                bot.build(combustionEngine, x, 10);
            }
        }, 300f);
        s.run(this::linkGrid);
        s.until(tag + ": the extractors work", () -> state.rules.defaultTeam.data().getBuildings(crystalExtractor).count(b -> b.efficiency > 0f) >= 6, 60f,
            () -> state.rules.defaultTeam.data().getBuildings(crystalExtractor).count(b -> b.efficiency > 0f) + " working");
        s.until(tag + ": crystal reaches the core", () -> Vesper.fault.stored() >= 10, 120f, () -> "stored " + Vesper.fault.stored());
        s.check(tag + ": objectives advanced to storing crystal", () -> Vesper.fault.completed == 2, () -> "completed " + Vesper.fault.completed);

        if(spread){
            // --- stable: nothing comes out of a quiet fault, so the base spreads along it
            phase(h, s, "a third deposit and engines on the fault's carbon", () -> {
                for(int[] n : nodesEast) bot.build(resonanceLink, n[0], n[1]);
                bot.line(transitBelt, 29, 60, 65, 60, 2);
                for(int x : new int[]{59, 61, 63}) bot.build(crystalExtractor, x, 61);
                for(int x : new int[]{67, 69, 71}){
                    bot.build(surveyDrill, x, 53);
                    bot.build(combustionEngine, x, 55);
                }
            }, 420f);
            s.run(this::linkEast);
            s.until(tag + ": nine extractors work", () -> state.rules.defaultTeam.data().getBuildings(crystalExtractor).count(b -> b.efficiency > 0f) >= 9, 60f,
                () -> state.rules.defaultTeam.data().getBuildings(crystalExtractor).count(b -> b.efficiency > 0f) + " working");
            if(!loud) s.check(tag + ": still quiet: under the pulse level", () -> a.level < a.pulseLevel, () -> "activity " + Strings.fixed(a.level, 1));
        }

        if(loud){
            // --- loud: pulse turrets where the patrols come out, then the reactor
            s.until(tag + ": crystal for the pulse turrets", () -> Vesper.fault.stored() >= 50, 240f, () -> "stored " + Vesper.fault.stored());
            phase(h, s, "pulse turrets guarding the fault", () -> {
                bot.build(pulseTurret, 24, 52);
                bot.build(pulseTurret, 31, 51);
                bot.build(pulseTurret, 37, 51);
                bot.build(resonanceLink, 50, 50);
            }, 240f);
            s.run(this::linkGrid);
            s.run(() -> {
                link(50, 50, 52, 45, 4);
                reactor().configure(true);
            });
            s.wait(1f);
            s.check(tag + ": the reactor is lit and feeds the grid", () -> reactor().ignited && reactor().power.graph == world.build(27, 54).power.graph,
                () -> "output " + (int)(reactor().getPowerProduction() * 60f) + " power/s");
        }
        s.run(() -> {
            productionStart = ticks;
            crystalAtProduction = Vesper.fault.stored();
            h.line("phase: production (" + tag + ", t=" + minutes() + ", activity " + (int)a.level + ", core: " + coreSummary() + ")");
        });
        h.shot(tag + "-01-base", 28, 30, 2f, true);
        s.wait(120f);
        h.shot(tag + "-02-fault", 34, 54, 2.5f, true);

        //if it takes long, report where the enemies are
        s.step(d -> h.lastWinner != null || (ticks - productionStart) > 40f * 60f * 60f);
        s.until(tag + ": VICTORY - the game declares Resonance Fault won", () -> h.lastWinner == state.rules.defaultTeam, 1f,
            () -> "stored " + Vesper.fault.stored() + "/" + ResonanceFault.goal + ", core " + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + " hp"));
        s.check(tag + ": the mission is complete", () -> Vesper.fault.victory && Vesper.fault.current() == null, () -> "");
        s.run(() -> {
            float production = (ticks - productionStart) / 60f;
            h.line("info: " + tag + " won after " + minutes() + " of game time; production phase " + Strings.fixed(production / 60f, 1) + " min at "
                + Strings.fixed((ResonanceFault.goal - crystalAtProduction) / Math.max(production, 1f), 2) + " crystal/s");
            h.line("info: " + tag + " mean activity " + Strings.fixed(levelSum / ticks, 1) + " (peak " + (int)maxLevel + "), mean resonance x"
                + Strings.fixed(resonanceSum / ticks, 2) + ", pulses " + a.pulses + ", patrols " + a.patrols + ", awakened " + a.awakened
                + ", waves met " + (state.wave - 1) + ", core " + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + "/" + (int)state.rules.defaultTeam.core().maxHealth));
            h.line("info: " + tag + " buildings lost " + lost + ", rebuilt " + bot.rebuilt);
            h.line("info: " + tag + " infrastructure: " + infrastructure());
        });
        h.shot(tag + "-03-victory", 28, 30, 2f, true);
        s.run(() -> {
            bot.active = false;
            if(!headless && ui.restart.isShown()) ui.restart.hide();
        });
    }

    /** Power nodes: the engines, then up the valley to the fault deposits (bottom-left tiles; 1x1). */
    static final int[][] nodes = {{16, 14}, {23, 13}, {30, 13}, {25, 19}, {27, 25}, {27, 31}, {27, 37}, {27, 43}, {27, 49}, {27, 54}, {33, 53}, {39, 53}, {45, 53}};
    /** Extractors: three on the west deposit beside the belt, three on the next deposit above the branch belt. */
    static final int[][] extractors = {{26, 57}, {26, 59}, {29, 57}, {40, 56}, {42, 56}, {44, 56}};

    /** The stable base's extension along the fault: nodes to the third deposit and the fault's carbon. */
    static final int[][] nodesEast = {{51, 56}, {57, 58}, {63, 58}, {69, 58}};

    void linkEast(){
        link(45, 53, 51, 56, 1);
        for(int i = 0; i + 1 < nodesEast.length; i++) link(nodesEast[i][0], nodesEast[i][1], nodesEast[i + 1][0], nodesEast[i + 1][1], 1);
        link(57, 58, 59, 61, 2);
        link(63, 58, 61, 61, 2);
        link(63, 58, 63, 61, 2);
        for(int x : new int[]{67, 69, 71}) link(69, 58, x, 55, 2);
    }

    /** What the player built: block counts and their total cost. */
    static String infrastructure(){
        ObjectIntMap<String> counts = new ObjectIntMap<>();
        ObjectIntMap<Item> cost = new ObjectIntMap<>();
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(b.block == coreFoothold || b.block == derelictReactor) continue;
            counts.increment(b.block.name.replace("vesper-", ""));
            for(ItemStack stack : b.block.requirements) cost.increment(stack.item, stack.amount);
        }
        StringBuilder out = new StringBuilder();
        for(var e : counts) out.append(e.key).append('=').append(e.value).append(' ');
        out.append("| cost");
        for(var e : cost) out.append(' ').append(e.key.name.replace("vesper-", "")).append(' ').append(e.value);
        return out.toString();
    }

    /** Links each node to the next and to the powered blocks around it (what a player's node does when placed). */
    void linkGrid(){
        for(int i = 0; i + 1 < nodes.length; i++){
            int next = i == 1 ? 3 : i + 1;
            link(nodes[i][0], nodes[i][1], nodes[next][0], nodes[next][1], 1);
        }
        link(23, 13, 30, 13, 1);
        for(int x : new int[]{10, 12, 14}) link(16, 14, x, 14, 2);
        for(int x : new int[]{28, 30, 32}) link(30, 13, x, 10, 2);
        link(27, 54, 26, 57, 2);
        link(27, 54, 26, 59, 2);
        link(27, 54, 29, 57, 2);
        link(39, 53, 40, 56, 2);
        link(39, 53, 42, 56, 2);
        link(45, 53, 44, 56, 2);
        link(27, 49, 24, 52, 2);
        link(27, 54, 31, 51, 2);
        link(39, 53, 37, 51, 2);
        link(45, 53, 50, 50, 1);
    }

    static void link(int x0, int y0, int left, int bottom, int size){
        Building from = world.build(x0, y0), to = Worlds.at(left, bottom, size);
        if(from != null && to != null && from != to && !from.power.links.contains(to.pos())) from.configure(to.pos());
    }

    void phase(VesperHarness h, Script s, String name, Runnable queue, float timeout){
        s.run(() -> {
            queue.run();
            h.line("phase: " + name + " (" + tag + ", t=" + minutes() + ", core: " + coreSummary() + ")");
        });
        s.until(tag + ": " + name, bot::idle, timeout, () -> bot.status + ", t=" + minutes());
    }

    static ReactorBuild reactor(){
        return Vesper.mission.reactor();
    }

    static int core(Item item){
        return state.rules.defaultTeam.core() == null ? -1 : state.rules.defaultTeam.core().items.get(item);
    }

    static String coreSummary(){
        return "F" + core(ferroprism) + " S" + core(conductiveScrap) + " A" + core(structuralAlloy) + " R" + core(recoveredCircuit) + " X" + core(resonanceCrystal);
    }

    String minutes(){
        return Strings.fixed(ticks / 3600f, 1) + "min";
    }
}
