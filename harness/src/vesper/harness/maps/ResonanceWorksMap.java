package vesper.harness.maps;

import arc.graphics.*;
import arc.struct.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.MapObjectives.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import vesper.content.*;
import vesper.graphics.*;
import vesper.mission.*;
import vesper.systems.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Resonance Works - a standalone map of the resonance industry (not part of Chapter One):
 * <pre>
 *                                   the Works hall (the relay)                        NE: landing zone
 *                                          |                                               |
 *   W: carbon seam   the works floor: ferroprism, carbon, scrap and crystal seams          | the east lane
 *                    laid out side by side, the Works gate at its south edge                |
 *   SW: the foothold (core, ferroprism)  <---------------------------------------------------'
 * </pre>
 * The builders laid their seams side by side on the works floor, so a whole core line fits there: drills straight
 * into a forge and an assembler, crystal by belt into a resonator, cores by belt up to the relay. The waves come
 * down the east lane; what the basin sends (the awakening, patrols) climbs out of the Works gate, right under the
 * floor, and the pulses land on the resonance machine nearest to it.
 */
public class ResonanceWorksMap{
    public static final int width = 100, height = 72;
    public static final Team player = Team.sharded;

    public static final int coreX = 16, coreY = 15;          //centre of the 3x3 core
    public static final int relayX = 44, relayY = 50;        //bottom-left of the 3x3 relay
    public static final int spawnX = 90, spawnY = 64;
    static final int[] gateTarget = {32, 23};
    public static final Seq<int[]> gateTiles = new Seq<>();

    static final int seed = 5417;
    public static final Seq<String> skipped = new Seq<>();

    public static void generate(Tiles tiles){
        Floor basalt = (Floor)machineBasalt, soil = (Floor)scrapSoil, dust = (Floor)vitrifiedDust, plating = (Floor)rustedPlating;
        skipped.clear();
        Terrain t = new Terrain(tiles, width, height, seed, skipped);

        // --- carve the walkable world
        t.ellipse(18, 16, 13, 10);                                       //the foothold
        t.capsule(5f, 20, 18, 30, 28);                                   //up to the works floor
        t.ellipse(37, 37, 16, 14);                                       //the works floor
        t.ellipse(44, 52, 10, 7);                                        //the Works hall
        t.ellipse(24, 40, 6, 6);                                         //the west carbon seam
        t.capsule(4.5f, 28, 22, 44, 23);                                 //the gate apron under the floor
        t.ellipse(89, 63, 7, 6);                                         //landing zone
        t.capsule(6f, 88, 60, 82, 40, 72, 22, 52, 12, 32, 13);           //the east lane, out of range of the floor, down to the foothold
        t.rock(58, 40, 3f);                                              //keeps the floor and the lane apart
        t.rock(72, 52, 2.4f);
        t.rock(26, 52, 2f);
        t.rock(62, 30, 2.4f);
        t.carve(basalt, machineBasaltWall);

        // --- floors: plating where the builders worked, dust around the gate
        t.floorBlob(plating, 38, 34, 12, 9, 0.2f);
        t.floorBlob(plating, 44, 52, 8, 5, 0.2f);
        t.floorBlob(plating, coreX, coreY, 5.5f, 5.5f, 0.2f);
        t.floorBlob(soil, 12, 22, 6, 4, 0.4f);
        t.floorBlob(dust, 32, 24, 6, 3, 0.45f);
        t.floorBlob(dust, 89, 63, 7, 6, 0.35f);
        t.road(plating, 1.4f, 0.4f, 38, 36, 38, 50, 44, 50);

        // --- the seams of the works floor: exact under where a line of drills stands, ragged around it
        seam(tiles, oreFerroprism, 32, 30, 2, 2);
        seam(tiles, oreFerroprism, 34, 28, 2, 2);
        seam(tiles, oreVitreousCarbon, 36, 30, 2, 2);
        seam(tiles, oreConductiveScrap, 32, 32, 2, 4);
        seam(tiles, oreResonanceCrystal, 43, 30, 6, 2);
        seam(tiles, oreVitreousCarbon, 28, 36, 2, 2);
        seam(tiles, oreFerroprism, 36, 24, 4, 2);
        t.ore(oreFerroprism, 31, 29, 2.2f, 1.6f);
        t.ore(oreConductiveScrap, 31, 34, 1.6f, 2.6f);
        t.ore(oreResonanceCrystal, 46, 29, 4f, 1.6f);
        t.ore(oreVitreousCarbon, 24, 40, 3f, 3f);
        // --- the foothold economy: ferroprism and scrap near the core
        seam(tiles, oreFerroprism, 20, 9, 4, 2);
        t.ore(oreFerroprism, 22, 10, 3.4f, 2.2f);
        t.ore(oreConductiveScrap, 10, 22, 3f, 2.4f);
        t.ore(oreVitreousCarbon, 9, 11, 2.6f, 2f);

        // --- the Works: the relay in its hall, the builders' ruins and pylons around the floor, the gate
        t.place(worksRelay, relayX, relayY, player);
        t.placeNear(stabilizerPylon, 50, 44, Team.derelict);
        t.placeNear(stabilizerPylon, 25, 30, Team.derelict);
        t.placeNear(dataTerminal, 50, 56, Team.derelict);
        t.placeNear(recoverableRuin, 36, 55, Team.derelict);
        t.placeNear(recoverableRuin, 13, 8, Team.derelict);
        gateTiles.clear();
        int[] gate = t.openNear(gateTarget[0], gateTarget[1]);
        if(gate == null) skipped.add("gate near " + gateTarget[0] + "," + gateTarget[1]);
        else gateTiles.add(gate);

        // --- the foothold and the enemy landing zone
        t.place(coreFoothold, coreX - 1, coreY - 1, player);
        tiles.getn(spawnX, spawnY).setOverlay(Blocks.spawn);
    }

