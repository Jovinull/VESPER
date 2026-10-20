package vesper.harness.suites;

import arc.files.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.Unloader.*;
import vesper.harness.*;
import vesper.world.ResonanceDynamo.*;
import vesper.world.Stabilizer.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * The cargo unloader in a purpose-built world, with no bot in the chain: cores stored in the core leave it through
 * an unloader, travel a belt and are burned by a resonance dynamo powering two stabilizer pylons (fixed 1-unit
 * loads). Then: an empty and a finite stock, a filter change, two unloaders taking the same item, a congested belt,
 * save and load, a destroyed unloader rebuilt with its filter, and an unloader next to an enemy's container. Every
 * core is accounted for at each step: in the core, on a belt, in a store or burned.
 * <p>
 * Test fixtures: the core's stock is added by the test (what a base would have stored), and an enemy container is
 * placed next to one unloader. Everything that moves is moved by the machines.
 */
public class LogisticsSuite implements VesperHarness.Suite{
    static final Team team = Team.sharded;
    static final int width = 30, height = 24;
    final Bot bot = new Bot();
    /** Cores the test has put into the world so far (all of them must stay accounted for). */
    int added;
    int before, burnedBefore, takenBefore;
    String saved = "";

    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> {
            added = 0;
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.loadout.clear();
            Worlds.generate(width, height, LogisticsSuite::generate, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            //the dynamo line takes cores; the other two take something the core does not hold, for now
            unloader(13, 11).configure(resonanceCore);
            unloader(11, 13).configure(ferroprism);
            unloader(9, 11).configure(ferroprism);
            world.build(13, 5).items.add(ferroprism, 50);
        });
        s.wait(5f);
        s.check("unloader: with no cores in store nothing moves, the dynamo stays dark and its loads unpowered", () -> beltItems(14, 17, 11, 11) == 0
            && dynamo().coresBurned == 0 && dynamo().items.total() == 0 && load(21, 10).power.status < 0.01f, () -> state());

        //a finite stock of 12 cores
        s.run(() -> addCores(12));
        s.until("unloader: cores leave the core, travel the belt and power the loads through the dynamo, with no bot", () -> dynamo().coresBurned > 0
            && load(21, 10).power.status > 0.99f && load(21, 12).power.status > 0.99f, 30f, () -> state());
        s.check("unloader: every core is in the core, on the belt, in the dynamo or burned", () -> accounted() == added, () -> accounted() + " of " + added + " (" + state() + ")");
        s.until("unloader: a finite stock runs out and the dynamo stops; nothing more is taken", () -> core().items.get(resonanceCore) == 0
            && dynamo().items.get(resonanceCore) == 0 && beltItems(14, 17, 11, 11) == 0 && dynamo().productionEfficiency == 0f, 240f, () -> state());
        s.check("unloader: all 12 cores were burned, none lost or made", () -> dynamo().coresBurned == 12 && accounted() == added, () -> state());

        //a filter change: the line above the core takes ferroprism, then cores
        s.run(() -> {
            addCores(20);
            core().items.add(ferroprism, 200);
            unloader(11, 13).configure(resonanceCore);
        });
        s.wait(8f);
        s.check("unloader: two unloaders take the same item from one core, each line gets its share", () -> container(11, 18).items.get(resonanceCore) > 0
            && (dynamo().coresBurned > 12 || dynamo().items.get(resonanceCore) > 0 || beltItems(14, 17, 11, 11) > 0), () -> "container " + container(11, 18).items.get(resonanceCore) + ", " + state());
        s.check("unloader: shared between two lines, every core is still accounted for", () -> accounted() == added, () -> accounted() + " of " + added);
        s.run(() -> {
            unloader(11, 13).configure(ferroprism);
            before = container(11, 18).items.get(resonanceCore) + beltItems(11, 11, 14, 17);
        });
        s.wait(8f);
        s.check("unloader: after its filter changes it takes the new item and no more of the old", () -> container(11, 18).items.get(ferroprism) > 0
            && container(11, 18).items.get(resonanceCore) <= before && unloader(11, 13).sortItem == ferroprism,
            () -> "container: " + container(11, 18).items.get(resonanceCore) + " cores, " + container(11, 18).items.get(ferroprism) + " ferroprism");

        //a congested belt: the line on the west loses its container and fills up; the unloader stops, nothing vanishes
        s.run(() -> {
            world.tile(3, 11).setNet(Blocks.air);
            unloader(9, 11).configure(resonanceCore);
        });
        s.wait(10f);
        s.run(() -> before = beltItems(5, 8, 11, 11));
        s.wait(10f);
        s.check("unloader: on a congested belt it stops taking (the belt holds what it took)", () -> beltItems(5, 8, 11, 11) == before && before > 0,
            () -> before + " -> " + beltItems(5, 8, 11, 11) + " on the belt");
        s.check("unloader: with a congested line every core is still accounted for", () -> accounted() == added, () -> accounted() + " of " + added);

