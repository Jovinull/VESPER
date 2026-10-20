package vesper.harness.suites;

import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import vesper.*;
import vesper.harness.*;
import vesper.systems.*;
import vesper.world.ContainmentField.*;
import vesper.world.ResonanceDynamo.*;

import java.util.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * The cost of the resonance industry in a base far larger than a real map: a 160x160 grid of forges and stabilizers
 * with a containment field every 12 tiles (169) and a cluster of 9 overlapping fields, during a pulse: the shield
 * lookups a landing pulse makes for every building it reaches, the same lookup for every building of the base (the
 * worst case), the per-frame overload check of every stabilizer; and ten dynamos on one grid of over a thousand
 * consumers (their per-tick demand). Then Deep Signal's network mid-synchronization: the terminal and relays' frame, the
 * objectives, the panel text, and saving and loading. Each timing is the median (and the minimum) of 7 runs after 3 warm-up runs,
 * measured in this process with System.nanoTime; a new frame is simulated where the code caches per frame. Fails
 * only past generous budgets.
 */
public class IndustryPerfSuite implements VesperHarness.Suite{
    static final int size = 160;
    static final Team team = Team.sharded;
    /** Bottom-left tiles of the overlapping cluster's fields (6 tiles apart, each reaching 9). */
    static final int[] cluster = {95, 101, 107};