    /** Ore on every tile of a rectangle of open ground (where a row of drills is meant to stand). */
    static void seam(Tiles tiles, Block ore, int x0, int y0, int w, int h){
        for(int x = x0; x < x0 + w; x++){
            for(int y = y0; y < y0 + h; y++){
                Tile tile = tiles.get(x, y);
                if(tile != null && tile.block() == Blocks.air) tile.setOverlay(ore);
                else skipped.add("seam tile " + x + "," + y);
            }
        }
    }

    /** Map rules: native waves without a win wave (the mission declares victory), Vesper blocks only, activity and the response. */
    public static Rules rules(){
        Rules r = new Rules();
        r.waves = true;
        r.waveTimer = true;
        r.waveSending = true;
        r.initialWaveSpacing = 4f * 60f * 60f;
        r.waveSpacing = 120f * 60f;
        r.winWave = 0;
        r.dropZoneRadius = 160f;
        r.derelictRepair = false;
        r.fog = false;
        r.spawns = waves();
        r.loadout = ItemStack.list(ferroprism, 1500, conductiveScrap, 700, structuralAlloy, 600, recoveredCircuit, 360, resonanceCrystal, 60, resonanceCore, 50);
        r.blockWhitelist = true;
        r.hideBannedBlocks = true;
        r.bannedBlocks.addAll(transitBelt, cargoRouter, cargoJunction, cargoBridge, resonanceLink,
            surveyDrill, scrapCollector, reinforcedDrill, crystalExtractor,
            structuralForge, scrapRecycler, alloyRefinery, combustionEngine, powerAccumulator, componentAssembler, experimentalResonator,
            reinforcedWall, heavyWall, kineticTurret, pulseTurret, stabilizerPylon, resonanceDynamo, containmentField);
        r.tags.put(FirstAwakening.tagScenario, ResonanceWorks.scenarioId);
        r.tags.put(PlanetaryActivity.tagEnabled, "true");
        r.tags.put(PlanetaryActivity.tagResponse, "true");
        StringBuilder g = new StringBuilder();
        for(int[] gate : gateTiles) g.append(g.length() == 0 ? "" : ";").append(gate[0]).append(',').append(gate[1]);
        r.tags.put(PlanetaryActivity.tagGates, g.toString());
        return r;
    }

    /**
     * Endless waves that grow slowly and stop growing (time pressure, not the main threat: the planetary response is):
     * crawlers from the first, a sentinel from the sixth, a second crawler group from the tenth; at most 4 + 2 + 2.
     */
    public static Seq<SpawnGroup> waves(){
        Seq<SpawnGroup> out = new Seq<>();
        out.add(group(VUnits.rustCrawler, 0, 2, 5f, 4));
        out.add(group(VUnits.ancientSentinel, 5, 1, 6f, 2));
        out.add(group(VUnits.rustCrawler, 9, 1, 6f, 2));
        return out;
    }

    static SpawnGroup group(UnitType type, int begin, int amount, float scaling, int max){
        SpawnGroup g = new SpawnGroup(type);
        g.begin = begin;
        g.unitAmount = amount;
        g.unitScaling = scaling;
        g.max = max;
        return g;
    }

    /** World marker naming the relay. */
    public static void addMarkers(){
        float cx = (relayX + 1f) * tilesize, cy = (relayY + 1f) * tilesize;
        ShapeTextMarker marker = new ShapeTextMarker("@vesper.marker.relay", cx, cy, 22f, 22.5f, 22f);
        marker.sides = 8;
        marker.color = new Color(VPal.resonanceLight);
        marker.minimap = 1;
        state.markers.add(1, marker);
    }

    public static StringMap tags(){
        return StringMap.of(
            "name", "Resonance Works",
            "author", "Vesper",
            "description", "Vesper 0.6 standalone map of the resonance industry. An ancient power works waits for power: burn resonance cores in dynamos and feed its relay. Only the energy it actually receives counts. How hard you push the basin to make the cores is up to you.",
            "genfilters", "[]"
        );
    }
}
