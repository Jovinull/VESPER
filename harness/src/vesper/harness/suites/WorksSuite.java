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
import vesper.harness.maps.*;
import vesper.mission.*;
import vesper.world.ContainmentField.*;
import vesper.world.ResonanceDynamo.*;
import vesper.world.WorksRelay.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Resonance Works played start to finish on the bundled map by the scripted {@link Bot}, without cheats, with one of
 * three strategies on a common base (drill-fed turrets at the core and at the Works gate; a core line on the works
 * floor: ferroprism and carbon drills into a forge, the forge and two scrap collectors into an assembler, the
 * assembler and three crystal extractors into a resonator, the resonator into two factory dynamos, and a belt carrying
 * the rest up to two dynamos beside the relay; two combustion engines start it):
 * <ul>
 * <li><b>stable</b>: two stabilizers keep the basin under the pulse level;</li>
 * <li><b>intensive</b>: nothing holds the basin down (fed, the works stir it more); two more turrets at the gate for
 * what comes out of it;</li>
 * <li><b>hybrid</b>: one stabilizer, and a containment field over the core line that keeps the pulses off it.</li>
 * </ul>
 * Each must reach the native victory. The suite reports what each got and paid: the time, the energy actually
 * delivered and its rate, the cores made and burned, the level, pulses and patrols, what the field paid and spared,
 * losses and the infrastructure. No time is imposed on a strategy; the comparison is the result.
 */
public class WorksSuite implements VesperHarness.Suite{
    final String mode, tag;
    final Bot bot = new Bot();
    final ObjectIntMap<String> lost = new ObjectIntMap<>();
    boolean listening;
    float ticks, sampleTimer, levelSum, resonanceSum, maxLevel, productionStart, starvedTicks, fedTicks;
    double deliveredAtProduction;
    int pulsesSeen, slowedPulses;

    /** @param mode stable, intensive or hybrid */
    public WorksSuite(String mode){
        this.mode = mode;
        tag = "works-" + mode;
    }