    @Override
    public void define(VesperHarness h, Script s){
        var a = Vesper.activity;
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.canGameOver = false;
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagResponse, "true");
            //fixture: every machine fully powered (the fields ready), so the timings are those of a working base
            r.teams.get(team).cheat = true;
            Worlds.generate(size, size, IndustryPerfSuite::generate, r);
        });
        s.wait(1f);
        pulse(h, s, a, "spread fields", size / 2, size / 2);
        pulse(h, s, a, "9 overlapping fields", 101, 101);

        //ten dynamos on one grid with every stabilizer of the base: each one's demand loops over the grid's members each tick
        s.run(() -> {
            a.pulseActive = -1f;
            var dynamos = team.data().buildings.select(b -> b instanceof ResonanceDynamoBuild);
            var graph = dynamos.first().power.graph;
            for(Building b : team.data().buildings){
                if(b.power != null && b.power.graph != graph && (b.block == stabilizerPylon || b instanceof ResonanceDynamoBuild)) graph.addGraph(b.power.graph);
            }
            for(Building d : dynamos) d.items.add(resonanceCore, 10);
            float[] t = time(3, 7, 50, () -> {
                for(int i = 0; i < dynamos.size; i++) dynamos.get(i).updateTile();
            });
            h.line("info: performance: " + dynamos.size + " dynamos on one grid of " + graph.consumers.size + " consumers: " + us(t[0]) + " per tick for all of them (min "
                + us(t[1]) + ")");
            h.check("performance: " + dynamos.size + " dynamos' ticks on a grid of " + graph.consumers.size + " consumers cost under 2 ms", t[0] < 2f, ms(t[0]));
        });

        // --- 0.7: Deep Signal's network in the middle of a synchronization (the relay world of the deep suite)
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.tags.put(vesper.mission.FirstAwakening.tagScenario, vesper.mission.DeepSignal.scenarioId);
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(vesper.world.AncientSite.tagEnabled, "true");
            Worlds.generate(DeepSignalSuite.width, DeepSignalSuite.height, DeepSignalSuite::generate, r);
        });
        s.wait(0.5f);
        s.run(() -> {
            DeepSignalSuite.terminal().investigate(1f);
            for(int[] rel : DeepSignalSuite.relays) DeepSignalSuite.feed(world.build(rel[0] - 2, rel[1] + 1), resonanceCore, 200);
            for(int i = 0; i < 3; i++) DeepSignalSuite.node(i).handleStack(resonanceCore, 100, null);
        });
        s.wait(1f);
        s.run(() -> DeepSignalSuite.terminal().configure(true));
        s.wait(1f);
        s.run(() -> {
            var t = DeepSignalSuite.terminal();
            var nodes = t.nodes();
            //a frame of the network: the terminal and its three relays updated (each asks the others' state)
            float[] network = time(3, 7, 200, () -> {
                t.updateTile();
                for(int i = 0; i < nodes.size; i++) nodes.get(i).updateTile();
            });
            //what the mission panel asks each frame: every objective's state and text
            float[] objectives = time(3, 7, 200, () -> {
                for(var o : Vesper.deep.objectives){
                    o.done.get();
                    o.text();
                }
            });
            float[] info = time(3, 7, 50, t::infoText);
            h.line("info: performance: Deep Signal mid-synchronization (" + t.syncState + "): terminal and 3 relays " + us(network[0]) + " a frame (min " + us(network[1])
                + "); the 3 objectives and their texts " + us(objectives[0]) + " (min " + us(objectives[1]) + "); the terminal's panel text " + us(info[0]) + " (min " + us(info[1]) + ")");
            h.check("performance: the terminal and its 3 relays cost under 0.2 ms a frame", network[0] < 0.2f, ms(network[0]));
            h.check("performance: the Deep Signal objectives cost under 0.2 ms a frame", objectives[0] < 0.2f, ms(objectives[0]));
            h.check("performance: the terminal's panel text costs under 0.5 ms", info[0] < 0.5f, ms(info[0]));
            //saving and loading the world mid-synchronization
            var file = h.out.child("perf-deep.msav");
            float[] save = time(1, 5, 1, () -> mindustry.io.SaveIO.save(file));
            long start = System.nanoTime();
            logic.reset();
            mindustry.io.SaveIO.load(file);
            state.set(mindustry.core.GameState.State.playing);
            float load = (System.nanoTime() - start) / 1e6f;
            h.line("info: performance: saving the relay world mid-synchronization " + ms(save[0]) + " (min " + ms(save[1]) + "), loading it " + ms(load));
            h.check("performance: saving the relay world mid-synchronization costs under 200 ms", save[0] < 200f, ms(save[0]));
        });
    }

    /** A pulse over a point of the base: every field there paid up and holding it back; the three lookups timed. */
    void pulse(VesperHarness h, Script s, PlanetaryActivity a, String label, int x, int y){
        s.run(() -> {
            a.pulseX = x;
            a.pulseY = y;
            a.pulseActive = 600f;
            a.pulses++;
            for(Building b : team.data().buildings){
                if(b instanceof FieldBuild f){
                    f.items.add(resonanceCore, 10);
                    f.absorbedPulse = b.within(x * tilesize, y * tilesize, a.pulseRadius + f.shieldRadius()) ? a.pulses : -1;
                }
            }
        });
        s.wait(0.5f);
        s.run(() -> {
            var buildings = team.data().buildings.copy();
            var stabilizers = buildings.select(b -> b.block == stabilizerPylon);
            int reached = buildings.count(a::inPulse), covered = buildings.count(b -> a.inPulse(b) && a.shielded(b));
            int fields = buildings.count(b -> b instanceof FieldBuild f && f.shielding());
            //what landing a pulse does: one shield lookup for each building it reaches (a new frame each time)
            float[] land = time(3, 7, 5, () -> {
                Time.time += 1f;
                for(int i = 0; i < buildings.size; i++){
                    Building b = buildings.get(i);
                    if(a.inPulse(b)) a.shielded(b);
                }
            });
            //what every stabilizer asks each frame
            float[] frame = time(3, 7, 20, () -> {
                Time.time += 1f;
                for(int i = 0; i < stabilizers.size; i++) a.interferes(stabilizers.get(i));
            });
            //the worst case: one lookup for every building of the base in one frame
            float[] all = time(3, 7, 2, () -> {
                Time.time += 1f;
                for(int i = 0; i < buildings.size; i++) a.shielded(buildings.get(i));
            });
            h.line("info: performance (" + label + "): " + buildings.size + " buildings, " + fields + " fields holding the pulse; landing on " + reached + " buildings ("
                + covered + " covered): " + ms(land[0]) + " (min " + ms(land[1]) + "); " + stabilizers.size + " stabilizers' checks a frame: " + ms(frame[0]) + " (min "
                + ms(frame[1]) + "); a lookup for every building: " + ms(all[0]) + " (min " + ms(all[1]) + ", " + us(all[0] / buildings.size) + " each)");
            h.check("performance (" + label + "): landing a pulse on " + reached + " buildings costs under 5 ms", land[0] < 5f, ms(land[0]));
            h.check("performance (" + label + "): a shield lookup for every one of " + buildings.size + " buildings costs under 20 ms", all[0] < 20f, ms(all[0]));
            h.check("performance (" + label + "): " + stabilizers.size + " stabilizers' overload checks cost under 2 ms a frame", frame[0] < 2f, ms(frame[0]));
            h.check("performance (" + label + "): the covered buildings are the ones a field reaches", covered == buildings.count(b -> a.inPulse(b) && covers(b)),
                covered + " covered");
        });
    }

    /** The definition the lookup must agree with: some field holding the pulse back reaches the building. */
    static boolean covers(Building b){
        for(Building other : team.data().buildings){
            if(other instanceof FieldBuild f && f.shielding() && b.within(other, f.shieldRadius())) return true;
        }
        return false;
    }

    /** Median and minimum, in milliseconds per call of the body, of {@code runs} timed runs after {@code warmup} untimed ones. */
    static float[] time(int warmup, int runs, int inner, Runnable body){
        for(int i = 0; i < warmup; i++) for(int j = 0; j < inner; j++) body.run();
        float[] t = new float[runs];
        for(int i = 0; i < runs; i++){
            long start = System.nanoTime();
            for(int j = 0; j < inner; j++) body.run();
            t[i] = (System.nanoTime() - start) / 1e6f / inner;
        }
        Arrays.sort(t);
        return new float[]{t[runs / 2], t[0]};
    }

    static String ms(float v){
        return Strings.fixed(v, 3) + " ms";
    }

    static String us(float ms){
        return Strings.fixed(ms * 1000f, 2) + " us";
    }

    /** A crowded base: every 3 tiles a forge or a stabilizer, a field every 12 tiles and a cluster of 9, ten dynamos along the bottom. */
    static void generate(Tiles tiles){
        for(int x = 0; x < size; x++){
            for(int y = 0; y < size; y++){
                boolean border = x == 0 || y == 0 || x == size - 1 || y == size - 1;
                tiles.set(x, y, new Tile(x, y, machineBasalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        for(int i = 0; i < 10; i++) place(tiles, resonanceDynamo, 2 + i * 3, 2);
        int index = 0;
        for(int x = 8; x + 2 < size; x += 3){
            for(int y = 8; y + 2 < size; y += 3){
                boolean field = ((x - 8) % 12 == 0 && (y - 8) % 12 == 0) || (Arrays.binarySearch(cluster, x) >= 0 && Arrays.binarySearch(cluster, y) >= 0);
                place(tiles, field ? containmentField : index % 2 == 0 ? structuralForge : stabilizerPylon, x, y);
                index++;
            }
        }
    }

    static void place(Tiles tiles, Block block, int left, int bottom){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, 0);
    }
}
