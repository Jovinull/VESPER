package vesper.harness.maps;

import arc.struct.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.MapObjectives.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import vesper.content.*;
import vesper.mission.*;
import vesper.systems.*;
import vesper.world.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * The Buried Foundry - an excavated ancient foundry of The Silent Basin, for industrial archaeology:
 * <pre>
 *                         N: the archive gallery (Archive Terminal)
 *        NW gate  ┌──── the foundry hall: the dead furnace ringed by pylons and broken machines ────┐  NE gate     NE: landing zone
 *  W alcove: Ancient Recoverer                                                   E alcove: Ancient Recoverer   │ the east lane
 *                 └──────────── the excavation trench (choke) ──┐                                                │
 *   SW: the foothold (core, basic ores) <────────────────────────────────────────────────────────────────────────┘
 * </pre>
 * The waves come down the east lane into the foothold; the hall is off their path (only the trench leads there).
 * What the planet sends from the hall's gates (awakening response, patrols) comes up the trench. The buried machines
 * belong to the player's team from the start (inert until surveyed), like the reactor of First Awakening.
 */
public class BuriedFoundryMap{
    public static final int width = 110, height = 86;
    public static final Team player = Team.sharded;

    public static final int coreX = 20, coreY = 16;
    /** Bottom-left tiles of the installations. */
    public static final int westX = 30, westY = 50, eastX = 80, eastY = 50, archiveX = 55, archiveY = 76;
    public static final int furnaceX = 53, furnaceY = 50;
    public static final int spawnX = 101, spawnY = 77;
    static final int[][] gateTargets = {{37, 63}, {75, 63}};
    public static final Seq<int[]> gateTiles = new Seq<>();

    static final int seed = 9203;
    public static final Seq<String> skipped = new Seq<>();

    public static void generate(Tiles tiles){
        Floor basalt = (Floor)machineBasalt, soil = (Floor)scrapSoil, dust = (Floor)vitrifiedDust, plating = (Floor)rustedPlating;
        skipped.clear();
        Terrain t = new Terrain(tiles, width, height, seed, skipped);

        // --- carve the walkable world
        t.ellipse(22, 18, 19, 13);                                       //the foothold
        t.ellipse(7, 25, 5, 6);                                          //west scrap nook
        t.capsule(5.5f, 30, 29, 37, 37, 43, 42);                         //the excavation trench (choke)
        t.ellipse(56, 52, 25, 15);                                       //the foundry hall
        t.ellipse(31, 51, 6, 6);                                         //west alcove
        t.ellipse(82, 51, 6, 6);                                         //east alcove
        t.capsule(4.5f, 56, 64, 56, 74);                                 //the gallery up to the archive
        t.ellipse(56, 77, 7, 5);                                         //the archive
        t.ellipse(100, 76, 8, 6);                                        //landing zone
        t.capsule(6.5f, 99, 69, 97, 44, 86, 28, 66, 20, 44, 16);         //the east lane down to the foothold
        t.rock(91, 52, 3.2f);                                            //keeps the hall and the lane apart
        t.rock(38, 22, 1.8f);
        t.rock(62, 26, 2.2f);
        t.rock(44, 52, 1.6f);
        t.rock(68, 52, 1.6f);
        t.carve(basalt, machineBasaltWall);

        // --- floors: the hall plated by its builders, burned glass where the furnace vented, spoil heaps of the dig
        t.floorBlob(soil, 8, 25, 6, 5, 0.4f);
        t.floorBlob(soil, 30, 8, 9, 4, 0.35f);
        t.floorBlob(plating, 56, 52, 23, 13, 0.18f);
        t.floorBlob(dust, 56, 52, 9, 6, 0.45f);
        t.floorBlob(plating, 56, 76, 6, 4, 0.2f);
        t.floorBlob(soil, 40, 40, 5, 4, 0.5f);
        t.floorBlob(soil, 72, 40, 4, 3, 0.5f);
        t.floorBlob(dust, 100, 76, 8, 6, 0.35f);
        t.floorBlob(plating, coreX, coreY, 5.5f, 5.5f, 0.2f);
        t.road(plating, 1.3f, 0.45f, 26, 22, 30, 29, 37, 37, 43, 42, 50, 46);

        // --- ores: a foothold economy; crystal by each buried machine and a little near the foothold
        t.ore(oreFerroprism, 32, 12, 3.8f, 2.6f);
        t.ore(oreFerroprism, 12, 28, 3f, 2.4f);
        t.ore(oreVitreousCarbon, 10, 11, 3.4f, 2.4f);
        t.ore(oreVitreousCarbon, 29, 24, 3f, 2.2f);
        t.ore(oreConductiveScrap, 6, 22, 2.8f, 3.2f);
        t.ore(oreConductiveScrap, 36, 18, 2.6f, 2.2f);
        t.ore(oreResonanceCrystal, 39, 29, 2.4f, 1.8f);
        //the west machine stands on its own seams: crystal just below it, scrap just above (drills against it feed it)
        t.ore(oreResonanceCrystal, 31.5f, 48.5f, 3.6f, 1.6f);
        t.ore(oreConductiveScrap, 31.5f, 54f, 3.6f, 1.6f);
        t.ore(oreResonanceCrystal, 75, 45, 2.8f, 2f);
        t.ore(oreConductiveScrap, 78, 57, 2.6f, 2f);

        // --- the buried machines and the archive (the player's, inert until surveyed)
        t.place(ancientRecoverer, westX, westY, player);
        t.place(ancientRecoverer, eastX, eastY, player);
        t.place(archiveTerminal, archiveX, archiveY, player);
        // --- the dead furnace at the heart of the hall, ringed by the builders' pylons, broken machines and consoles
        t.place(derelictReactor, furnaceX, furnaceY, Team.derelict);
        t.placeNear(stabilizerPylon, 48, 46, Team.derelict);
        t.placeNear(stabilizerPylon, 61, 46, Team.derelict);
        t.placeNear(stabilizerPylon, 48, 56, Team.derelict);
        t.placeNear(stabilizerPylon, 61, 56, Team.derelict);
        t.placeNear(recoverableRuin, 41, 58, Team.derelict);
        t.placeNear(recoverableRuin, 69, 58, Team.derelict);
        t.placeNear(dataTerminal, 50, 61, Team.derelict);
        t.placeNear(dataTerminal, 60, 61, Team.derelict);
        t.placeNear(recoverableRuin, 49, 40, Team.derelict);
        t.placeNear(dataTerminal, 64, 41, Team.derelict);
        gateTiles.clear();
        for(int[] g : gateTargets){
            int[] best = t.openNear(g[0], g[1]);
            if(best == null) skipped.add("gate near " + g[0] + "," + g[1]);
            else gateTiles.add(best);
        }

        // --- the foothold and the enemy landing zone
        t.place(coreFoothold, coreX - 1, coreY - 1, player);
        tiles.getn(spawnX, spawnY).setOverlay(Blocks.spawn);
    }