    boolean won(VesperHarness h){
        return h.lastWinner == state.rules.defaultTeam;
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
                var relay = relay();
                if(relay != null && productionStart > 0f){
                    if(relay.satisfaction() > 0.99f) fedTicks += Time.delta;
                    else if(relay.satisfaction() < 0.01f) starvedTicks += Time.delta;
                }
                if(a.pulses > pulsesSeen){
                    pulsesSeen = a.pulses;
                    //a pulse that slowed the forge: the core line ran at half speed for its 20 s
                    if(Worlds.at(34, 30, 2) != null && Worlds.at(34, 30, 2).timeScale() < 1f) slowedPulses++;
                }
                if((sampleTimer += Time.delta) >= 30f * 60f){
                    sampleTimer = 0f;
                    Log.info("[vesper-harness] @ t=@ wave @ activity @ (@) x@ industry @ ancient @ stabilization @ | delivered @/@ at @/s, burned @, dynamo stores @ | enemies @ pulses @ patrols @",
                        tag, minutes(), state.wave, (int)a.level, a.stage, Strings.fixed(a.resonanceMultiplier(), 2), (int)a.industry, (int)a.ancient,
                        Strings.fixed(a.stabilization, 1), (long)Vesper.works.delivered(), ResonanceWorks.goal, relay == null ? 0 : (int)relay.rate,
                        Vesper.works.burned(), stores(), state.enemies, a.pulses, a.patrols);
                }
            });
        }
        var a = Vesper.activity;

        s.run(() -> {
            lost.clear();
            ticks = levelSum = resonanceSum = maxLevel = sampleTimer = productionStart = starvedTicks = fedTicks = 0f;
            pulsesSeen = slowedPulses = 0;
            Worlds.play(Worlds.bundled(ContentSuite.worksName), Gamemode.survival);
            bot.active = true;
            bot.rebuild = true;
        });
        s.wait(0.5f);
        s.check(tag + ": no cheat, no infinite resources", () -> !state.rules.infiniteResources && !state.rules.teams.get(state.rules.defaultTeam).cheat, () -> "");
        s.check(tag + ": the map loads as authored", () -> Worlds.foreignTiles() == 0 && state.rules.winWave == 0 && state.rules.waves,
            () -> Worlds.foreignTiles() + " foreign tiles");
        s.check(tag + ": scenario, activity and planetary response active; not a campaign sector", () -> Vesper.scenario() == Vesper.works && a.active() && a.responses()
            && state.rules.sector == null && relay() != null && relay().delivered == 0, () -> "");
        s.check(tag + ": starting loadout", () -> core(ferroprism) == 1500 && core(resonanceCore) == 50 && core(resonanceCrystal) == 60, () -> coreSummary());

        // --- defense: ferroprism to the core through two router-fed turrets, drill-fed turrets at the Works gate
        phase(h, s, "ferroprism to the core through two router-fed turrets", () -> {
            bot.build(surveyDrill, 20, 9);
            bot.build(surveyDrill, 22, 9);
            bot.line(transitBelt, 22, 11, 23, 11, 2);
            bot.build(cargoRouter, 21, 11);
            bot.build(transitBelt, 20, 11, 2);
            bot.build(cargoRouter, 19, 11);
            bot.build(transitBelt, 18, 11, 2);
            bot.line(transitBelt, 17, 11, 17, 13, 1);
            bot.build(kineticTurret, 21, 12);
            bot.build(kineticTurret, 19, 12);
        }, 240f);
        phase(h, s, "drill-fed turrets at the Works gate", () -> {
            bot.build(surveyDrill, 36, 24);
            bot.build(surveyDrill, 38, 24);
            bot.build(kineticTurret, 34, 24);
            bot.build(kineticTurret, 36, 22);
            bot.build(kineticTurret, 38, 22);
            bot.build(kineticTurret, 40, 24);
        }, 300f);
        s.check(tag + ": defenses up before the first wave", () -> state.wave <= 1, () -> "wave " + state.wave + ", next in " + (int)(state.wavetime / 60f) + " s, t=" + minutes());

        // --- the core line on the works floor
        phase(h, s, "two combustion engines on the west carbon and the first node", () -> {
            bot.build(surveyDrill, 28, 36);
            bot.build(combustionEngine, 28, 38);
            bot.build(combustionEngine, 26, 36);
            bot.build(resonanceLink, 31, 38);
        }, 240f);
        phase(h, s, "the forge between its ferroprism and carbon drills, the assembler and its scrap collectors", () -> {
            bot.build(surveyDrill, 32, 30);
            bot.build(surveyDrill, 34, 28);
            bot.build(surveyDrill, 36, 30);
            bot.build(structuralForge, 34, 30);
            bot.build(componentAssembler, 34, 32);
            bot.build(scrapCollector, 32, 32);
            bot.build(scrapCollector, 32, 34);
        }, 360f);
        phase(h, s, "the resonator, its crystal extractors and belt, two factory dynamos and the nodes", () -> {
            bot.build(experimentalResonator, 37, 32);
            for(int x : new int[]{43, 45, 47}) bot.build(crystalExtractor, x, 30);
            bot.line(transitBelt, 40, 32, 48, 32, 2);
            bot.build(resonanceDynamo, 40, 33);
            bot.build(resonanceDynamo, 35, 35);
            bot.build(resonanceLink, 36, 38);
            bot.build(resonanceLink, 43, 36);
        }, 420f);
        s.run(this::linkGrid);
        s.until(tag + ": the forge makes alloy and the assembler circuits", () -> Worlds.at(34, 32, 3) != null && Worlds.at(34, 32, 3).efficiency > 0f, 120f,
            () -> "assembler " + (Worlds.at(34, 32, 3) == null ? "-" : Strings.fixed(Worlds.at(34, 32, 3).efficiency, 2)));
        s.until(tag + ": the resonator makes cores and the factory dynamo burns them", () -> dynamo(40, 33) != null && dynamo(40, 33).coresBurned > 0, 240f,
            () -> stores() + ", resonator " + (Worlds.at(37, 32, 3) == null ? "-" : Strings.fixed(Worlds.at(37, 32, 3).efficiency, 2)));
        phase(h, s, "the core belt up to the Works and two dynamos beside the relay", () -> {
            bot.line(transitBelt, 38, 35, 38, 47, 1);
            bot.build(cargoRouter, 38, 48);
            bot.line(transitBelt, 39, 48, 43, 48, 0);
            bot.line(transitBelt, 38, 49, 38, 50, 1);
            bot.line(transitBelt, 38, 51, 40, 51, 0);
            bot.build(resonanceDynamo, 41, 50);
            bot.build(resonanceDynamo, 44, 47);
        }, 420f);

        if(mode.equals("stable")){
            phase(h, s, "two stabilizers west of the floor", () -> {
                bot.build(stabilizerPylon, 24, 40);
                bot.build(stabilizerPylon, 27, 42);
            }, 300f);
            s.run(this::linkGrid);
        }else if(mode.equals("hybrid")){
            phase(h, s, "one stabilizer and a containment field over the core line", () -> {
                bot.build(stabilizerPylon, 27, 42);
                bot.build(containmentField, 38, 30);
            }, 300f);
            s.run(this::linkGrid);
        }else{
            phase(h, s, "two more drill-fed turrets at the Works gate", () -> {
                bot.build(kineticTurret, 36, 26);
                bot.build(kineticTurret, 38, 26);
            }, 240f);
        }
        s.until(tag + ": the relay receives power from the dynamos", () -> Vesper.works.completed >= 1, 240f,
            () -> "relay " + (relay() == null ? "-" : (int)(relay().satisfaction() * 100) + "%") + ", " + stores());
        s.run(() -> {
            productionStart = ticks;
            deliveredAtProduction = Vesper.works.delivered();
            h.line("phase: production (" + tag + ", t=" + minutes() + ", activity " + (int)a.level + ", core: " + coreSummary() + ")");
        });
        h.shot(tag + "-01-floor", 38, 36, 2.2f, true);
        s.wait(120f);
        h.shot(tag + "-02-works", 42, 44, 2f, true);

        //if it takes long, report what holds it back
        s.step(d -> won(h) || h.lastWinner != null || (ticks - productionStart) > 60f * 60f * 60f);
        s.until(tag + ": VICTORY - the game declares Resonance Works won", () -> won(h), 1f,
            () -> "delivered " + (long)Vesper.works.delivered() + "/" + ResonanceWorks.goal + ", objectives " + Vesper.works.completed + ", core "
                + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + " hp"));
        s.check(tag + ": the mission is complete and the energy is what the relay received", () -> Vesper.works.victory && Vesper.works.current() == null
            && Vesper.works.delivered() >= ResonanceWorks.goal, () -> "");
        s.check(tag + ": a standalone victory leaves the campaign alone", () -> !vesper.campaign.Chapter.complete() && state.rules.sector == null, () -> "");
        s.run(() -> {
            float production = (ticks - productionStart) / 60f;
            var relay = relay();
            h.line("info: " + tag + " won after " + minutes() + " of game time; production phase " + Strings.fixed(production / 60f, 1) + " min at "
                + Strings.fixed((float)((Vesper.works.delivered() - deliveredAtProduction) / Math.max(production, 1f)), 0) + " power/s delivered (relay full "
                + Strings.fixed(fedTicks / Math.max(ticks - productionStart, 1f) * 100f, 0) + "% of the time, dark " + Strings.fixed(starvedTicks / Math.max(ticks - productionStart, 1f) * 100f, 0) + "%)");
            h.line("info: " + tag + " cores burned by the dynamos " + Vesper.works.burned() + " (relay grid " + burnedOn(true) + ", factory " + burnedOn(false) + "), field "
                + fieldSpent() + "; mean activity " + Strings.fixed(levelSum / ticks, 1) + " (peak " + (int)maxLevel + "), mean resonance x" + Strings.fixed(resonanceSum / ticks, 2)
                + ", pulses " + a.pulses + " (" + slowedPulses + " slowed the core line), patrols " + a.patrols + ", awakened " + a.awakened + ", waves met " + (state.wave - 1)
                + ", core " + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + "/" + (int)state.rules.defaultTeam.core().maxHealth));
            h.line("info: " + tag + " buildings lost " + lost + ", rebuilt " + bot.rebuilt);
            h.line("info: " + tag + " infrastructure: " + FaultSuite.infrastructure());
        });
        h.shot(tag + "-03-victory", 38, 38, 2f, true);
        s.run(() -> {
            bot.active = false;
            if(!headless && ui.restart.isShown()) ui.restart.hide();
        });
    }

    /** Links the nodes and the powered blocks around them (what a player's node does when placed). */
    void linkGrid(){
        FaultSuite.link(31, 38, 28, 38, 2);
        FaultSuite.link(31, 38, 26, 36, 2);
        FaultSuite.link(31, 38, 34, 32, 3);
        FaultSuite.link(31, 38, 36, 38, 1);
        FaultSuite.link(36, 38, 40, 33, 3);
        FaultSuite.link(36, 38, 35, 35, 3);
        FaultSuite.link(36, 38, 43, 36, 1);
        for(int x : new int[]{43, 45, 47}) FaultSuite.link(43, 36, x, 30, 2);
        FaultSuite.link(36, 38, 38, 30, 2);
        for(int[] st : new int[][]{{24, 40}, {27, 42}}) FaultSuite.link(31, 38, st[0], st[1], 2);
    }

    static @Nullable RelayBuild relay(){
        return Vesper.works.relay();
    }

    static @Nullable ResonanceDynamoBuild dynamo(int left, int bottom){
        return Worlds.at(left, bottom, 3) instanceof ResonanceDynamoBuild d ? d : null;
    }

    /** Cores burned by the dynamos on the relay's grid, or by the others. */
    static int burnedOn(boolean relayGrid){
        int sum = 0;
        var relay = relay();
        for(var b : state.rules.defaultTeam.data().getBuildings(resonanceDynamo)){
            if(b instanceof ResonanceDynamoBuild d && (relay != null && d.power.graph == relay.power.graph) == relayGrid) sum += d.coresBurned;
        }
        return sum;
    }

    static int fieldSpent(){
        int sum = 0;
        for(var b : state.rules.defaultTeam.data().getBuildings(containmentField)) if(b instanceof FieldBuild f) sum += f.coresSpent;
        return sum;
    }

    static String stores(){
        StringBuilder out = new StringBuilder("dynamos");
        for(var b : state.rules.defaultTeam.data().getBuildings(resonanceDynamo)){
            if(b instanceof ResonanceDynamoBuild d) out.append(' ').append(d.items.get(resonanceCore)).append('/').append((int)(d.throttle * 100)).append('%');
        }
        return out.toString();
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
