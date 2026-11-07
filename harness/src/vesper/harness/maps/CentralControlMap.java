package vesper.harness.maps;

import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.MapObjectives.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;
import vesper.content.*;
import vesper.graphics.*;
import vesper.mission.*;
import vesper.systems.*;
import vesper.world.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Central Control - the second sector of Chapter Two: the complex the deep network answered to, built for one job:
 * distributing power and material from a central controller to two industrial wings, and keeping its vents shut.
 * <pre>
 *                                 the breach (landing zone)
 *                                          |
 *   NW vent (lock) ──────────── the maintenance corridor, N vent (lock) ──────────── NE vent (lock)
 *        |                                 |                                              |
 *   W: the conduction gallery ── doorway ── the distribution chamber (controller, junction) ── doorway ── E: the processing hall
 *   (the conduction array,            |    (cyan containment conduits north,            (the processing line,
 *    carbon seams, old pylons)         |     amber recovery conduits to the wings)        scrap and crystal seams)
 *        └──── the south-west gallery ── the technical gallery ── the south-east gallery ────┘
 *                                 S: the landing bay (core, basic ores)
 * </pre>
 * Hull plating behind bulkheads, the two buses' conduits laid in the floor from the controller to what they drive,
 * collapses and derelict machinery in the corners. The controller, the junction, the two subsystems and the three
 * locks belong to the player's team from the start, dark until the controller is surveyed. The waves come out of
 * the breach into the maintenance corridor; what the planet sends climbs out of the three vents, and the pulses
 * land on the working resonance machine nearest an open one: the subsystems sit under the side vents.
 */
public class CentralControlMap{
    public static final int width = 140, height = 104;
    public static final Team player = Team.sharded;

    public static final int coreX = 70, coreY = 12;                     //centre of the 3x3 core
    public static final int controllerX = 68, controllerY = 48;         //bottom-left of the 5x5 controller
    public static final int junctionX = 69, junctionY = 40;             //bottom-left of the 3x3 junction
    public static final int arrayX = 19, arrayY = 46, lineX = 117, lineY = 46;
    /** Bottom-left tiles of the three locks (3x3): north-west, north, north-east; the vents are their centres. */
    public static final int[][] locks = {{13, 75}, {69, 77}, {125, 75}};
    public static final int spawnX = 70, spawnY = 93;

    static final int seed = 8803;
    public static final Seq<String> skipped = new Seq<>();

    static boolean[] open;
    static int[] kind;
    static final int bulkhead = 0, rubble = 1;

