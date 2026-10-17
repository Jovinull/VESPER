package vesper.harness.suites;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.type.*;
import mindustry.world.*;
import vesper.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.systems.*;
import vesper.systems.PlanetaryActivity.*;
import vesper.world.ResonantCrafter.*;
import vesper.world.ResonantDrill.*;
import vesper.world.Stabilizer.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * The planetary response systems in a purpose-built world: stabilizers (on, off, unpowered, diminishing returns,
 * destroyed and rebuilt with their setting), the stage thresholds, the resonance bonus measured on a real extractor
 * and resonator, resonance pulses (announcement, boost, slowdown, overload, flare, cooldown), patrols that scale
 * with the level (size, cooldown, unit cap), two events at once, save/load in each of those states, and nothing of
 * it outside Vesper maps or on the builders' derelict pylons; the cost of the per-second sample with a crowded base. Last, a save written by 0.2.0-alpha (First Awakening in
 * the middle of the awakening countdown) loads with its state intact and plays on.
 * <p>
 * Test fixtures, not gameplay: the level is set by holding the surge value, enemy units deal no damage, and pulses
 * are held back while the bonus and the thresholds are measured. Everything else runs through the real systems.
 */
public class ResponseSuite implements VesperHarness.Suite{
    static final int width = 64, height = 40;
    static final Team team = Team.sharded;
    /** Stabilizers by bottom-left tile: 1-5 in a row outside the pulse, 6 inside it, 0 unpowered. */
    static final int[][] stabilizerTiles = {{30, 22}, {10, 22}, {13, 22}, {16, 22}, {19, 22}, {22, 22}, {8, 9}};
    static final int gateX = 50, gateY = 8;

