package vesper.harness.suites;

import arc.*;
import arc.files.*;
import arc.math.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.ConsumeGenerator.*;
import vesper.*;
import vesper.harness.*;
import vesper.systems.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.ContainmentField.*;
import vesper.world.ResonanceDynamo.*;
import vesper.world.ResonantCrafter.*;
import vesper.world.Stabilizer.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * The resonance industry in purpose-built worlds, measured on the real machines.
 * <p>
 * Power world (no planetary activity, x1): the dynamo fed by belt follows its load (stabilizer pylons as fixed
 * 1-unit loads), the energy per core is exact, it burns nothing without a load, gives its rating at full load,
 * stops when starved and resumes, charges batteries flat out and then follows the load, shares a grid with a
 * second dynamo and a combustion engine, and keeps its state through save and load; a containment field there
 * stands by at no cost (no planetary response on the map).
 * <p>
 * Field world (activity and the planetary response): the field stands by while no pulse can come, pays its
 * upkeep exactly once pulses can, absorbs a pulse (industry and a stabilizer under it spared, outside them not),
 * overlapping fields each pay, it never lowers the activity, a pulse survives save and load with what the field
 * absorbed, switched off mid-pulse it leaves its industry to the rest of the pulse (and switching it back on gives
 * nothing back), without power or cores it is overloaded, it is rebuilt with its setting; the dynamo is heavy
 * industry for the basin and the resonance speeds it up without adding energy to a core.
 * <p>
 * Chain world: crystal extractors and the experimental resonator make the cores the dynamo burns, powering
 * themselves once started; the loop cannot start itself from nothing.
 * <p>
 * Test fixtures, not gameplay: item and power sources stand in for the rest of a base (cores and circuits fed in,
 * fields powered), the activity level is set by holding the surge value, enemies deal no damage and patrols are
 * held back. The machines, the logistics into them and the planetary systems are the real ones.
 */
public class IndustrySuite implements VesperHarness.Suite{
    static final Team team = Team.sharded;
    static final int width = 40, height = 30;
    /** Power world: stabilizer pylons used as 1-unit loads, by bottom-left tile. */
    static final int[][] loads = {{17, 11}, {17, 14}, {17, 17}, {19, 11}, {19, 14}, {19, 17}};
    static final int fieldWidth = 64, fieldHeight = 40, gateX = 60, gateY = 20;

    /** Surge held while pulses are wanted: with the extractor and both pylons working the level settles at 52.8, under the awakening. */
    static final float pulseShock = 68f;

    final Bot bot = new Bot();
    int[] chainStart = {0, 0};
    float holdShock = -1f;
    boolean listening;
    float energy, coresStart, window;
    int spentA, spentB, pulsesBefore;
    float upkeepA, paidStart;
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