    public static void generate(Tiles tiles){
        Floor plating = (Floor)hullPlating, basalt = (Floor)machineBasalt, dust = (Floor)vitrifiedDust, rusted = (Floor)rustedPlating;
        skipped.clear();
        open = new boolean[width * height];
        kind = new int[width * height];

        // --- the rooms and galleries (inclusive tile rectangles)
        room(54, 4, 86, 22);                                    //the landing bay
        room(63, 23, 77, 33);                                   //the technical gallery
        room(44, 34, 96, 64);                                   //the distribution chamber
        room(38, 44, 43, 54);                                   //the west doorway
        room(6, 28, 37, 66);                                    //the conduction gallery (west wing)
        room(97, 44, 102, 54);                                  //the east doorway
        room(103, 28, 134, 66);                                 //the processing hall (east wing)
        room(16, 67, 26, 71);                                   //north passages: west wing, chamber, east wing
        room(63, 65, 77, 71);
        room(114, 67, 124, 71);
        room(6, 72, 134, 81);                                   //the maintenance corridor
        room(36, 82, 46, 90);                                   //maintenance alcoves
        room(94, 82, 104, 90);
        room(60, 82, 80, 84);                                   //the breach approach
        room(52, 85, 88, 99);                                   //the breach
        room(20, 16, 53, 22);                                   //the south-west gallery, to the west wing
        room(20, 23, 28, 27);
        room(87, 16, 120, 22);                                  //the south-east gallery, to the east wing
        room(112, 23, 120, 27);
        // --- bulkhead pillars in the big halls, away from where anything is meant to stand
        pillar(47, 36); pillar(92, 36); pillar(47, 62); pillar(92, 62);
        pillar(57, 56); pillar(82, 56);
        pillar(9, 46); pillar(32, 46); pillar(107, 54); pillar(131, 46);
        // --- collapses: the ceiling came down in corners of the complex
        collapse(8, 64, 3.2f);
        collapse(132, 64, 3.2f);
        collapse(55, 98, 3f);
        collapse(86, 97, 2.8f);
        collapse(133, 79, 2.6f);
        collapse(7, 80, 2.6f);
        collapse(85, 5, 2.4f);
        collapse(55, 5, 2.4f);
        collapse(36, 29, 2.4f);
        collapse(133, 29, 2.4f);

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                int i = x + y * width;
                boolean border = x < 3 || y < 3 || x >= width - 3 || y >= height - 3;
                Block wall = kind[i] == rubble ? machineBasaltWall : bulkheadWall;
                tiles.set(x, y, new Tile(x, y, kind[i] == rubble ? basalt : plating, Blocks.air, open[i] && !border ? Blocks.air : wall));
            }
        }
        Terrain t = new Terrain(tiles, width, height, seed, skipped);

        // --- the buses in the floor: containment (cyan) from the controller north to the three locks, recovery (amber) from the
        //controller down to the junction and out through both doorways to the subsystems
        Floor cyan = (Floor)containmentConduit, amber = (Floor)recoveryConduit;
        stripe(tiles, cyan, 70, 53, 71, 76);
        stripe(tiles, cyan, 14, 73, 126, 74);
        stripe(tiles, amber, 70, 43, 71, 47);
        stripe(tiles, amber, 46, 41, 68, 42);
        stripe(tiles, amber, 46, 43, 47, 49);
        stripe(tiles, amber, 23, 48, 45, 49);
        stripe(tiles, amber, 72, 41, 94, 42);
        stripe(tiles, amber, 93, 43, 94, 49);
        stripe(tiles, amber, 95, 48, 116, 49);
        t.floorBlob(rusted, coreX, coreY, 6f, 5f, 0.2f);
        t.floorBlob(dust, 70, 92, 10, 6, 0.4f);
        t.floorBlob(basalt, 72, 94, 6, 4, 0.5f);
        t.floorBlob(dust, 10, 62, 4, 3, 0.5f);
        t.floorBlob(dust, 130, 62, 4, 3, 0.5f);
        t.floorBlob(rusted, 128, 40, 4, 4, 0.3f);

        // --- the bay: a little of everything for the first drills, turrets and the first engine, and, east of the core, the seams of
        //a materials line (a forge between its two ferroprism drills and its carbon drill, scrap for an assembler above it)
        seam(tiles, oreFerroprism, 58, 6, 4, 2);
        t.ore(oreFerroprism, 60, 7, 3.4f, 2f);
        seam(tiles, oreVitreousCarbon, 58, 18, 4, 2);
        t.ore(oreVitreousCarbon, 60, 19, 3f, 1.8f);
        t.ore(oreFerroprism, 82, 18, 2.6f, 2f);
        seam(tiles, oreFerroprism, 74, 6, 2, 2);
        seam(tiles, oreFerroprism, 76, 4, 2, 2);
        seam(tiles, oreVitreousCarbon, 78, 6, 2, 2);
        seam(tiles, oreConductiveScrap, 78, 10, 2, 4);
        t.ore(oreConductiveScrap, 81, 12, 2f, 2.4f);
        // --- the conduction gallery: the old power wing's carbon, for engines, and a little crystal
        seam(tiles, oreVitreousCarbon, 8, 30, 6, 2);
        seam(tiles, oreVitreousCarbon, 8, 36, 6, 2);
        t.ore(oreVitreousCarbon, 11, 33.5f, 4.5f, 2f);
        t.ore(oreVitreousCarbon, 12, 58, 3f, 2.4f);
        t.ore(oreFerroprism, 30, 32, 3f, 2.2f);
        t.ore(oreResonanceCrystal, 31, 62, 2.4f, 1.6f);
        // --- the processing hall: the builders' seams side by side, exact under where a core line stands (as in the Underworks'
        //fabrication hall), and beside the processing line the scrap and crystal it eats
        seam(tiles, oreFerroprism, 110, 32, 2, 2);
        seam(tiles, oreFerroprism, 112, 30, 2, 2);
        seam(tiles, oreVitreousCarbon, 114, 32, 2, 2);
        seam(tiles, oreConductiveScrap, 110, 34, 2, 4);
        seam(tiles, oreResonanceCrystal, 121, 32, 6, 2);
        seam(tiles, oreVitreousCarbon, 106, 38, 2, 2);
        t.ore(oreVitreousCarbon, 106, 38, 3f, 2.4f);
        t.ore(oreResonanceCrystal, 124, 31, 4.4f, 1.6f);
        t.ore(oreConductiveScrap, 108, 31, 2.2f, 2.6f);
        seam(tiles, oreConductiveScrap, 115, 46, 2, 4);
        seam(tiles, oreResonanceCrystal, 121, 46, 2, 4);
        t.ore(oreResonanceCrystal, 129, 60, 2.6f, 1.8f);
        t.ore(oreConductiveScrap, 108, 62, 3f, 2f);
        t.ore(oreFerroprism, 129, 40, 2.6f, 2f);
        // --- the maintenance corridor: ferroprism by the chamber's north passage, for turrets that hold it
        seam(tiles, oreFerroprism, 66, 66, 4, 2);

        // --- the complex: the controller, the junction, the subsystems and the locks (the player's, dark until surveyed)
        t.place(centralController, controllerX, controllerY, player);
        t.place(distributionJunction, junctionX, junctionY, player);
        t.place(conductionArray, arrayX, arrayY, player);
        t.place(processingLine, lineX, lineY, player);
        for(int[] l : locks) t.place(containmentLock, l[0], l[1], player);
        // --- what else the builders left: consoles by the controller, old pylons in the power wing, ruins in the corners
        t.placeNear(dataTerminal, 60, 54, Team.derelict);
        t.placeNear(dataTerminal, 78, 54, Team.derelict);
        t.placeNear(stabilizerPylon, 12, 52, Team.derelict);
        t.placeNear(stabilizerPylon, 28, 40, Team.derelict);
        t.placeNear(recoverableRuin, 28, 58, Team.derelict);
        t.placeNear(recoverableRuin, 106, 58, Team.derelict);
        t.placeNear(recoverableRuin, 40, 86, Team.derelict);
        t.placeNear(recoverableRuin, 98, 86, Team.derelict);
        t.placeNear(dataTerminal, 128, 52, Team.derelict);

        // --- the foothold and the enemy landing zone
        t.place(coreFoothold, coreX - 1, coreY - 1, player);
        tiles.getn(spawnX, spawnY).setOverlay(Blocks.spawn);
    }

    static void room(int x0, int y0, int x1, int y1){
        for(int x = x0; x <= x1; x++){
            for(int y = y0; y <= y1; y++){
                if(x >= 0 && y >= 0 && x < width && y < height) open[x + y * width] = true;
            }
        }
    }

    /** A 2x2 bulkhead column. */
    static void pillar(int x, int y){
        for(int dx = 0; dx < 2; dx++) for(int dy = 0; dy < 2; dy++) open[x + dx + (y + dy) * width] = false;
    }

    /** A fallen piece of ceiling: basalt rubble with a ragged edge. */
    static void collapse(float cx, float cy, float r){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.dst(x, y, cx, cy) + (Simplex.noise2d(seed, 2, 0.5, 1 / 2.5, x, y) - 0.5f) * 2.4f;
                if(d < r){
                    open[x + y * width] = false;
                    kind[x + y * width] = rubble;
                }
            }
        }
    }

    /** A floor over every open tile of a rectangle. */
    static void stripe(Tiles tiles, Floor floor, int x0, int y0, int x1, int y1){
        for(int x = x0; x <= x1; x++){
            for(int y = y0; y <= y1; y++){
                Tile tile = tiles.get(x, y);
                if(tile != null && tile.block() == Blocks.air) tile.setFloor(floor);
            }
        }
    }

    static void seam(Tiles tiles, Block ore, int x0, int y0, int w, int h){
        ResonanceWorksMap.seam(tiles, ore, x0, y0, w, h);
        if(!ResonanceWorksMap.skipped.isEmpty()){
            skipped.addAll(ResonanceWorksMap.skipped);
            ResonanceWorksMap.skipped.clear();
        }
    }

    /** The vents: in front of each lock, two tiles below its bottom row (what climbs out stands clear of the lock). */
    public static String gates(){
        StringBuilder g = new StringBuilder();
        for(int[] l : locks) g.append(g.length() == 0 ? "" : ";").append(l[0] + 1).append(',').append(l[1] - 2);
        return g.toString();
    }

    /**
     * Map rules: native waves without a win wave (the mission declares victory), Vesper blocks only, activity with the
     * response, the archaeology tag (the controller is surveyed like the other sites), the complex's darkness.
     */
    public static Rules rules(){
        Rules r = new Rules();
        r.waves = true;
        r.waveTimer = true;
        r.waveSending = true;
        r.initialWaveSpacing = 4f * 60f * 60f;
        r.waveSpacing = 150f * 60f;
        r.winWave = 0;
        r.dropZoneRadius = 160f;
        r.derelictRepair = false;
        r.fog = false;
        r.lighting = true;
        r.ambientLight = new Color(0.02f, 0.03f, 0.06f, 0.72f);
        r.spawns = waves();
        //the start of a base: the survey, a defense, the first engines and the core line, and the junction's materials and cores; the
        //subsystems' materials and the cores the protocols burn come from the player's industry. 20 cores paid the junction and the
        //processing line, which then made every core the rest needed: a base with no core line won fastest (see the docs' audit)
        r.loadout = ItemStack.list(ferroprism, 1500, conductiveScrap, 600, structuralAlloy, 400, recoveredCircuit, 240, resonanceCrystal, 40, resonanceCore, 6);
        r.blockWhitelist = true;
        r.hideBannedBlocks = true;
        r.bannedBlocks.addAll(transitBelt, cargoRouter, cargoJunction, cargoBridge, cargoUnloader, resonanceLink,
            surveyDrill, scrapCollector, reinforcedDrill, crystalExtractor,
            structuralForge, scrapRecycler, alloyRefinery, combustionEngine, powerAccumulator, componentAssembler, experimentalResonator,
            reinforcedWall, heavyWall, kineticTurret, pulseTurret, ancientPlatform, stabilizerPylon, resonanceDynamo, containmentField, surveyRig);
        r.tags.put(FirstAwakening.tagScenario, CentralControl.scenarioId);
        r.tags.put(PlanetaryActivity.tagEnabled, "true");
        r.tags.put(PlanetaryActivity.tagResponse, "true");
        r.tags.put(AncientSite.tagEnabled, "true");
        r.tags.put(PlanetaryActivity.tagGates, gates());
        return r;
    }

    /** Endless waves that grow slowly and stop growing: crawlers from the first, sentinels from the fourth, a second crawler group from the seventh. */
    public static Seq<SpawnGroup> waves(){
        Seq<SpawnGroup> out = new Seq<>();
        out.add(ResonanceWorksMap.group(VUnits.rustCrawler, 0, 2, 5f, 5));
        out.add(ResonanceWorksMap.group(VUnits.ancientSentinel, 3, 1, 6f, 3));
        out.add(ResonanceWorksMap.group(VUnits.rustCrawler, 6, 1, 6f, 3));
        return out;
    }

    /** World markers: the controller, the junction and the two subsystems, and the three vents. */
    public static void addMarkers(){
        float cx = (controllerX + 2f) * tilesize, cy = (controllerY + 2f) * tilesize;
        ShapeTextMarker marker = new ShapeTextMarker("@vesper.marker.controller", cx, cy, 30f, 22.5f, 30f);
        marker.sides = 8;
        marker.color = new Color(VPal.resonanceLight);
        marker.minimap = 1;
        state.markers.add(1, marker);
        int id = 2;
        for(int[] site : new int[][]{{arrayX, arrayY, 4}, {lineX, lineY, 4}}){
            TextMarker m = new TextMarker("@vesper.marker.subsystem", (site[0] + 1.5f) * tilesize, (site[1] + 5f) * tilesize, 1f,
                (byte)(WorldLabel.flagBackground | WorldLabel.flagOutline));
            m.minimap = 1;
            state.markers.add(id++, m);
        }
        for(int[] l : locks){
            TextMarker m = new TextMarker("@vesper.marker.vent", (l[0] + 1f) * tilesize, (l[1] + 4f) * tilesize, 1f,
                (byte)(WorldLabel.flagBackground | WorldLabel.flagOutline));
            m.minimap = 1;
            state.markers.add(id++, m);
        }
    }

    public static StringMap tags(){
        return StringMap.of(
            "name", "Central Control",
            "author", "Vesper",
            "description", "Vesper 0.8 map, Chapter Two. The complex the deep network answered to still runs two protocols from one controller: containment holds its vents shut, recovery brings its machinery back. Survey the controller, route its power between them, restore both wings and run the reactivation sequence.",
            "genfilters", "[]"
        );
    }
}