        //save and load: the filters, and the cores, survive
        s.run(() -> {
            saved = unloader(13, 11).sortItem + " " + unloader(11, 13).sortItem + " " + unloader(9, 11).sortItem + " | " + accounted();
            Fi file = h.out.child("logistics.msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
            String after = unloader(13, 11).sortItem + " " + unloader(11, 13).sortItem + " " + unloader(9, 11).sortItem + " | " + accounted();
            h.check("persistence: the unloaders' filters and every core survive save and load", after.equals(saved), "before " + saved + ", after " + after);
        });
        s.run(() -> burnedBefore = dynamo().coresBurned);
        s.wait(10f);
        s.check("persistence: after loading the line keeps feeding the dynamo", () -> dynamo().coresBurned > burnedBefore || core().items.get(resonanceCore) == 0,
            () -> state());

        //destroyed and rebuilt from the team's plans: the filter comes back
        s.run(() -> {
            core().items.add(ItemStack.list(ferroprism, 100, structuralAlloy, 100, recoveredCircuit, 100));
            bot.active = true;
            unloader(13, 11).kill();
        });
        s.wait(1f);
        s.check("rebuild: the lost unloader is in the rebuild plans with its filter", () -> {
            for(var plan : team.data().plans) if(plan.block == cargoUnloader && plan.config == resonanceCore) return true;
            return false;
        }, () -> team.data().plans.size + " plans");
        s.run(() -> {
            for(var plan : team.data().plans) if(plan.block == cargoUnloader) bot.build(cargoUnloader, plan.x, plan.y, 0, plan.config);
        });
        s.until("rebuild: the core unit rebuilds it", () -> world.build(13, 11) instanceof UnloaderBuild && bot.idle(), 60f, () -> bot.status);
        s.check("rebuild: it comes back taking cores", () -> unloader(13, 11).sortItem == resonanceCore, () -> "" + unloader(13, 11).sortItem);
        s.run(() -> bot.active = false);

        //an enemy's container next to an unloader: nothing comes out of it
        s.run(() -> before = world.build(13, 5).items.get(ferroprism));
        s.wait(10f);
        s.check("unloader: nothing is taken from another team's container", () -> world.build(13, 5).items.get(ferroprism) == before
            && container(17, 5).items.total() == 0 && beltItems(16, 16, 5, 5) == 0, () -> "enemy container " + world.build(13, 5).items.get(ferroprism)
            + ", own container " + container(17, 5).items.total());

        //a different map: the unloader's state stays with the world it belonged to
        s.run(() -> Worlds.generate(width, height, LogisticsSuite::generate, new Rules()));
        s.wait(1f);
        s.check("map change: a new world's unloaders start empty and unconfigured", () -> unloader(13, 11).sortItem == null && dynamo().coresBurned == 0
            && core().items.get(resonanceCore) == 0, () -> state());
    }

    void addCores(int amount){
        core().items.add(resonanceCore, amount);
        added += amount;
    }

    /** Cores anywhere in the team's buildings (core, belts, dynamo, containers), plus those the dynamo burned. */
    static int accounted(){
        int sum = 0;
        for(Building b : team.data().buildings) if(b.items != null) sum += b.items.get(resonanceCore);
        return sum + dynamo().coresBurned;
    }

    static int beltItems(int x0, int x1, int y0, int y1){
        int sum = 0;
        for(int x = x0; x <= x1; x++) for(int y = y0; y <= y1; y++) if(world.build(x, y) != null && world.build(x, y).items != null) sum += world.build(x, y).items.total();
        return sum;
    }

    static String state(){
        var d = dynamo();
        return "core " + core().items.get(resonanceCore) + ", belt " + beltItems(14, 17, 11, 11) + ", dynamo store " + d.items.get(resonanceCore) + " burned " + d.coresBurned
            + " output " + Strings.fixed(d.getPowerProduction(), 2) + ", loads " + Strings.fixed(load(21, 10).power.status, 2);
    }

    static Building core(){
        return team.core();
    }

    static UnloaderBuild unloader(int x, int y){
        return (UnloaderBuild)world.build(x, y);
    }

    static ResonanceDynamoBuild dynamo(){
        return (ResonanceDynamoBuild)Worlds.at(18, 10, 3);
    }

    static StabilizerBuild load(int left, int bottom){
        return (StabilizerBuild)Worlds.at(left, bottom, 2);
    }

    static Building container(int left, int bottom){
        return Worlds.at(left, bottom, 2);
    }

    /** The core, three unloaders on it (east to the dynamo line, north and west to containers) and an enemy container. */
    static void generate(Tiles tiles){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                tiles.set(x, y, new Tile(x, y, machineBasalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        place(tiles, coreFoothold, 10, 10, team);
        place(tiles, cargoUnloader, 13, 11, team);
        for(int x = 14; x <= 17; x++) tiles.getn(x, 11).setBlock(transitBelt, team, 0);
        place(tiles, resonanceDynamo, 18, 10, team);
        place(tiles, stabilizerPylon, 21, 10, team);
        place(tiles, stabilizerPylon, 21, 12, team);
        place(tiles, cargoUnloader, 11, 13, team);
        for(int y = 14; y <= 17; y++) tiles.getn(11, y).setBlock(transitBelt, team, 1);
        place(tiles, Blocks.container, 11, 18, team);
        place(tiles, cargoUnloader, 9, 11, team);
        for(int x = 5; x <= 8; x++) tiles.getn(x, 11).setBlock(transitBelt, team, 2);
        place(tiles, Blocks.container, 3, 11, team);
        place(tiles, Blocks.container, 13, 5, Team.crux);
        place(tiles, cargoUnloader, 15, 5, team);
        tiles.getn(16, 5).setBlock(transitBelt, team, 0);
        place(tiles, Blocks.container, 17, 5, team);
    }

    static void place(Tiles tiles, Block block, int left, int bottom, Team t){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, t, 0);
    }
}