        // --- the power world: no planetary activity, every resonance multiplier is x1
        s.run(() -> {
            holdShock = -1f;
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            Worlds.generate(width, height, IndustrySuite::generatePower, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            link(15, 15, 10, 14, 3);
            for(int[] l : loads) link(15, 15, l[0], l[1], 2);
            link(15, 9, 10, 8, 3);
            link(15, 9, 17, 5, 2);
            for(int i = 0; i < loads.length; i++) load(i).configureAny(i < 2);
            //the field gets power and cores, but this map has no planetary response
            world.build(26, 14).configureAny(resonanceCore);
        });
        s.wait(3f);
        s.check("dynamo: without cores it produces nothing and its loads have no power", () -> dynamo(10, 14).productionEfficiency == 0f
            && dynamo(10, 14).coresBurned == 0 && load(0).power.status < 0.01f, () -> state(dynamo(10, 14)));
        s.run(() -> world.build(5, 15).configureAny(resonanceCore));
        s.until("dynamo: cores arrive by belt and it follows a 2-unit load", () -> near(dynamo(10, 14).throttle, 0.4f, 0.01f)
            && near(dynamo(10, 14).getPowerProduction(), 2f, 0.02f) && load(0).power.status > 0.99f, 30f, () -> state(dynamo(10, 14)));

        //five minutes at 2 units: 2 x 60 x 300 = 36000 units, 24 cores of 1500
        measure(s, () -> dynamo(10, 14), 300f);
        s.check("dynamo: energy per core is exactly 1500 at x1 (measured)", () -> near(energy / burned(dynamo(10, 14)), 1500f, 15f),
            () -> Strings.fixed(energy, 0) + " units from " + Strings.fixed(burned(dynamo(10, 14)), 2) + " cores");
        s.check("dynamo: burns cores at the rate of its load (2 units: 24 cores in 5 min)", () -> near(burned(dynamo(10, 14)), 24f, 0.3f),
            () -> Strings.fixed(burned(dynamo(10, 14)), 2) + " cores, " + Strings.fixed(dynamo(10, 14).coresPerMinute(), 2) + " per minute shown");
        s.check("dynamo: what it produced is what its loads took", () -> near(energy, 2f * 60f * 300f, 2f * 60f * 300f * 0.01f),
            () -> Strings.fixed(energy, 0) + " of " + (int)(2f * 60f * 300f));

        s.run(() -> {
            for(int i = 0; i < loads.length; i++) load(i).configureAny(false);
        });
        s.wait(5f);
        measure(s, () -> dynamo(10, 14), 60f);
        s.check("dynamo: with no load it burns nothing and keeps its cores", () -> burned(dynamo(10, 14)) < 0.001f && energy < 1f
            && dynamo(10, 14).items.get(resonanceCore) == dynamo(10, 14).block.itemCapacity, () -> state(dynamo(10, 14)));

        s.run(() -> {
            for(int i = 0; i < loads.length; i++) load(i).configureAny(true);
        });
        s.wait(5f);
        s.check("dynamo: at full load it gives its rated 5 units (6 asked)", () -> near(dynamo(10, 14).throttle, 1f, 0.001f)
            && near(dynamo(10, 14).getPowerProduction(), 5f, 0.01f) && near(load(0).power.status, 5f / 6f, 0.02f), () -> state(dynamo(10, 14)));
        measure(s, () -> dynamo(10, 14), 60f);
        s.check("dynamo: at full load it burns 12 cores a minute", () -> near(burned(dynamo(10, 14)), 12f, 0.2f),
            () -> Strings.fixed(burned(dynamo(10, 14)), 2) + " cores in 60 s");

        //starved: the source goes away, the belt and the store run dry
        s.run(() -> world.tile(5, 15).setNet(Blocks.air));
        s.until("dynamo: starved, it stops and its loads lose power", () -> dynamo(10, 14).productionEfficiency == 0f && load(0).power.status < 0.01f,
            240f, () -> state(dynamo(10, 14)));
        s.check("dynamo: starved, it keeps nothing back (store empty)", () -> dynamo(10, 14).items.get(resonanceCore) == 0, () -> state(dynamo(10, 14)));
        s.run(() -> {
            world.tile(5, 15).setNet(Blocks.itemSource, team, 0);
            world.build(5, 15).configureAny(resonanceCore);
        });
        s.until("dynamo: resumes as soon as cores arrive again", () -> near(dynamo(10, 14).getPowerProduction(), 5f, 0.01f), 30f, () -> state(dynamo(10, 14)));

        //batteries: flat out while they have room, then the loads again
        s.run(() -> {
            for(int i = 2; i < loads.length; i++) load(i).configureAny(false);
            link(15, 15, 13, 18, 2);
        });
        s.wait(2f);
        s.check("dynamo: runs flat out while the batteries have room", () -> dynamo(10, 14).throttle > 0.98f && battery().power.status < 0.99f,
            () -> state(dynamo(10, 14)) + ", battery " + Strings.fixed(battery().power.status * 100f, 1) + "%");
        s.until("dynamo: follows its loads again once the batteries are full", () -> near(dynamo(10, 14).throttle, 0.4f, 0.03f)
            && battery().power.status > 0.99f, 90f, () -> state(dynamo(10, 14)) + ", battery " + Strings.fixed(battery().power.status * 100f, 1) + "%");
        s.wait(20f);
        s.check("dynamo: the full batteries stay full and the loads powered", () -> battery().power.status > 0.99f && load(0).power.status > 0.99f
            && near(dynamo(10, 14).throttle, 0.4f, 0.03f), () -> state(dynamo(10, 14)) + ", battery " + Strings.fixed(battery().power.status * 100f, 1) + "%");

        //save and load in the middle of a core
        s.run(() -> {
            var d = dynamo(10, 14);
            before = dynamoState(d);
            Fi file = h.out.child("industry-dynamo.msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
            h.check("persistence: the dynamo's core in progress, load, store and count survive save and load", dynamoState(dynamo(10, 14)).equals(before),
                "before " + before + ", after " + dynamoState(dynamo(10, 14)));
        });
        s.wait(3f);
        s.check("persistence: after loading it goes on following its loads", () -> near(dynamo(10, 14).throttle, 0.4f, 0.03f) && load(0).power.status > 0.99f,
            () -> state(dynamo(10, 14)));

        //one grid, two dynamos and a combustion engine: the engine runs, the dynamos cover the rest between them
        s.run(() -> {
            unlink(15, 15, 13, 18, 2);
            link(15, 15, 15, 9, 1);
            world.build(5, 9).configureAny(resonanceCore);
            world.build(19, 5).configureAny(vitreousCarbon);
            for(int i = 0; i < 4; i++) load(i).configureAny(true);
        });
        s.wait(20f);
        s.check("dynamo: two dynamos and an engine share 4 units: the engine runs, the dynamos cover the rest between them", () -> {
            float engine = engine().getPowerProduction(), da = dynamo(10, 14).getPowerProduction(), db = dynamo(10, 8).getPowerProduction();
            return engine > 1f && near(da + db + engine, 4f, 0.05f) && load(0).power.status > 0.99f;
        }, () -> "engine " + Strings.fixed(engine().getPowerProduction(), 2) + ", dynamos " + Strings.fixed(dynamo(10, 14).getPowerProduction(), 2) + " + "
            + Strings.fixed(dynamo(10, 8).getPowerProduction(), 2));

        s.check("field: on a map without the planetary response it stands by (no power, no cores)", () -> {
            var f = field(24, 14);
            return f.online && !f.shouldConsume() && f.coresSpent == 0 && f.fieldStatus().equals("standby") && f.items.get(resonanceCore) == f.block.itemCapacity;
        }, () -> fieldState(field(24, 14)));

        // --- the field world: planetary activity and the response
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
            Worlds.generate(fieldWidth, fieldHeight, IndustrySuite::generateField, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            for(int[] t : new int[][]{{27, 25}, {24, 14}, {3, 31}}) world.build(t[0], t[1]).configureAny(resonanceCore);
            fieldStabilizer(7, 30).configureAny(false);
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
        });
        s.wait(20f);
        s.check("field: standing by while no pulse can come: no power drawn, no cores spent", () -> !a.pulsing() && field(27, 23).coresSpent == 0
            && field(24, 15).coresSpent == 0 && !field(27, 23).shouldConsume() && field(27, 23).items.get(resonanceCore) == 10, () -> fieldState(field(27, 23)));
        s.run(() -> holdShock = pulseShock);
        s.waitFor(() -> a.level >= 51f, 60f);
        s.wait(5f);
        s.check("field: still standing by above the pulse level while no pulse is announced (nothing to hold back, nothing paid)", () -> a.level >= 50f
            && !a.pulsing() && !field(27, 23).shouldConsume() && field(27, 23).coresSpent == 0, () -> "level " + Strings.fixed(a.level, 1) + ", " + fieldState(field(27, 23)));

        //a pulse is announced at the extractor: one field covers the forge east of it, a second field overlaps the first
        s.run(() -> {
            a.pulseCooldown = 0f;
            pulsesBefore = a.pulses;
        });
        s.until("pulse: announced at the extractor", () -> a.pulsePending > 0f && a.pulseX == 20 && a.pulseY == 20, 10f,
            () -> "epicentre " + a.pulseX + "," + a.pulseY);
        s.wait(1f);
        s.check("field: running as soon as a pulse is announced, its first core paid", () -> a.pulsing() && field(27, 23).ready() && field(27, 23).coresSpent >= 1,
            () -> fieldState(field(27, 23)));
        //fixture: the announced pulse waits longer, so the upkeep can be measured over a long run
        s.run(() -> {
            paidStart = paid(field(27, 23));
            a.pulsePending = 160f * 60f;
        });
        s.wait(150f);
        s.check("field: upkeep is exact: 1 core for every 60 s it runs (150 s measured)", () -> near(paid(field(27, 23)) - paidStart, 150f * 60f, 30f),
            () -> Strings.fixed((paid(field(27, 23)) - paidStart) / 60f, 1) + " s paid in 150 s, " + field(27, 23).coresSpent + " cores so far");
        s.waitFor(() -> a.pulsePending > 0f && a.pulsePending <= 30f, 30f);
        s.run(() -> {
            spentA = field(27, 23).coresSpent;
            spentB = field(24, 15).coresSpent;
            upkeepA = field(27, 23).upkeep;
        });
        s.until("pulse: lands", () -> a.pulseActive > 0f && a.pulses == pulsesBefore + 1, 5f, null);
        s.check("field: absorbs the pulse and pays 2 cores for it", () -> field(27, 23).shielding() && field(27, 23).coresSpent - spentA
            == absorbCost() + (field(27, 23).upkeep > upkeepA ? 1 : 0), () -> fieldState(field(27, 23)) + ", spent before " + spentA);
        s.check("field: overlapping fields each absorb and each pay", () -> field(24, 15).shielding() && field(24, 15).coresSpent - spentB >= absorbCost(),
            () -> fieldState(field(24, 15)) + ", spent before " + spentB);
        s.check("field: the industry under it is spared, the industry in the pulse outside it slows", () -> forge(24, 20).timeScale() == 1f
            && near(forge(14, 18).timeScale(), 0.5f, 0.001f) && forge(44, 20).timeScale() == 1f,
            () -> "covered " + forge(24, 20).timeScale() + ", uncovered " + forge(14, 18).timeScale() + ", outside the pulse " + forge(44, 20).timeScale());
        s.wait(1.5f);
        s.check("field: a stabilizer under it keeps working, one outside it is overloaded", () -> fieldStabilizer(23, 24).stabilizerOperation() > 0f
            && fieldStabilizer(14, 22).stabilizerOperation() == 0f, () -> "covered " + fieldStabilizer(23, 24).stabilizerOperation() + ", uncovered "
            + fieldStabilizer(14, 22).stabilizerOperation());
        s.check("field: it protects, it does not stabilize (only the working pylon lowers the level)", () -> !(field(27, 23) instanceof ActivityStabilizer)
            && near(a.stabilization, 12f, 0.05f), () -> "stabilization " + Strings.fixed(a.stabilization, 2));
        //hold the level under the awakening for the rest of this pulse (the pulse runs on whatever the level)
        s.run(() -> holdShock = 50f);
        s.run(() -> {
            before = saved(field(27, 23)) + " | " + saved(field(24, 15));
            Fi file = h.out.child("industry-field-pulse.msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
            String after = saved(field(27, 23)) + " | " + saved(field(24, 15));
            h.check("persistence: the fields' setting, store, upkeep and absorbed pulse survive save and load (nothing paid twice)", after.equals(before),
                "before " + before + ", after " + after);
        });
        s.wait(0.5f);
        s.check("persistence: after loading mid-pulse the covered forge is still spared, the uncovered one slowed", () -> field(27, 23).shielding()
            && forge(24, 20).timeScale() == 1f && near(forge(14, 18).timeScale(), 0.5f, 0.001f),
            () -> "covered " + forge(24, 20).timeScale() + ", uncovered " + forge(14, 18).timeScale());
        s.run(() -> field(24, 15).configureAny(false));
        s.wait(1f);
        s.check("field: one of two overlapping fields switched off: the other still covers the forge", () -> !field(24, 15).shielding()
            && forge(24, 20).timeScale() == 1f, () -> "forge " + forge(24, 20).timeScale());
        s.run(() -> field(27, 23).configureAny(false));
        s.wait(1f);
        s.check("field: both switched off mid-pulse: the forge they spared slows for the rest of the pulse", () -> near(forge(24, 20).timeScale(), 0.5f, 0.001f)
            && fieldStabilizer(23, 24).stabilizerOperation() == 0f, () -> "forge " + forge(24, 20).timeScale());
        s.run(() -> field(27, 23).configureAny(true));
        s.wait(1f);
        s.check("field: switched back on mid-pulse it gives nothing back (no free coverage)", () -> !field(27, 23).shielding()
            && near(forge(24, 20).timeScale(), 0.5f, 0.001f), () -> fieldState(field(27, 23)));
        s.until("pulse: ends", () -> a.pulseActive < 0f, 25f, null);
        s.wait(1f);
        s.check("pulse: afterwards the forges run normally again", () -> forge(24, 20).timeScale() == 1f && forge(14, 18).timeScale() == 1f, () -> "");

        //without power, and short of cores: overloaded
        s.run(() -> {
            field(24, 15).configureAny(false);
            world.tile(29, 23).setNet(Blocks.air);
        });
        announcePulse(s, a);
        s.check("field: a pulse announced and no power: it is not ready", () -> !field(27, 23).ready() && field(27, 23).fieldStatus().equals("unpowered"),
            () -> fieldState(field(27, 23)));
        landPulse(s, a);
        s.check("field: without power a pulse overloads it and nothing is spared or paid", () -> field(27, 23).overloaded() && !field(27, 23).shielding()
            && field(27, 23).coresSpent == spentA && near(forge(24, 20).timeScale(), 0.5f, 0.001f), () -> fieldState(field(27, 23)) + ", forge " + forge(24, 20).timeScale());
        s.waitFor(() -> a.pulseActive < 0f, 25f);
        s.run(() -> {
            world.tile(29, 23).setNet(Blocks.powerSource, team, 0);
            //fixture: the store emptied and the supply cut, with 5 s of the current core's upkeep left
            world.tile(27, 25).setNet(Blocks.air);
            field(27, 23).items.clear();
            field(27, 23).upkeep = 5f * 60f;
        });
        announcePulse(s, a);
        s.check("field: on its paid upkeep but with no cores in store it is still running", () -> field(27, 23).ready() && field(27, 23).upkeep > 0f,
            () -> fieldState(field(27, 23)));
        s.until("field: once its paid upkeep runs out it is starved and draws no power", () -> !field(27, 23).ready() && field(27, 23).fieldStatus().equals("starved")
            && field(27, 23).efficiency == 0f, 15f, () -> fieldState(field(27, 23)));
        landPulse(s, a);
        s.check("field: short of the 2 cores a pulse costs it is overloaded", () -> field(27, 23).overloaded() && field(27, 23).coresSpent == spentA
            && near(forge(24, 20).timeScale(), 0.5f, 0.001f), () -> fieldState(field(27, 23)));
        s.waitFor(() -> a.pulseActive < 0f, 25f);

        //destroyed and rebuilt from the team's plans with its setting
        s.run(() -> {
            field(24, 15).configureAny(false);
            bot.active = true;
            team.core().items.add(ItemStack.list(structuralAlloy, 200, recoveredCircuit, 200, resonanceCore, 50));
        });
        s.wait(1f);
        s.run(() -> field(24, 15).kill());
        s.wait(1f);
        s.run(() -> {
            for(var plan : team.data().plans){
                int off = (plan.block.size - 1) / 2;
                if(plan.block == containmentField) bot.build(containmentField, plan.x - off, plan.y - off, 0, plan.config);
            }
        });
        s.until("rebuild: the core unit rebuilds the field", () -> field(24, 15) != null && bot.idle(), 120f, () -> bot.status);
        s.check("rebuild: it comes back with its setting (switched off)", () -> field(24, 15) != null && !field(24, 15).online, () -> fieldState(field(24, 15)));
        s.run(() -> bot.active = false);

        //the dynamo in the basin: heavy industry, and faster with the resonance, without more energy in a core
        s.run(() -> {
            holdShock = 120f;
            a.pulseCooldown = 1e9f;
            fieldStabilizer(23, 24).configureAny(false);
            fieldStabilizer(14, 22).configureAny(false);
        });
        s.waitFor(() -> a.level >= 99.5f, 120f);
        s.check("dynamo: idle, it adds nothing to the activity", () -> dynamo(4, 30).productionEfficiency == 0f && near(a.industry, industryWithout(), 0.05f),
            () -> "industry " + Strings.fixed(a.industry, 2));
        s.run(() -> fieldStabilizer(7, 30).configureAny(true));
        s.wait(5f);
        s.check("dynamo: at level 100 it can give twice its rating, and follows a 1-unit load", () -> near(dynamo(4, 30).resonance(), 2f, 0.01f)
            && near(dynamo(4, 30).getPowerProduction(), 1f, 0.02f) && near(dynamo(4, 30).throttle, 0.1f, 0.01f), () -> state(dynamo(4, 30)));
        s.check("dynamo: working, it is heavy industry for the basin (weight 7 at full output)", () -> near(a.industry - industryWithout(), 7f
            * Math.min(1f, dynamo(4, 30).productionEfficiency), 0.1f) && a.industry > industryWithout(), () -> "industry " + Strings.fixed(a.industry, 2)
            + ", without the dynamo " + Strings.fixed(industryWithout(), 2));
        measure(s, () -> dynamo(4, 30), 120f);
        s.check("dynamo: at level 100 a core still holds 1500 units (measured)", () -> near(energy / burned(dynamo(4, 30)), 1500f, 15f),
            () -> Strings.fixed(energy, 0) + " units from " + Strings.fixed(burned(dynamo(4, 30)), 2) + " cores");
        s.run(() -> holdShock = -1f);

        // --- the chain: extractors and the resonator make the cores the dynamo burns to power them
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            Worlds.generate(width, height, IndustrySuite::generateChain, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            for(int[] e : new int[][]{{8, 10}, {8, 12}}) link(12, 16, e[0], e[1], 2);
            link(12, 16, 14, 17, 2);
            link(12, 16, 10, 10, 3);
            link(12, 16, 13, 9, 3);
            link(12, 16, 19, 14, 2);
            world.build(11, 9).configureAny(recoveredCircuit);
        });
        s.wait(3f);
        s.check("chain: nothing starts from nothing (no power, no cores, no crystal)", () -> dynamo(13, 9).productionEfficiency == 0f
            && resonator().efficiency == 0f && dynamo(13, 9).coresBurned == 0, () -> state(dynamo(13, 9)));
        s.run(() -> world.build(21, 14).configureAny(vitreousCarbon));
        s.until("chain: started by a combustion engine, the extractors and the resonator make the first cores", () -> dynamo(13, 9).items.get(resonanceCore) > 0,
            120f, () -> "dynamo store " + dynamo(13, 9).items.get(resonanceCore));
        s.run(() -> world.tile(21, 14).setNet(Blocks.air));
        //the engine burns what it holds, then the loop is on its own
        s.waitFor(() -> engine(19, 14).productionEfficiency == 0f, 60f);
        s.run(() -> chainStart = chainCount());
        s.wait(180f);
        s.check("chain: without the engine the loop powers itself and its 1-unit load with the cores it makes", () -> engine(19, 14).productionEfficiency == 0f
            && dynamo(13, 9).productionEfficiency > 0f && chainLoad().power.status > 0.99f && dynamo(13, 9).coresBurned > chainStart[0],
            () -> state(dynamo(13, 9)) + ", load " + Strings.fixed(chainLoad().power.status, 3));
        s.check("chain: the resonator makes at least what the dynamo burns (made = burned + stored)", () -> made() >= dynamo(13, 9).coresBurned - chainStart[0],
            () -> "made " + made() + ", burned " + (dynamo(13, 9).coresBurned - chainStart[0]));
        s.run(() -> h.line("info: chain: in 180 s the resonator made " + made() + " cores, the dynamo burned " + (dynamo(13, 9).coresBurned - chainStart[0])
            + " to power 2 extractors, the resonator and a 1-unit load (" + Strings.fixed(dynamo(13, 9).getPowerProduction() * 60f, 0) + " power/s now)"));

        // --- the value of archaeology: core sources side by side, measured at a low and a high level
        s.run(() -> {
            holdShock = 0f;
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            //at level 100 the basin awakens: what comes out of it deals no damage here
            r.teams.get(r.waveTeam).unitDamageMultiplier = 0f;
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(AncientSite.tagEnabled, "true");
            Worlds.generate(economyWidth, economyHeight, IndustrySuite::generateEconomy, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            //fixture: the recoverers stand restored, as after a restoration (the restoration itself is played by the foundry suites)
            for(Building b : team.data().getBuildings(ancientRecoverer)) ((SiteBuild)b).siteState = SiteState.restored;
            for(int[] c : economyCells) configureCell(c);
        });
        economy(h, s, "low", 0f);
        economy(h, s, "high", 120f);
        s.run(() -> holdShock = -1f);

        // --- saves written by 0.5.0-alpha: the response world in the middle of a pulse, the archaeology world with a restored recoverer
        s.run(() -> ResponseSuite.loadFixture("planetary-response-0.5-pulse.msav"));
        s.wait(1f);
        s.check("0.5 save: the pulse it was in runs on as before (no field there: the forge inside slowed, the stabilizer inside overloaded)", () -> a.active()
            && a.responses() && a.pulseActive > 0f && ResponseSuite.forge(10, 4).timeScale() == 0.5f && ResponseSuite.stabilizer(6).stabilizerOperation() == 0f
            && ResponseSuite.stabilizer(1).stabilizerOperation() > 0f && !a.shielded(ResponseSuite.forge(10, 4)), () -> "pulse " + Strings.fixed(a.pulseActive / 60f, 1) + " s");
        s.until("0.5 save: the pulse ends and everything returns to normal", () -> a.pulseActive < 0f && ResponseSuite.forge(10, 4).timeScale() == 1f
            && ResponseSuite.stabilizer(6).stabilizerOperation() > 0f, 30f, null);
        s.run(() -> {
            ResponseSuite.loadFixture("archaeology-0.5-restored.msav");
            fixtureCores = state.stats.coreItemCount.get(resonanceCore);
        });
        s.wait(30f);
        s.check("0.5 save: a recoverer restored in 0.5 keeps making resonance cores (about 10 in 30 s)", () -> ArchaeologySuite.site(ArchaeologySuite.bx, ArchaeologySuite.by)
            .siteState == SiteState.restored && state.stats.coreItemCount.get(resonanceCore) - fixtureCores >= 8,
            () -> (state.stats.coreItemCount.get(resonanceCore) - fixtureCores) + " cores in 30 s");
    }

    /**
     * Core sources by bottom-left of their first machine: kind (0 recoverer, 1 resonator) of the lower and the upper
     * machine (-1 none). Each cell has its own item and power sources and one container its machines empty into.
     */
    static final int[][] economyCells = {{8, 4, 0, -1}, {20, 4, 0, 0}, {32, 4, 1, -1}, {44, 4, 0, 1}};
    static final String[] economyNames = {"one restored recoverer", "two restored recoverers", "a resonator only", "a recoverer and a resonator"};
    static final int economyWidth = 60, economyHeight = 20;
    final int[] economyStart = new int[economyCells.length];
    float economyMultiplier;
    int fixtureCores;

    static void configureCell(int[] c){
        for(int m = 0; m < 2; m++){
            int kind = c[2 + m], oy = c[1] + m * 3;
            if(kind < 0) continue;
            world.build(c[0] - 1, oy).configureAny(kind == 0 ? conductiveScrap : recoveredCircuit);
            world.build(c[0] - 1, oy + 2).configureAny(resonanceCrystal);
        }
    }

    static Building container(int[] c){
        return Worlds.at(c[0] + 3, c[3] >= 0 ? c[1] + 2 : c[1], 2);
    }

    /** Holds the level, then counts what each cell makes in 120 s. */
    void economy(VesperHarness h, Script s, String label, float shock){
        s.run(() -> holdShock = shock);
        //the level settles where the machines (and the held surge) put it, so the resonance stays the same while counting
        s.wait(5f);
        s.waitFor(() -> Math.abs(Vesper.activity.level - Mathf.clamp(Vesper.activity.industry + Vesper.activity.ancient + shock - Vesper.activity.stabilization, 0f, 100f)) < 0.5f, 120f);
        s.wait(5f);
        s.run(() -> {
            //fixture: the containers are emptied, so none fills up (300) and stops its cell while counting
            for(int i = 0; i < economyCells.length; i++){
                container(economyCells[i]).items.clear();
                economyStart[i] = 0;
            }
            economyMultiplier = Vesper.activity.resonanceMultiplier();
        });
        s.wait(120f);
        s.run(() -> {
            var a = Vesper.activity;
            StringBuilder out = new StringBuilder();
            for(int i = 0; i < economyCells.length; i++){
                int made = container(economyCells[i]).items.get(resonanceCore) - economyStart[i];
                out.append(economyNames[i]).append(' ').append(Strings.fixed(made / 2f, 1)).append("/min; ");
            }
            h.line("info: economy (" + label + ", level " + Strings.fixed(a.level, 1) + ", resonance x" + Strings.fixed(economyMultiplier, 2) + ", industry "
                + Strings.fixed(a.industry, 1) + ", ancient " + Strings.fixed(a.ancient, 1) + "): " + out);
            //the rated speeds: a recoverer 20 cores a minute, a resonator 30, both times the resonance
            int one = container(economyCells[0]).items.get(resonanceCore) - economyStart[0];
            int resonator = container(economyCells[2]).items.get(resonanceCore) - economyStart[2];
            h.check("economy (" + label + "): a restored recoverer makes 20 cores a minute times the resonance (measured)",
                Math.abs(one / 2f - 20f * economyMultiplier) <= 20f * economyMultiplier * 0.08f, Strings.fixed(one / 2f, 1) + "/min at x" + Strings.fixed(economyMultiplier, 2));
            int two = container(economyCells[1]).items.get(resonanceCore) - economyStart[1], mixed = container(economyCells[3]).items.get(resonanceCore) - economyStart[3];
            h.check("economy (" + label + "): sources add up (two recoverers twice one; a recoverer and a resonator, both rates)",
                Math.abs(two - 2 * one) <= 2 && Math.abs(mixed - one - resonator) <= 2, "two " + two + ", mixed " + mixed + ", one " + one + ", resonator " + resonator);
            h.check("economy (" + label + "): a resonator makes 30 cores a minute times the resonance (measured)",
                Math.abs(resonator / 2f - 30f * economyMultiplier) <= 30f * economyMultiplier * 0.08f, Strings.fixed(resonator / 2f, 1) + "/min at x" + Strings.fixed(economyMultiplier, 2));
        });
    }

    /** Integrates what a dynamo puts into the grid over a window and records its fractional core count at the start. */
    void measure(Script s, arc.func.Prov<ResonanceDynamoBuild> dynamo, float seconds){
        s.run(() -> {
            energy = 0f;
            window = 0f;
            coresStart = progress(dynamo.get());
        });
        s.step(d -> {
            energy += dynamo.get().getPowerProduction() * d;
            return (window += d) >= seconds * 60f;
        });
    }

    /** Cores burned since {@link #measure} started, counting the one in progress by what is left of it. */
    float burned(ResonanceDynamoBuild d){
        return progress(d) - coresStart;
    }

    static float progress(ResonanceDynamoBuild d){
        return d.coresBurned - Math.max(d.generateTime, 0f);
    }

    static int absorbCost(){
        return ((vesper.world.ContainmentField)containmentField).absorbCost;
    }

    /** Ticks of operation a field has paid for and used up. */
    static float paid(FieldBuild f){
        return f.coresSpent * ((vesper.world.ContainmentField)f.block).upkeepTime - f.upkeep;
    }

    /** Brings the level over the pulse level and lets a pulse be announced (it lands about 20 s later). */
    void announcePulse(Script s, PlanetaryActivity a){
        s.run(() -> holdShock = pulseShock);
        s.waitFor(() -> a.level >= 51f, 60f);
        s.run(() -> {
            a.pulseCooldown = 0f;
            pulsesBefore = a.pulses;
            spentA = field(27, 23).coresSpent;
        });
        s.waitFor(() -> a.pulsePending > 0f, 30f);
        s.wait(1f);
    }

    void landPulse(Script s, PlanetaryActivity a){
        s.waitFor(() -> a.pulses == pulsesBefore + 1 && a.pulseActive > 0f, 30f);
        s.wait(0.5f);
    }

    /** Industry of everything in the field world but the dynamo. */
    static float industryWithout(){
        float sum = 0f;
        for(Building b : team.data().buildings){
            if(b instanceof ResonanceDynamoBuild) continue;
            float w = PlanetaryActivity.weights.get(b.block, 0f);
            if(w > 0f) sum += w * (b.shouldConsume() ? Math.min(1f, b.efficiency) : 0f);
        }
        return sum;
    }

    static String state(ResonanceDynamoBuild d){
        return "load " + Strings.fixed(d.throttle * 100f, 1) + "%, output " + Strings.fixed(d.getPowerProduction(), 3) + "/tick, store "
            + d.items.get(resonanceCore) + ", burned " + d.coresBurned + ", satisfaction " + Strings.fixed(d.power.graph.getSatisfaction(), 3);
    }

    static String dynamoState(ResonanceDynamoBuild d){
        return Strings.format("progress=@ load=@ store=@ burned=@ efficiency=@", Strings.fixed(d.generateTime, 4), Strings.fixed(d.throttle, 4),
            d.items.get(resonanceCore), d.coresBurned, Strings.fixed(d.productionEfficiency, 4));
    }

    static String fieldState(@Nullable FieldBuild f){
        if(f == null) return "no field";
        return Strings.format("@ @ store=@ upkeep=@ spent=@ absorbed=@ overloaded=@", f.online ? "on" : "off", f.fieldStatus(), f.items.get(resonanceCore),
            (int)f.upkeep, f.coresSpent, f.absorbedPulse, f.overloadedPulse);
    }

    /** Chain world: cores burned so far, and cores in the dynamo's and the resonator's stores (what the resonator made is the change of both). */
    static int[] chainCount(){
        return new int[]{dynamo(13, 9).coresBurned, dynamo(13, 9).items.get(resonanceCore) + resonator().items.get(resonanceCore)};
    }

    int made(){
        int[] now = chainCount();
        return now[0] - chainStart[0] + now[1] - chainStart[1];
    }

    static String saved(@Nullable FieldBuild f){
        if(f == null) return "no field";
        return Strings.format("@ store=@ upkeep=@ spent=@ absorbed=@ overloaded=@", f.online ? "on" : "off", f.items.get(resonanceCore), Strings.fixed(f.upkeep, 2),
            f.coresSpent, f.absorbedPulse, f.overloadedPulse);
    }

    static ResonanceDynamoBuild dynamo(int left, int bottom){
        return (ResonanceDynamoBuild)Worlds.at(left, bottom, 3);
    }

    static @Nullable FieldBuild field(int left, int bottom){
        return Worlds.at(left, bottom, 2) instanceof FieldBuild f ? f : null;
    }

    static StabilizerBuild load(int index){
        return (StabilizerBuild)Worlds.at(loads[index][0], loads[index][1], 2);
    }

    static StabilizerBuild fieldStabilizer(int left, int bottom){
        return (StabilizerBuild)Worlds.at(left, bottom, 2);
    }

    static Building battery(){
        return Worlds.at(13, 18, 2);
    }

    static ConsumeGeneratorBuild engine(){
        return engine(17, 5);
    }

    static ConsumeGeneratorBuild engine(int left, int bottom){
        return (ConsumeGeneratorBuild)Worlds.at(left, bottom, 2);
    }

    static Building forge(int left, int bottom){
        return Worlds.at(left, bottom, 2);
    }

    static StabilizerBuild chainLoad(){
        return (StabilizerBuild)Worlds.at(14, 17, 2);
    }

    static ResonantCrafterBuild resonator(){
        return (ResonantCrafterBuild)Worlds.at(10, 10, 3);
    }

    static boolean near(float value, float target, float tolerance){
        return Math.abs(value - target) <= tolerance;
    }

    static void link(int nodeX, int nodeY, int left, int bottom, int size){
        Building node = world.build(nodeX, nodeY), to = Worlds.at(left, bottom, size);
        if(node != null && to != null && !node.power.links.contains(to.pos())) node.configure(to.pos());
    }

    static void unlink(int nodeX, int nodeY, int left, int bottom, int size){
        Building node = world.build(nodeX, nodeY), to = Worlds.at(left, bottom, size);
        if(node != null && to != null && node.power.links.contains(to.pos())) node.configure(to.pos());
    }

    static void base(Tiles tiles, int w, int h){
        for(int x = 0; x < w; x++){
            for(int y = 0; y < h; y++){
                boolean border = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                tiles.set(x, y, new Tile(x, y, machineBasalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        place(tiles, coreFoothold, 2, 2);
    }

    /** Two dynamos fed by belt, six 1-unit loads and a battery on one node, an engine on a second, and a field. */
    static void generatePower(Tiles tiles){
        base(tiles, width, height);
        place(tiles, Blocks.itemSource, 5, 15);
        for(int x = 6; x <= 9; x++) tiles.getn(x, 15).setBlock(transitBelt, team, 0);
        place(tiles, resonanceDynamo, 10, 14);
        place(tiles, Blocks.itemSource, 5, 9);
        for(int x = 6; x <= 9; x++) tiles.getn(x, 9).setBlock(transitBelt, team, 0);
        place(tiles, resonanceDynamo, 10, 8);
        place(tiles, resonanceLink, 15, 15);
        place(tiles, resonanceLink, 15, 9);
        for(int[] l : loads) place(tiles, stabilizerPylon, l[0], l[1]);
        place(tiles, powerAccumulator, 13, 18);
        place(tiles, combustionEngine, 17, 5);
        place(tiles, Blocks.itemSource, 19, 5);
        place(tiles, containmentField, 24, 14);
        place(tiles, Blocks.itemSource, 26, 14);
        place(tiles, Blocks.powerSource, 24, 16);
    }

    /**
     * A pulse target (crystal extractor) with forges inside the pulse (one under two overlapping fields, one not) and
     * outside it, a stabilizer under a field and one not, and a dynamo far from the gate with a 1-unit load.
     */
    static void generateField(Tiles tiles){
        base(tiles, fieldWidth, fieldHeight);
        for(int x = 20; x <= 21; x++) for(int y = 20; y <= 21; y++) tiles.getn(x, y).setOverlay(oreResonanceCrystal);
        place(tiles, crystalExtractor, 20, 20);
        place(tiles, Blocks.powerSource, 19, 20);
        //somewhere for its crystal to go, or a full extractor stops working (and stops being a pulse target)
        place(tiles, Blocks.itemVoid, 22, 20);
        place(tiles, structuralForge, 24, 20);
        place(tiles, structuralForge, 14, 18);
        place(tiles, structuralForge, 44, 20);
        place(tiles, containmentField, 27, 23);
        place(tiles, Blocks.powerSource, 29, 23);
        place(tiles, Blocks.itemSource, 27, 25);
        place(tiles, containmentField, 24, 15);
        place(tiles, Blocks.powerSource, 26, 15);
        place(tiles, Blocks.itemSource, 24, 14);
        place(tiles, stabilizerPylon, 23, 24);
        place(tiles, Blocks.powerSource, 25, 25);
        place(tiles, stabilizerPylon, 14, 22);
        place(tiles, Blocks.powerSource, 13, 22);
        place(tiles, resonanceDynamo, 4, 30);
        place(tiles, Blocks.itemSource, 3, 31);
        place(tiles, stabilizerPylon, 7, 30);
    }

    /** Two crystal extractors against the resonator, the dynamo beside it, a 1-unit load and an engine to start it. */
    static void generateChain(Tiles tiles){
        base(tiles, width, height);
        int[][] extractors = {{8, 10}, {8, 12}};
        for(int[] e : extractors){
            for(int x = e[0]; x <= e[0] + 1; x++) for(int y = e[1]; y <= e[1] + 1; y++) tiles.getn(x, y).setOverlay(oreResonanceCrystal);
            place(tiles, crystalExtractor, e[0], e[1]);
        }
        place(tiles, experimentalResonator, 10, 10);
        place(tiles, Blocks.itemSource, 11, 9);
        place(tiles, resonanceDynamo, 13, 9);
        place(tiles, resonanceLink, 12, 16);
        place(tiles, stabilizerPylon, 14, 17);
        place(tiles, combustionEngine, 19, 14);
        place(tiles, Blocks.itemSource, 21, 14);
    }

    /** Four core sources, each with its item sources (scrap or circuits, crystal), its own power and a container. */
    static void generateEconomy(Tiles tiles){
        base(tiles, economyWidth, economyHeight);
        for(int[] c : economyCells){
            for(int m = 0; m < 2; m++){
                int kind = c[2 + m], oy = c[1] + m * 3;
                if(kind < 0) continue;
                place(tiles, kind == 0 ? ancientRecoverer : experimentalResonator, c[0], oy);
                place(tiles, Blocks.itemSource, c[0] - 1, oy);
                place(tiles, Blocks.itemSource, c[0] - 1, oy + 2);
                place(tiles, Blocks.powerSource, c[0] + 1, m == 0 ? oy - 1 : oy + 3);
            }
            place(tiles, Blocks.container, c[0] + 3, c[3] >= 0 ? c[1] + 2 : c[1]);
        }
    }

    static void place(Tiles tiles, Block block, int left, int bottom){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, 0);
    }
}
