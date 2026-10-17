package vesper.harness.suites;

import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.ItemTurret.*;
import mindustry.world.blocks.distribution.Conveyor.*;
import mindustry.world.blocks.power.PowerGenerator.*;
import mindustry.world.blocks.production.GenericCrafter.*;
import vesper.harness.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Real production without cheats or infinite resources, in a purpose-built world where every chain starts from
 * ore: drills -> belts -> forge -> core (A), carbon -> combustion engines -> resonance link -> consumers (B),
 * forge + scrap -> component assembler -> core (C), ferroprism -> belt -> junction -> bridge -> kinetic turret,
 * which then has to kill a rust crawler (D).
 */
public class EconomySuite implements VesperHarness.Suite{
    static final int width = 64, height = 44;
    static final Team team = Team.sharded;
    static final Seq<int[]> links = new Seq<>();

    int crawlerId = -1;
    int ferroStart, scrapStart;

    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.infiniteResources = false;
            r.canGameOver = false;
            Worlds.generate(width, height, EconomySuite::generate, r);
        });
        s.wait(0.2f);
        s.run(() -> {
            for(int[] l : links){
                Building from = Worlds.at(l[0], l[1], 1), to = world.build(l[2], l[3]);
                if(from != null && to != null) from.configureAny(to.pos());
            }
            team.core().items.clear();
        });
        //measured rates: one survey drill on ferroprism and one scrap collector unload straight into the core
        s.run(() -> {
            ferroStart = state.stats.coreItemCount.get(ferroprism);
            scrapStart = state.stats.coreItemCount.get(conductiveScrap);
        });
        s.wait(60f);
        s.check("economy: survey drill rate matches the formula", () -> rateOk(state.stats.coreItemCount.get(ferroprism) - ferroStart, surveyDrill, ferroprism),
            () -> "ferroprism in 60 s: " + (state.stats.coreItemCount.get(ferroprism) - ferroStart) + ", formula " + Strings.fixed(expected(surveyDrill, ferroprism), 1));
        s.check("economy: scrap collector rate matches the formula", () -> rateOk(state.stats.coreItemCount.get(conductiveScrap) - scrapStart, scrapCollector, conductiveScrap),
            () -> "scrap in 60 s: " + (state.stats.coreItemCount.get(conductiveScrap) - scrapStart) + ", formula " + Strings.fixed(expected(scrapCollector, conductiveScrap), 1));
        s.check("economy: no cheat and finite resources", () -> !state.rules.infiniteResources && !state.rules.teams.get(team).cheat
            && !state.rules.teams.get(team).infiniteResources, () -> "");

        //belts carry ore within seconds
        s.until("economy: drills put ore on the belts", () -> conveyorItems(20, 30, 27, 30) > 0 && conveyorItems(20, 29, 23, 29) > 0, 20f,
            () -> "ferroprism belt " + conveyorItems(20, 30, 27, 30) + " items, carbon belt " + conveyorItems(20, 29, 23, 29));
        s.until("economy A: structural forge receives both inputs", () -> crafter(17, 29) != null && crafter(17, 29).items.get(ferroprism) > 0
            && crafter(17, 29).items.get(vitreousCarbon) > 0, 30f, null);
        s.until("economy A: alloy reaches the core", () -> core(structuralAlloy) >= 5, 120f, () -> "core alloy " + core(structuralAlloy));

        s.until("economy B: combustion engines burn carbon", () -> generator(34, 19) > 0.5f && generator(38, 19) > 0.5f, 60f,
            () -> "efficiency " + generator(34, 19) + ", " + generator(38, 19));
        s.until("economy B: the grid links engines to the assembler", () -> Worlds.at(34, 19, 2) != null && Worlds.at(44, 24, 3) != null
            && Worlds.at(34, 19, 2).power.graph == Worlds.at(44, 24, 3).power.graph && Worlds.at(34, 19, 2).power.graph.getPowerProduced() > 0f, 60f,
            () -> "engines produce " + (int)(team.data().getBuildings(combustionEngine).sumf(b -> b.getPowerProduction()) * 60f) + " power/s");

        s.until("economy C: assembler works on alloy and scrap", () -> crafter(44, 24) != null && crafter(44, 24).totalProgress > 0f, 120f, null);
        s.until("economy C: recovered circuits reach the core", () -> core(recoveredCircuit) >= 3, 240f, () -> "core circuits " + core(recoveredCircuit));

        s.until("economy D: ferroprism crosses junction and bridge into the turret", () -> turretAmmo() > 0, 90f, () -> "ammo " + turretAmmo());
        s.run(() -> crawlerId = rustCrawler().spawn(Team.crux, 22 * tilesize, 5 * tilesize, 180f).id);
        h.shot("economy-01-chains", 30, 22, 2.5f, false);
        s.until("economy D: the kinetic turret destroys a rust crawler", () -> Groups.unit.getByID(crawlerId) == null
            || Groups.unit.getByID(crawlerId).dead(), 60f, null);
        s.check("economy D: turret survived", () -> Worlds.at(10, 8, 2) instanceof ItemTurretBuild t && t.isValid(), () -> "");
        s.run(() -> h.line("info: core after the economy suite: alloy " + core(structuralAlloy) + ", circuits " + core(recoveredCircuit)
            + ", ferroprism " + core(ferroprism) + ", carbon " + core(vitreousCarbon)));
    }

    /** Items a fully covered drill makes in 60 s, from Drill.getDrillTime (60 ticks per second). */
    static float expected(Block block, Item item){
        var drill = (mindustry.world.blocks.production.Drill)block;
        return 60f * 60f * block.size * block.size / drill.getDrillTime(item);
    }

    static boolean rateOk(int measured, Block block, Item item){
        //warm-up costs a little at the start; anything beyond +-25% means the model in the docs is wrong
        return Math.abs(measured - expected(block, item)) <= expected(block, item) * 0.25f;
    }

    static UnitType rustCrawler(){
        return vesper.content.VUnits.rustCrawler;
    }

    static int core(Item item){
        return team.core() == null ? -1 : team.core().items.get(item);
    }

    static GenericCrafterBuild crafter(int left, int bottom){
        Building b = Worlds.at(left, bottom, world.tile(left, bottom).block().size);
        return b instanceof GenericCrafterBuild g ? g : null;
    }

    static float generator(int left, int bottom){
        return Worlds.at(left, bottom, 2) instanceof GeneratorBuild g ? g.productionEfficiency : -1f;
    }

    static int turretAmmo(){
        return Worlds.at(10, 8, 2) instanceof ItemTurretBuild t ? t.totalAmmo : -1;
    }

    static int conveyorItems(int x0, int y, int x1, int y1){
        int n = 0;
        for(int x = x0; x <= x1; x++){
            if(world.build(x, y) instanceof ConveyorBuild c) n += c.len;
        }
        return n;
    }

    /**
     * Layout (tiles, y-up). A: ferroprism drills (y 31-32) and carbon drills (y 27-28) feed belts at y 30 / 29
     * running west into the forge at 17,29, which outputs west into the core at 12,29. B: carbon drills feed two
     * engines at 34,19 / 38,19; a resonance link at 41,21 powers the assembler. C: a second forge (fed by its own
     * drills) and three scrap collectors surround the assembler at 44,24; circuits run west to the core. D: a
     * ferroprism drill feeds a belt that crosses a carbon belt through a junction and jumps a bridge into the
     * kinetic turret at 10,8.
     */
    static void generate(Tiles tiles){
        links.clear();
        Block basalt = machineBasalt;
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                tiles.set(x, y, new Tile(x, y, basalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        // --- A
        ore(tiles, oreFerroprism, 20, 31, 8, 2);
        ore(tiles, oreVitreousCarbon, 20, 27, 4, 2);
        for(int x = 20; x <= 26; x += 2) place(tiles, surveyDrill, x, 31, 0);
        for(int x = 20; x <= 22; x += 2) place(tiles, surveyDrill, x, 27, 0);
        line(tiles, 20, 30, 27, 30, 2);
        line(tiles, 19, 30, 19, 30, 2);
        line(tiles, 19, 29, 23, 29, 2);
        place(tiles, structuralForge, 17, 29, 0);
        line(tiles, 15, 30, 16, 30, 2);
        place(tiles, coreFoothold, 12, 29, 0);

        // --- B
        ore(tiles, oreVitreousCarbon, 32, 19, 2, 2);
        ore(tiles, oreVitreousCarbon, 34, 21, 2, 2);
        ore(tiles, oreVitreousCarbon, 36, 19, 2, 2);
        ore(tiles, oreVitreousCarbon, 38, 21, 2, 2);
        place(tiles, surveyDrill, 32, 19, 0);
        place(tiles, surveyDrill, 34, 21, 0);
        place(tiles, combustionEngine, 34, 19, 0);
        place(tiles, surveyDrill, 36, 19, 0);
        place(tiles, surveyDrill, 38, 21, 0);
        place(tiles, combustionEngine, 38, 19, 0);
        place(tiles, resonanceLink, 41, 21, 0);
        links.add(new int[]{41, 21, 35, 20});
        links.add(new int[]{41, 21, 39, 20});
        links.add(new int[]{41, 21, 45, 25});

        // --- C: forge 2 below the assembler, scrap collectors on its right, output belt west to the core
        place(tiles, componentAssembler, 44, 24, 0);
        ore(tiles, oreFerroprism, 42, 17, 6, 2);
        ore(tiles, oreVitreousCarbon, 48, 19, 2, 4);
        place(tiles, structuralForge, 44, 22, 0);
        place(tiles, surveyDrill, 42, 17, 0);
        place(tiles, surveyDrill, 44, 17, 0);
        place(tiles, surveyDrill, 46, 17, 0);
        line(tiles, 42, 19, 43, 19, 0);
        line(tiles, 44, 19, 46, 19, 0);
        line(tiles, 47, 19, 47, 19, 1);
        line(tiles, 47, 20, 47, 21, 1);
        line(tiles, 46, 22, 47, 22, 2);
        place(tiles, surveyDrill, 48, 21, 0);
        place(tiles, surveyDrill, 48, 19, 0);
        ore(tiles, oreConductiveScrap, 47, 24, 2, 6);
        place(tiles, scrapCollector, 47, 24, 0);
        place(tiles, scrapCollector, 47, 26, 0);
        place(tiles, scrapCollector, 44, 27, 0);
        ore(tiles, oreConductiveScrap, 44, 27, 2, 2);
        line(tiles, 43, 25, 43, 25, 2);
        line(tiles, 42, 25, 16, 25, 2);
        line(tiles, 15, 25, 15, 28, 1);
        line(tiles, 15, 29, 15, 29, 2);

        // --- rate probes beside the core (12..14 x 29..31): a drill above it, a collector below it
        ore(tiles, oreFerroprism, 12, 32, 2, 2);
        place(tiles, surveyDrill, 12, 32, 0);
        ore(tiles, oreConductiveScrap, 12, 27, 2, 2);
        place(tiles, scrapCollector, 12, 27, 0);

        // --- D: drill -> belt west -> junction (carbon belt crossing north) -> bridge -> turret
        ore(tiles, oreFerroprism, 26, 8, 2, 2);
        place(tiles, surveyDrill, 26, 8, 0);
        line(tiles, 25, 8, 22, 8, 2);
        place(tiles, cargoJunction, 21, 8, 0);
        line(tiles, 20, 8, 20, 8, 2);
        place(tiles, cargoBridge, 19, 8, 0);
        place(tiles, cargoBridge, 15, 8, 0);
        links.add(new int[]{19, 8, 15, 8});
        line(tiles, 14, 8, 12, 8, 2);
        place(tiles, kineticTurret, 10, 8, 0);
        ore(tiles, oreVitreousCarbon, 20, 4, 2, 2);
        place(tiles, surveyDrill, 20, 4, 0);
        line(tiles, 21, 6, 21, 7, 1);
        line(tiles, 21, 9, 21, 12, 1);
    }

    static void ore(Tiles tiles, Block ore, int x0, int y0, int w, int h){
        for(int x = x0; x < x0 + w; x++) for(int y = y0; y < y0 + h; y++) tiles.getn(x, y).setOverlay(ore);
    }

    static void line(Tiles tiles, int x0, int y0, int x1, int y1, int rotation){
        for(int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++){
            for(int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++){
                place(tiles, transitBelt, x, y, rotation);
            }
        }
    }

    static void place(Tiles tiles, Block block, int left, int bottom, int rotation){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, rotation);
    }
}
