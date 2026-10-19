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

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * The cost of the resonance industry in a base far larger than a real map: a 160x160 grid of forges and stabilizers
 * with a containment field every 12 tiles (169) during a pulse: the shield lookups a landing pulse makes for every
 * building it reaches, the same lookup for every building of the base (the worst case), and the per-frame overload
 * check of every stabilizer; and a dynamo on a grid of over a thousand consumers (its per-tick demand). Records the
 * timings and fails only past generous budgets.
 */
public class IndustryPerfSuite implements VesperHarness.Suite{
    static final int size = 160;
    static final Team team = Team.sharded;

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
        s.run(() -> {
            //fixture: a pulse running over the middle of the base, every field paid up and holding it back
            a.pulseX = size / 2;
            a.pulseY = size / 2;
            a.pulseActive = 600f;
            a.pulses++;
            int fields = 0;
            for(Building b : team.data().buildings){
                if(b instanceof FieldBuild f){
                    f.items.add(resonanceCore, 10);
                    f.absorbedPulse = a.pulses;
                    fields++;
                }
            }
            h.line("info: performance world: " + team.data().buildings.size + " buildings, " + fields + " fields");
        });
        s.wait(1f);
        s.run(() -> {
            var buildings = team.data().buildings.copy();
            int reached = 0;
            for(Building b : buildings) if(a.inPulse(b)) reached++;
            //what landing a pulse does: one shield lookup for each building it reaches
            long start = System.nanoTime();
            int shielded = 0;
            for(int rep = 0; rep < 20; rep++){
                for(Building b : buildings) if(a.inPulse(b) && a.shielded(b)) shielded++;
            }
            float perPulse = (System.nanoTime() - start) / 1e6f / 20f;
            //what every stabilizer asks each frame
            var stabilizers = buildings.select(b -> b.block == stabilizerPylon);
            start = System.nanoTime();
            for(int rep = 0; rep < 100; rep++) for(Building b : stabilizers) a.interferes(b);
            float perFrame = (System.nanoTime() - start) / 1e6f / 100f;
            //the worst case: one lookup for every building of the base
            start = System.nanoTime();
            for(int rep = 0; rep < 5; rep++) for(Building b : buildings) a.shielded(b);
            float all = (System.nanoTime() - start) / 1e6f / 5f;
            int fields = buildings.count(b -> b instanceof FieldBuild);
            h.line("info: performance: " + fields + " fields; a pulse reaching " + reached + " buildings: shield lookups " + Strings.fixed(perPulse, 3) + " ms (" + shielded / 20
                + " covered); a lookup for each of the " + buildings.size + " buildings: " + Strings.fixed(all, 3) + " ms (" + Strings.fixed(all * 1000f / buildings.size, 2)
                + " us each); " + stabilizers.size + " stabilizers' overload checks " + Strings.fixed(perFrame, 3) + " ms per frame");
            h.check("performance: landing a pulse on " + reached + " buildings among " + fields + " fields costs under 5 ms", perPulse < 5f, Strings.fixed(perPulse, 3) + " ms");
            h.check("performance: a shield lookup for every one of " + buildings.size + " buildings costs under 20 ms", all < 20f, Strings.fixed(all, 3) + " ms");
            h.check("performance: " + stabilizers.size + " stabilizers' overload checks during a pulse cost under 2 ms a frame", perFrame < 2f, Strings.fixed(perFrame, 3) + " ms");
        });
        //a dynamo on one grid with every stabilizer of the base: its demand loops over the grid's members each tick
        s.run(() -> {
            a.pulseActive = -1f;
            Building dynamo = world.build(3, 3);
            int linked = 0;
            Building node = world.build(1, 1);
            if(dynamo instanceof ResonanceDynamoBuild d){
                d.items.add(resonanceCore, 10);
                for(Building b : team.data().buildings){
                    if(b.block == stabilizerPylon && b.power != null && b.power.graph != d.power.graph){
                        d.power.graph.addGraph(b.power.graph);
                        linked++;
                    }
                }
                long start = System.nanoTime();
                for(int rep = 0; rep < 200; rep++) d.updateTile();
                float perTick = (System.nanoTime() - start) / 1e6f / 200f;
                h.line("info: performance: a dynamo on a grid of " + d.power.graph.consumers.size + " consumers: " + Strings.fixed(perTick * 1000f, 1) + " us per tick");
                h.check("performance: a dynamo's tick on a grid of " + d.power.graph.consumers.size + " consumers costs under 1 ms", perTick < 1f, Strings.fixed(perTick, 3) + " ms");
            }else{
                h.fail("performance: dynamo present", "no dynamo at 3,3");
            }
        });
    }

    /** A crowded base: every 3 tiles a forge or a stabilizer, a containment field every 12 tiles, a dynamo in the corner. */
    static void generate(Tiles tiles){
        for(int x = 0; x < size; x++){
            for(int y = 0; y < size; y++){
                boolean border = x == 0 || y == 0 || x == size - 1 || y == size - 1;
                tiles.set(x, y, new Tile(x, y, machineBasalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        place(tiles, resonanceDynamo, 2, 2);
        int index = 0;
        for(int x = 8; x + 2 < size; x += 3){
            for(int y = 8; y + 2 < size; y += 3){
                boolean field = (x - 8) % 12 == 0 && (y - 8) % 12 == 0;
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