    /** Map rules: native waves held before the fourth until the archive is read; Vesper blocks only, the archaeology tags. */
    public static Rules rules(){
        Rules r = new Rules();
        r.waves = true;
        r.waveTimer = true;
        r.waveSending = true;
        r.initialWaveSpacing = 4f * 60f * 60f;
        r.waveSpacing = 150f * 60f;
        r.winWave = 0;                       //armed by the mission when the archive is read
        r.dropZoneRadius = 160f;
        r.derelictRepair = false;
        r.fog = false;
        r.spawns = waves();
        //enough circuits to survey and restore one machine and build one platform; more come from salvage or a Component Assembler (alloy + scrap)
        r.loadout = ItemStack.list(ferroprism, 800, conductiveScrap, 400, structuralAlloy, 150, recoveredCircuit, 200);
        r.blockWhitelist = true;
        r.hideBannedBlocks = true;
        //the Ancient Platform is not on the list: reading the archive adds it
        r.bannedBlocks.addAll(transitBelt, cargoRouter, cargoJunction, cargoBridge, resonanceLink,
            surveyDrill, scrapCollector, reinforcedDrill, crystalExtractor,
            structuralForge, scrapRecycler, alloyRefinery, combustionEngine, powerAccumulator, componentAssembler, experimentalResonator,
            reinforcedWall, heavyWall, kineticTurret, pulseTurret, stabilizerPylon, surveyRig);
        r.tags.put(FirstAwakening.tagScenario, BuriedFoundry.scenarioId);
        r.tags.put(PlanetaryActivity.tagEnabled, "true");
        r.tags.put(PlanetaryActivity.tagResponse, "true");
        r.tags.put(AncientSite.tagEnabled, "true");
        StringBuilder g = new StringBuilder();
        for(int[] gate : gateTiles) g.append(g.length() == 0 ? "" : ";").append(gate[0]).append(',').append(gate[1]);
        r.tags.put(PlanetaryActivity.tagGates, g.toString());
        return r;
    }

    /** Six attack waves; the last three follow the reading of the archive. */
    public static Seq<SpawnGroup> waves(){
        Seq<SpawnGroup> out = new Seq<>();
        int[][] plan = {
            //crawlers, sentinels per wave (index = wave - 1)
            {2, 0}, {3, 0}, {3, 1}, {4, 2}, {5, 3}, {6, 4}
        };
        for(int i = 0; i < plan.length; i++){
            if(plan[i][0] > 0) out.add(group(VUnits.rustCrawler, i, plan[i][0]));
            if(plan[i][1] > 0) out.add(group(VUnits.ancientSentinel, i, plan[i][1]));
        }
        return out;
    }

    static SpawnGroup group(UnitType type, int wave, int amount){
        SpawnGroup g = new SpawnGroup(type);
        g.begin = wave;
        g.end = wave;
        g.unitAmount = amount;
        return g;
    }

    /** World markers: the foundry hall and the archive. */
    public static void addMarkers(){
        marker("@vesper.marker.foundry", furnaceX + 1.5f, furnaceY + 6f, 1);
        marker("@vesper.marker.archive", archiveX + 0.5f, archiveY + 3f, 2);
    }

    static void marker(String text, float tx, float ty, int id){
        TextMarker m = new TextMarker(text, tx * tilesize, ty * tilesize, 1.2f, (byte)(WorldLabel.flagBackground | WorldLabel.flagOutline));
        m.minimap = 1;
        state.markers.add(id, m);
    }

    public static StringMap tags(){
        return StringMap.of(
            "name", "The Buried Foundry - The Silent Basin",
            "author", "Vesper",
            "description", "Vesper 0.4 experimental map. An ancient foundry lies half excavated below your landing. Survey its buried machines, restore them or strip them for salvage, read its archive, and hold out against what that wakes.",
            "genfilters", "[]"
        );
    }
}