    final Bot bot = new Bot();
    /** Fixture: while >= 0, the surge is held at this value (so the level settles where a test needs it). */
    float holdShock = -1f;
    boolean listening;
    final FloatSeq totals = new FloatSeq();
    int crystalStart, coresStart, lowCrystal, lowCores, pulsesBefore, enemiesBefore, expected;
    float shockBefore, pendingBefore, pulseBefore;
    String before = "";

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            Events.run(Trigger.update, () -> {
                if(holdShock >= 0f && Vesper.activity.active() && !net.client()) Vesper.activity.shock = holdShock;
            });
        }
        var a = Vesper.activity;

        s.run(() -> {
            holdShock = 0f;
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.teams.get(r.waveTeam).unitDamageMultiplier = 0f;
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagResponse, "true");
            r.tags.put(PlanetaryActivity.tagGates, gateX + "," + gateY);
            Worlds.generate(width, height, ResponseSuite::generate, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            link(9, 7, 7, 4, 2);
            link(9, 7, 4, 7, 3);
            link(9, 7, 8, 9, 2);
            for(int i = 1; i <= 3; i++) link(14, 25, stabilizerTiles[i][0], stabilizerTiles[i][1], 2);
            for(int i = 4; i <= 5; i++) link(20, 25, stabilizerTiles[i][0], stabilizerTiles[i][1], 2);
            world.build(3, 7).configureAny(resonanceCrystal);
            world.build(3, 9).configureAny(recoveredCircuit);
            for(int i = 0; i < stabilizerTiles.length; i++) stabilizer(i).configureAny(false);
        });
        s.wait(2f);
        s.check("response: the test map has planetary activity and the planetary response", () -> a.active() && a.responses() && Vesper.scenario() == null, () -> "");

        // --- stabilizers
        s.check("stabilizer: switched off it takes nothing off and draws no power", () -> a.stabilization == 0f && stabilizer(1).contribution == 0f
            && !stabilizer(1).shouldConsume() && stabilizer(1).efficiency == 0f, () -> "stabilization " + a.stabilization + ", efficiency " + stabilizer(1).efficiency);
        s.run(() -> stabilizer(1).configureAny(true));
        s.wait(2f);
        s.check("stabilizer: switched on and powered it takes 12 off", () -> near(a.stabilization, 12f, 0.05f) && near(stabilizer(1).contribution, 12f, 0.05f),
            () -> "stabilization " + a.stabilization + ", contribution " + stabilizer(1).contribution);
        s.run(() -> stabilizer(0).configureAny(true));
        s.wait(2f);
        s.check("stabilizer: without power it takes nothing off", () -> stabilizer(0).online && stabilizer(0).stabilizerOperation() == 0f
            && stabilizer(0).contribution == 0f && near(a.stabilization, 12f, 0.05f), () -> "unpowered contribution " + stabilizer(0).contribution + ", total " + a.stabilization);
        s.run(() -> {
            totals.clear();
            totals.add(a.stabilization);
        });
        for(int i = 2; i <= 5; i++){
            int index = i;
            s.run(() -> stabilizer(index).configureAny(true));
            s.wait(2f);
            s.run(() -> totals.add(a.stabilization));
        }
        s.check("stabilizer: each further one adds less (12, 7.2, 4.3, 2.6, 1.6) and five stay under the cap of 30", () -> {
            float[] expected = {12f, 19.2f, 23.52f, 26.11f, 27.67f};
            for(int i = 0; i < expected.length; i++) if(!near(totals.get(i), expected[i], 0.1f)) return false;
            return totals.peek() < a.stabilizerStrength / (1f - a.stabilizerFalloff);
        }, () -> "totals " + totals);
        s.check("stabilizer: the contributions add up to the total", () -> near(sumContributions(), a.stabilization, 0.05f),
            () -> "sum " + sumContributions() + ", total " + a.stabilization);

        //the settings and contributions are saved
        s.run(() -> stabilizer(5).configureAny(false));
        s.wait(2f);
        saveLoad(h, s, "stabilizers");
        s.check("persistence: stabilizer settings survive save and load", () -> stabilizer(1).online && !stabilizer(5).online && stabilizer(0).online,
            () -> stabilizerStates());
        s.wait(2f);
        s.check("persistence: stabilization recomputed the same after load", () -> near(a.stabilization, 26.11f, 0.1f), () -> "stabilization " + a.stabilization);

        // --- stage thresholds (pulses held back)
        s.run(() -> {
            for(int i = 1; i <= 6; i++) stabilizer(i).configureAny(false);
            a.pulseCooldown = 1e9f;
            holdShock = 5f;
        });
        s.waitFor(() -> a.level < 20f, 60f);
        s.check("stage: dormant below 30", () -> a.stage == Stage.dormant, () -> "level " + Strings.fixed(a.level, 1) + " " + a.stage);
        s.run(() -> holdShock = 35f);
        s.waitFor(() -> a.level >= 40f, 30f);
        s.check("stage: stirring from 30", () -> a.stage == Stage.stirring, () -> "level " + Strings.fixed(a.level, 1) + " " + a.stage);
        s.run(() -> holdShock = 70f);
        s.waitFor(() -> a.level >= 75f, 30f);
        s.check("stage: awakened from 70, with the awakening response announced", () -> a.stage == Stage.awakened && a.awakened && a.pending > 0f && a.pendingAwakening,
            () -> "level " + Strings.fixed(a.level, 1) + " " + a.stage + ", pending " + a.pending);
        s.run(() -> holdShock = 58f);
        s.waitFor(() -> a.level <= 68.5f, 40f);
        s.check("stage: still awakened at 68 (hysteresis)", () -> a.stage == Stage.awakened, () -> "level " + Strings.fixed(a.level, 1) + " " + a.stage);
        s.run(() -> holdShock = 50f);
        s.waitFor(() -> a.level < 66.5f, 40f);
        s.check("stage: back to stirring under 67", () -> a.stage == Stage.stirring, () -> "level " + Strings.fixed(a.level, 1) + " " + a.stage);
        s.waitFor(() -> a.pending < 0f, 40f);
        s.run(() -> killEnemies());
        //stabilizers bring a loud base down: 4 on take 26 off a 60 target, the level falls 0.5 per second
        s.run(() -> {
            holdShock = 50f;
            for(int i = 1; i <= 4; i++) stabilizer(i).configureAny(true);
            pendingBefore = Time.time;
        });
        s.until("stage: four stabilizers bring the level from 60 down under 40", () -> a.level < 40f, 120f,
            () -> "level " + Strings.fixed(a.level, 1) + ", stabilization " + Strings.fixed(a.stabilization, 1));

        // --- the resonance bonus: same extractor and resonator, level 0 then 100 (pulses held back)
        s.run(() -> {
            for(int i = 1; i <= 5; i++) stabilizer(i).configureAny(true);
            holdShock = 0f;
        });
        s.waitFor(() -> a.level <= 0.5f, 120f);
        s.wait(10f);
        s.check("bonus: x1 at level 0", () -> near(a.resonanceMultiplier(), 1f, 0.001f) && near(extractor().resonance(), 1f, 0.001f),
            () -> "level " + Strings.fixed(a.level, 1) + ", multiplier " + a.resonanceMultiplier());
        s.run(() -> {
            crystalStart = state.stats.coreItemCount.get(resonanceCrystal);
            coresStart = state.stats.coreItemCount.get(resonanceCore);
        });
        s.wait(90f);
        s.run(() -> {
            lowCrystal = state.stats.coreItemCount.get(resonanceCrystal) - crystalStart;
            lowCores = state.stats.coreItemCount.get(resonanceCore) - coresStart;
            h.line("info: in 90 s at level 0: extractor " + lowCrystal + " crystal, resonator " + lowCores + " cores");
            for(int i = 1; i <= 5; i++) stabilizer(i).configureAny(false);
            holdShock = 100f;
        });
        s.waitFor(() -> a.level >= 99.5f, 120f);
        s.wait(10f);
        s.check("bonus: x2 at level 100", () -> near(a.resonanceMultiplier(), 2f, 0.01f) && near(extractor().resonance(), 2f, 0.01f)
            && near(resonator().resonance(), 2f, 0.01f), () -> "level " + Strings.fixed(a.level, 1) + ", multiplier " + a.resonanceMultiplier());
        s.run(() -> {
            crystalStart = state.stats.coreItemCount.get(resonanceCrystal);
            coresStart = state.stats.coreItemCount.get(resonanceCore);
        });
        s.wait(90f);
        s.check("bonus: the extractor mines twice as fast at 100 (measured)", () -> ratio(state.stats.coreItemCount.get(resonanceCrystal) - crystalStart, lowCrystal, 2f),
            () -> "90 s: " + lowCrystal + " crystal at 0, " + (state.stats.coreItemCount.get(resonanceCrystal) - crystalStart) + " at 100");
        s.check("bonus: the resonator works twice as fast at 100 (measured)", () -> ratio(state.stats.coreItemCount.get(resonanceCore) - coresStart, lowCores, 2f),
            () -> "90 s: " + lowCores + " cores at 0, " + (state.stats.coreItemCount.get(resonanceCore) - coresStart) + " at 100");
        s.run(() -> killEnemies());

        // --- resonance pulses
        s.run(() -> {
            holdShock = 65f;
            a.cooldown = 1e9f;
            stabilizer(1).configureAny(true);
            stabilizer(6).configureAny(true);
        });
        s.waitFor(() -> a.level <= 62f, 120f);
        s.run(() -> a.pulseCooldown = 0f);
        s.until("pulse: announced from 50 at the resonance machine nearest the gate", () -> a.pulsePending > 0f, 10f,
            () -> "level " + Strings.fixed(a.level, 1) + ", epicentre " + a.pulseX + "," + a.pulseY);
        s.check("pulse: aimed at the extractor, 20 s ahead", () -> a.pulseX == extractor().tileX() && a.pulseY == extractor().tileY() && a.pulsePending > 18f * 60f,
            () -> "epicentre " + a.pulseX + "," + a.pulseY + ", lands in " + Strings.fixed(a.pulsePending / 60f, 1) + " s");
        s.check("pulse: nothing happens before it lands", () -> !a.inPulse(extractor()) && forge(10, 4).timeScale() == 1f && stabilizer(6).stabilizerOperation() > 0f, () -> "");
        s.run(() -> {
            holdShock = -1f;
            a.shock = 40f;
            pulsesBefore = a.pulses;
        });
        s.waitFor(() -> a.pulsePending <= 30f, 30f);
        s.run(() -> shockBefore = a.shock);
        s.until("pulse: lands when announced", () -> a.pulseActive > 0f, 5f, null);
        s.check("pulse: the flare lifts the surge by 10", () -> a.pulses == pulsesBefore + 1 && a.shock - shockBefore >= 9.5f,
            () -> "surge " + Strings.fixed(shockBefore, 1) + " -> " + Strings.fixed(a.shock, 1));
        s.check("pulse: resonance machines inside run x1.5 on top of the level", () -> near(extractor().resonance(), a.resonanceMultiplier() * a.pulseBoost, 0.01f)
            && near(resonator().resonance(), a.resonanceMultiplier() * a.pulseBoost, 0.01f), () -> "extractor x" + Strings.fixed(extractor().resonance(), 2));
        s.check("pulse: ordinary industry inside slows to half, outside it does not", () -> near(forge(10, 4).timeScale(), 0.5f, 0.001f) && forge(40, 30).timeScale() == 1f,
            () -> "inside " + forge(10, 4).timeScale() + ", outside " + forge(40, 30).timeScale());
        s.wait(1.5f);
        s.check("pulse: a stabilizer inside is overloaded, one outside keeps working", () -> stabilizer(6).stabilizerOperation() == 0f && stabilizer(6).contribution == 0f
            && stabilizer(1).stabilizerOperation() > 0f && near(a.stabilization, 12f, 0.05f), () -> stabilizerStates());
        //save and load in the middle of the pulse
        s.run(() -> pulseBefore = a.pulseActive);
        saveLoad(h, s, "pulse");
        s.check("persistence: the running pulse survives save and load", () -> a.pulseActive > 0f && Math.abs(a.pulseActive - pulseBefore) <= 3f * 60f
            && near(forge(10, 4).timeScale(), 0.5f, 0.001f), () -> "pulse " + Strings.fixed(pulseBefore / 60f, 1) + " s -> " + Strings.fixed(a.pulseActive / 60f, 1)
            + " s, forge time scale " + forge(10, 4).timeScale());
        s.until("pulse: ends after its 20 s", () -> a.pulseActive < 0f, 25f, null);
        s.wait(1f);
        s.check("pulse: everything back to normal", () -> !a.inPulse(extractor()) && forge(10, 4).timeScale() == 1f && stabilizer(6).stabilizerOperation() > 0f,
            () -> "forge " + forge(10, 4).timeScale() + ", stabilizer " + stabilizer(6).stabilizerOperation());
        s.check("pulse: cooldown set from the level (150 s at 50 to 75 s at 100)", () -> a.pulseCooldown > 60f * 60f && a.pulseCooldown <= 150f * 60f,
            () -> Strings.fixed(a.pulseCooldown / 60f, 0) + " s");
        s.run(() -> holdShock = 40f);
        s.wait(30f);
        s.check("pulse: no new pulse during the cooldown", () -> a.pulses == pulsesBefore + 1 && a.pulsePending < 0f, () -> "pulses " + a.pulses);

        // --- patrols that scale with the level
        s.run(() -> {
            for(int i = 1; i <= 6; i++) stabilizer(i).configureAny(false);
            a.pulseCooldown = 1e9f;
            holdShock = 85f;
        });
        //a patrol is sized when it is announced: let anything announced earlier arrive, then hold patrols until the level is up
        s.waitFor(() -> a.pending < 0f, 30f);
        s.run(() -> {
            killEnemies();
            a.cooldown = 1e9f;
        });
        s.waitFor(() -> a.level >= 94.5f, 60f);
        s.run(() -> a.cooldown = 0f);
        s.until("patrol: scheduled once the cooldown ends", () -> a.pending > 0f && !a.pendingAwakening, 5f, null);
        s.check("patrol: larger at a higher level (6 crawlers and 2 sentinels at 95)", () -> a.pendingCrawlers == 6 && a.pendingSentinels == 2,
            () -> a.pendingCrawlers + " crawlers, " + a.pendingSentinels + " sentinels at level " + Strings.fixed(a.level, 1));
        s.run(() -> {
            expected = a.pendingCrawlers + a.pendingSentinels;
            enemiesBefore = state.enemies;
        });
        s.until("patrol: emerges after its warning", () -> a.pending < 0f, 25f, null);
        s.wait(0.3f);
        s.check("patrol: exactly its size emerges", () -> state.enemies - enemiesBefore == expected, () -> (state.enemies - enemiesBefore) + " of " + expected);
        s.check("patrol: next one sooner at a higher level (about 70 s at 95)", () -> near(a.cooldown / 60f, 70f, 3f), () -> Strings.fixed(a.cooldown / 60f, 1) + " s");
        //the cap: no patrol while 12 hostile machines are alive
        s.run(() -> {
            killEnemies();
            for(int i = 0; i < a.responseUnitCap; i++) VUnits.rustCrawler.spawn(state.rules.waveTeam, 56f * tilesize, 30f * tilesize);
        });
        //the enemy count is refreshed once per frame: release the cooldown only after it has seen the new units
        s.wait(1f);
        s.run(() -> a.cooldown = 0f);
        s.wait(3f);
        s.check("patrol: none while the unit cap is reached", () -> a.pending < 0f && state.enemies >= a.responseUnitCap, () -> state.enemies + " enemies");
        s.run(() -> killEnemies());
        s.until("patrol: scheduled again once the machines are gone", () -> a.pending > 0f, 5f, null);
        s.waitFor(() -> a.pending < 0f, 25f);
        s.run(() -> killEnemies());

        // --- two events at once, saved and loaded: both arrive exactly once
        s.run(() -> {
            a.cooldown = 0f;
            a.pulseCooldown = 0f;
            holdShock = 85f;
        });
        s.until("events: a patrol and a pulse announced together", () -> a.pending > 0f && a.pulsePending > 0f, 5f,
            () -> "patrol " + Strings.fixed(a.pending / 60f, 1) + " s, pulse " + Strings.fixed(a.pulsePending / 60f, 1) + " s");
        s.run(() -> {
            expected = a.pendingCrawlers + a.pendingSentinels;
            pulsesBefore = a.pulses;
        });
        saveLoad(h, s, "two events");
        s.check("persistence: both announcements survive save and load", () -> a.pending > 0f && a.pulsePending > 0f && a.pendingCrawlers + a.pendingSentinels == expected,
            () -> "patrol " + a.pending + ", pulse " + a.pulsePending);
        s.run(() -> enemiesBefore = state.enemies);
        s.waitFor(() -> a.pending < 0f && a.pulsePending < 0f, 25f);
        s.wait(0.5f);
        s.check("events: each arrives exactly once", () -> state.enemies - enemiesBefore == expected && a.pulses == pulsesBefore + 1,
            () -> (state.enemies - enemiesBefore) + " of " + expected + " machines, pulses " + a.pulses);
        s.run(() -> {
            killEnemies();
            holdShock = 0f;
            a.cooldown = a.pulseCooldown = 1e9f;
        });
        s.waitFor(() -> a.pulseActive < 0f, 25f);

        // --- destroyed stabilizers: rebuilt from the team's plans with their setting
        s.run(() -> {
            stabilizer(1).configureAny(true);
            stabilizer(2).configureAny(false);
            bot.active = true;
        });
        s.wait(2f);
        s.run(() -> {
            before = Strings.fixed(a.stabilization, 2);
            stabilizer(1).kill();
            stabilizer(2).kill();
        });
        s.wait(2f);
        s.check("rebuild: losing a working stabilizer drops the stabilization", () -> a.stabilization == 0f, () -> "before " + before + ", after " + a.stabilization);
        s.check("rebuild: both are queued for rebuilding with their setting", () -> plan(10, true) && plan(13, false), () -> team.data().plans.size + " plans");
        s.run(() -> {
            for(var plan : team.data().plans){
                if(plan.block == stabilizerPylon) bot.build(stabilizerPylon, plan.x - (plan.block.size - 1) / 2, plan.y - (plan.block.size - 1) / 2, 0, plan.config);
            }
            team.core().items.add(ItemStack.list(ferroprism, 200, structuralAlloy, 200, recoveredCircuit, 200));
        });
        s.until("rebuild: the core unit rebuilds them", bot::idle, 120f, () -> bot.status);
        s.run(() -> {
            link(14, 25, 10, 22, 2);
            link(14, 25, 13, 22, 2);
        });
        s.wait(2f);
        s.check("rebuild: they come back with their setting (one on, one off)", () -> stabilizer(1).online && !stabilizer(2).online && near(a.stabilization, 12f, 0.05f),
            () -> stabilizerStates());
        s.run(() -> {
            bot.active = false;
            holdShock = -1f;
        });

        // --- performance with many machines: the per-second sample and the stabilizer shares clients work out per packet
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.canGameOver = false;
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagResponse, "true");
            Worlds.generate(manySize, manySize, ResponseSuite::generateMany, r);
        });
        s.wait(2f);
        s.run(() -> {
            int buildings = team.data().buildings.size, stabilizers = team.data().getBuildings(stabilizerPylon).size;
            float sample = millis(a, "sample", 300), shares = millis(a, "assignContributions", 300);
            h.line("info: performance: " + buildings + " buildings (" + stabilizers + " stabilizers, " + team.data().getBuildings(crystalExtractor).size
                + " extractors): activity sample " + Strings.fixed(sample * 1000f, 0) + " us, stabilizer shares " + Strings.fixed(shares * 1000f, 0) + " us (once per second each)");
            h.check("performance: the per-second activity sample stays under 2 ms with " + buildings + " buildings", sample >= 0f && sample < 2f,
                Strings.fixed(sample, 3) + " ms");
        });

        // --- outside Vesper maps nothing changes
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.canGameOver = false;
            Worlds.generate(width, height, ResponseSuite::generate, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            link(9, 7, 7, 4, 2);
            for(int i = 1; i <= 3; i++) link(14, 25, stabilizerTiles[i][0], stabilizerTiles[i][1], 2);
        });
        s.wait(3f);
        s.check("outside Vesper maps: no activity, no bonus, stabilizers inert, no pulses", () -> !a.active() && !a.responses() && a.resonanceMultiplier() == 1f
            && extractor().resonance() == 1f && stabilizer(1).contribution == 0f && a.stabilization == 0f && a.pulses == 0 && a.pulsePending < 0f, () -> "");
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.galleryName), Gamemode.survival));
        s.wait(3f);
        s.check("outside Vesper scenarios: the gallery keeps its stabilizers inert", () -> !a.active() && Vesper.scenario() == null
            && team.data().getBuildings(stabilizerPylon).size == 2 && team.data().getBuildings(stabilizerPylon).allMatch(b -> ((StabilizerBuild)b).contribution == 0f), () -> "");
        s.run(() -> {
            Map vanilla = maps.all().find(m -> m.mod == null && !m.custom);
            if(vanilla != null) Worlds.play(vanilla, Gamemode.survival);
            h.line("info: vanilla map " + (vanilla == null ? "none found" : vanilla.name()));
        });
        s.wait(3f);
        s.check("outside Vesper maps: a vanilla map gets no Vesper state", () -> !a.active() && Vesper.scenario() == null && !state.rules.tags.containsKey(PlanetaryActivity.tagState), () -> "");

        //the builders' pylons in First Awakening are ruins: inert and not salvageable
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.basinName), Gamemode.survival));
        s.wait(3f);
        s.check("derelict pylons: inert and cannot be dismantled", () -> {
            var pylons = Team.derelict.data().getBuildings(stabilizerPylon);
            return pylons.size >= 4 && a.stabilization == 0f && pylons.allMatch(b -> !Build.validBreak(team, b.tileX(), b.tileY()));
        }, () -> Team.derelict.data().getBuildings(stabilizerPylon).size + " pylons");

        // --- a 0.2.0-alpha save: First Awakening saved mid-countdown (activity record version 1, pylons saved as plain ruins)
        s.run(() -> {
            Fi save = mods.getMod("vesper-harness").root.child("saves").child("first-awakening-0.2-midgame.msav");
            logic.reset();
            SaveIO.load(save);
            state.set(State.playing);
            enemiesBefore = state.enemies;
        });
        s.wait(1f);
        s.check("0.2 save: First Awakening loads with its 0.2 state", () -> Vesper.mission.active() && Vesper.mission.completed == 7 && Vesper.mission.released
            && a.active() && !a.responses() && a.awakened && a.pending > 0f && a.pendingSentinels == 2 && a.pendingCrawlers == 3 && a.pulsePending < 0f
            && Vesper.mission.reactor() != null && Vesper.mission.reactor().ignited && state.rules.winWave == 7,
            () -> "mission " + Vesper.mission.completed + ", " + a.stage + ", incoming " + a.pendingSentinels + "+" + a.pendingCrawlers + " in " + (int)(a.pending / 60f) + " s");
        s.check("0.2 save: the builders' pylons load as inert ruins", () -> Team.derelict.data().getBuildings(stabilizerPylon).size >= 4
            && Team.derelict.data().getBuildings(stabilizerPylon).allMatch(b -> ((StabilizerBuild)b).stabilizerOperation() == 0f) && a.stabilization == 0f, () -> "");
        s.until("0.2 save: the awakening response it was counting down to arrives", () -> a.pending < 0f, 40f, null);
        s.wait(0.3f);
        s.check("0.2 save: exactly the five announced machines", () -> state.enemies - enemiesBefore == 5, () -> (state.enemies - enemiesBefore) + " of 5");
        s.run(() -> {
            if(!headless && ui.restart.isShown()) ui.restart.hide();
        });
    }

    static void generate(Tiles tiles){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                tiles.set(x, y, new Tile(x, y, machineBasalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        for(int x = 7; x <= 8; x++) for(int y = 4; y <= 5; y++) tiles.getn(x, y).setOverlay(oreResonanceCrystal);
        place(tiles, coreFoothold, 4, 4);
        place(tiles, crystalExtractor, 7, 4);
        place(tiles, experimentalResonator, 4, 7);
        place(tiles, Blocks.itemSource, 3, 7);
        place(tiles, Blocks.itemSource, 3, 9);
        place(tiles, structuralForge, 10, 4);
        place(tiles, structuralForge, 40, 30);
        place(tiles, Blocks.powerSource, 9, 7);
        place(tiles, Blocks.powerSource, 14, 25);
        place(tiles, Blocks.powerSource, 20, 25);
        for(int[] t : stabilizerTiles) place(tiles, stabilizerPylon, t[0], t[1]);
    }

    static final int manySize = 120;

    /** A crowded base: a grid of extractors on crystal, forges and stabilizers (far more than a real map holds). */
    static void generateMany(Tiles tiles){
        for(int x = 0; x < manySize; x++){
            for(int y = 0; y < manySize; y++){
                boolean border = x == 0 || y == 0 || x == manySize - 1 || y == manySize - 1;
                tiles.set(x, y, new Tile(x, y, machineBasalt, border ? Blocks.air : oreResonanceCrystal, border ? machineBasaltWall : Blocks.air));
            }
        }
        place(tiles, coreFoothold, 2, 2);
        int index = 0;
        for(int x = 6; x + 2 < manySize; x += 3){
            for(int y = 6; y + 2 < manySize; y += 3){
                Block block = index % 6 == 0 ? stabilizerPylon : index % 6 == 1 ? structuralForge : crystalExtractor;
                place(tiles, block, x, y);
                index++;
            }
        }
    }

    /** Average time of one call of a PlanetaryActivity method (the sample takes a float), in milliseconds; -1 if it cannot run. */
    static float millis(PlanetaryActivity activity, String method, int runs){
        try{
            boolean sample = method.equals("sample");
            var m = sample ? PlanetaryActivity.class.getDeclaredMethod(method, float.class) : PlanetaryActivity.class.getDeclaredMethod(method);
            m.setAccessible(true);
            for(int i = 0; i < 20; i++){
                if(sample) m.invoke(activity, 1f / 60f);
                else m.invoke(activity);
            }
            long start = System.nanoTime();
            for(int i = 0; i < runs; i++){
                if(sample) m.invoke(activity, 1f / 60f);
                else m.invoke(activity);
            }
            return (System.nanoTime() - start) / 1e6f / runs;
        }catch(Exception e){
            return -1f;
        }
    }

    static void place(Tiles tiles, Block block, int left, int bottom){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, 0);
    }

    static void link(int nodeX, int nodeY, int left, int bottom, int size){
        Building node = world.build(nodeX, nodeY), to = Worlds.at(left, bottom, size);
        if(node != null && to != null && !node.power.links.contains(to.pos())) node.configure(to.pos());
    }

    /** Whether the team's rebuild queue holds a stabilizer whose footprint starts at this x, with this setting. */
    static boolean plan(int left, boolean online){
        for(var p : team.data().plans){
            if(p.block == stabilizerPylon && p.x - (p.block.size - 1) / 2 == left && Boolean.valueOf(online).equals(p.config)) return true;
        }
        return false;
    }

    static StabilizerBuild stabilizer(int index){
        int[] t = stabilizerTiles[index];
        return Worlds.at(t[0], t[1], 2) instanceof StabilizerBuild b ? b : null;
    }

    static ResonantDrillBuild extractor(){
        return (ResonantDrillBuild)Worlds.at(7, 4, 2);
    }

    static ResonantCrafterBuild resonator(){
        return (ResonantCrafterBuild)Worlds.at(4, 7, 3);
    }

    static Building forge(int left, int bottom){
        return Worlds.at(left, bottom, 2);
    }

    static float sumContributions(){
        float sum = 0f;
        for(int i = 0; i < stabilizerTiles.length; i++) if(stabilizer(i) != null) sum += stabilizer(i).contribution;
        return sum;
    }

    static String stabilizerStates(){
        StringBuilder out = new StringBuilder();
        for(int i = 0; i < stabilizerTiles.length; i++){
            var b = stabilizer(i);
            out.append(i).append(':').append(b == null ? "-" : (b.online ? "on " : "off ") + Strings.fixed(b.contribution, 1)).append(' ');
        }
        return out.toString();
    }

    static boolean near(float value, float target, float tolerance){
        return Math.abs(value - target) <= tolerance;
    }

    /** The measured ratio is within 15% of the expected one (production counts are whole items). */
    static boolean ratio(int high, int low, float expected){
        return low > 0 && Math.abs((float)high / low - expected) <= expected * 0.15f;
    }

    static void killEnemies(){
        Groups.unit.each(u -> u.team == state.rules.waveTeam, Unit::kill);
    }

    /** Saves, resets and loads the world (like quitting and continuing), keeping the bot and the fixtures. */
    void saveLoad(VesperHarness h, Script s, String what){
        s.run(() -> {
            before = snapshot();
            Fi file = h.out.child("response-" + what.replace(' ', '-') + ".msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
            h.line("saved and loaded (" + what + "): " + before);
        });
        s.wait(0.5f);
        s.check("persistence: activity state identical after save and load (" + what + ")", () -> snapshot().equals(before), () -> "after: " + snapshot());
    }

    static String snapshot(){
        var a = Vesper.activity;
        return Strings.format("stage=@ awakened=@ patrols=@ pending=@/@+@ pulse=@/@ at @,@ pulses=@ stabilization=@ stabilizers=@",
            a.stage, a.awakened, a.patrols, a.pending > 0f, a.pendingCrawlers, a.pendingSentinels, a.pulsePending > 0f, a.pulseActive > 0f,
            a.pulseX, a.pulseY, a.pulses, Strings.fixed(a.stabilization, 1), stabilizerStates());
    }
}
