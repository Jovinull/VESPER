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
 * Deep Signal - the first sector of Chapter Two: the builders' underworks below the basin, a built installation
 * rather than a cave:
 * <pre>
 *                       N: the north relay chamber (vent) ──── the breach corridor ──── NE: the breach (landing zone)
 *                                   |
 *  W: west relay ── west corridor ── the control hall (signal terminal) ── east corridor ── E: east relay
 *                                   |
 *                              the main shaft
 *                                   |
 *         S: the landing bay (core, basic ores) ── doorway ── SE: the fabrication hall (the builders' seams)
 * </pre>
 * Square rooms of hull plating behind bulkheads, signal channels along the corridors, a collapse here and there.
 * The relays (signal nodes) and the terminal belong to the player's team from the start, dark until the terminal
 * is surveyed. Each chamber has room west of its relay for a dynamo and a router pair under both, where a belt
 * from the control hall arrives; the trunk runs straight up the shaft from the core. The waves come out of the
 * breach through the north chamber; what the planet sends climbs out of a vent in each chamber, and the pulses land
 * on the working relay machine nearest a vent: the north relay's.
 */
public class DeepSignalMap{
    public static final int width = 120, height = 96;
    public static final Team player = Team.sharded;

    public static final int coreX = 59, coreY = 11;              //centre of the 3x3 core
    public static final int terminalX = 62, terminalY = 47;      //bottom-left of the 4x4 terminal
    /** Bottom-left tiles of the three relays (3x3): west, east, north. */
    public static final int[][] nodes = {{14, 44}, {102, 44}, {59, 78}};
    public static final int spawnX = 106, spawnY = 86;
    static final int[][] gateTargets = {{6, 54}, {113, 54}, {66, 86}};
    public static final Seq<int[]> gateTiles = new Seq<>();

    static final int seed = 7741;
    public static final Seq<String> skipped = new Seq<>();

    static boolean[] open;
    static int[] kind;
    static final int bulkhead = 0, rubble = 1;

    public static void generate(Tiles tiles){
        Floor plating = (Floor)hullPlating, channel = (Floor)signalChannel, basalt = (Floor)machineBasalt, dust = (Floor)vitrifiedDust, rusted = (Floor)rustedPlating;
        skipped.clear();
        open = new boolean[width * height];
        kind = new int[width * height];

        // --- the rooms and corridors (inclusive tile rectangles)
        room(46, 4, 72, 22);                                             //the landing bay
        room(76, 4, 112, 26);                                            //the fabrication hall
        room(73, 8, 75, 16);                                             //its doorway
        room(53, 23, 64, 33);                                            //the main shaft
        room(44, 34, 76, 54);                                            //the control hall
        room(26, 38, 43, 48);                                            //west corridor
        room(5, 33, 25, 56);                                             //west relay chamber
        room(77, 38, 93, 48);                                            //east corridor
        room(94, 33, 114, 56);                                           //east relay chamber
        room(52, 55, 64, 69);                                            //north corridor
        room(44, 70, 76, 90);                                            //north relay chamber
        room(77, 79, 91, 86);                                            //the breach corridor
        room(92, 76, 114, 92);                                           //the breach
        // --- bulkhead pillars in the big halls, away from where anything is meant to stand
        pillar(46, 51); pillar(74, 51); pillar(46, 36); pillar(74, 36);
        pillar(46, 88); pillar(74, 88); pillar(46, 72);
        pillar(78, 24); pillar(110, 24);
        // --- collapses: the ceiling came down in the corners of some rooms
        collapse(112, 91, 3.5f);
        collapse(96, 77, 3f);
        collapse(7, 35, 3.2f);
        collapse(112, 35, 3f);
        collapse(70, 20, 2.6f);
        collapse(48, 5, 2.4f);
        collapse(110, 6, 3.4f);

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                int i = x + y * width;
                boolean border = x < 3 || y < 3 || x >= width - 3 || y >= height - 3;
                Block wall = kind[i] == rubble ? machineBasaltWall : bulkheadWall;
                tiles.set(x, y, new Tile(x, y, kind[i] == rubble ? basalt : plating, Blocks.air, open[i] && !border ? Blocks.air : wall));
            }
        }
        Terrain t = new Terrain(tiles, width, height, seed, skipped);

        // --- floors: signal channels down the middle of every corridor (the network's own wiring), scorched plating at
        //the breach, rust in the bay where the old landing pad stood, dust where the ceiling fell
        stripe(tiles, channel, 58, 23, 59, 33);
        stripe(tiles, channel, 58, 55, 59, 69);
        stripe(tiles, channel, 26, 43, 43, 44);
        stripe(tiles, channel, 77, 43, 93, 44);
        stripe(tiles, channel, 77, 82, 91, 83);
        //the channels run on through the halls to the terminal
        stripe(tiles, channel, 58, 34, 59, 42);
        stripe(tiles, channel, 44, 43, 61, 44);
        t.floorBlob(rusted, coreX, coreY, 6f, 5f, 0.2f);
        t.floorBlob(dust, 104, 84, 9, 6, 0.4f);
        t.floorBlob(basalt, 103, 85, 6, 4, 0.5f);
        t.floorBlob(dust, 108, 9, 4, 3, 0.5f);
        t.floorBlob(dust, 9, 37, 4, 3, 0.5f);

        // --- the bay: a little of everything for the first drills and turrets
        seam(tiles, oreFerroprism, 63, 5, 4, 2);
        t.ore(oreFerroprism, 65, 6, 3.4f, 2f);
        t.ore(oreVitreousCarbon, 50, 9, 2.8f, 2.4f);
        t.ore(oreConductiveScrap, 50, 18, 3f, 2.2f);
        t.ore(oreFerroprism, 68, 18, 2.6f, 2f);
        // --- the fabrication hall: the builders' seams side by side, exact under where a core line stands
        seam(tiles, oreFerroprism, 86, 10, 2, 2);
        seam(tiles, oreFerroprism, 88, 8, 2, 2);
        seam(tiles, oreVitreousCarbon, 90, 10, 2, 2);
        seam(tiles, oreConductiveScrap, 86, 12, 2, 4);
        seam(tiles, oreResonanceCrystal, 97, 10, 6, 2);
        seam(tiles, oreVitreousCarbon, 82, 16, 2, 2);
        t.ore(oreFerroprism, 85, 9, 2.2f, 1.6f);
        t.ore(oreConductiveScrap, 85, 14, 1.6f, 2.6f);
        t.ore(oreResonanceCrystal, 100, 9, 4f, 1.6f);
        t.ore(oreVitreousCarbon, 82, 16, 3f, 3f);
        //a carbon seam of its own in the hall's north-east corner: three drills, each feeding the engine above it
        seam(tiles, oreVitreousCarbon, 102, 18, 6, 2);
        t.ore(oreVitreousCarbon, 105, 18.5f, 4.2f, 1.6f);
        // --- the north chamber: ferroprism by its east entrance, for turrets that hold the breach
        seam(tiles, oreFerroprism, 70, 81, 4, 2);
        t.ore(oreFerroprism, 72, 82, 3f, 2f);
        // --- crystal in the side chambers, a little
        t.ore(oreResonanceCrystal, 20, 52, 2.4f, 1.6f);
        t.ore(oreResonanceCrystal, 99, 52, 2.4f, 1.6f);

        // --- the network: the control terminal and its three relays (the player's, dark until surveyed)
        t.place(signalTerminal, terminalX, terminalY, player);
        for(int[] n : nodes) t.place(signalNode, n[0], n[1], player);
        // --- what else the builders left: consoles in the control hall, pylons and ruins by the relays
        t.placeNear(dataTerminal, 69, 50, Team.derelict);
        t.placeNear(dataTerminal, 50, 50, Team.derelict);
        t.placeNear(recoverableRuin, 49, 38, Team.derelict);
        t.placeNear(stabilizerPylon, 21, 36, Team.derelict);
        t.placeNear(stabilizerPylon, 108, 51, Team.derelict);
        t.placeNear(recoverableRuin, 66, 74, Team.derelict);
        t.placeNear(recoverableRuin, 98, 18, Team.derelict);
        t.placeNear(dataTerminal, 8, 46, Team.derelict);
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

    /**
     * Map rules: native waves without a win wave (the mission declares victory), Vesper blocks only, activity with the
     * response, the archaeology tag (the terminal is surveyed like the foundry's sites), and the underworks' darkness.
     */
    public static Rules rules(){
        Rules r = new Rules();
        r.waves = true;
        r.waveTimer = true;
        r.waveSending = true;
        r.initialWaveSpacing = 5f * 60f * 60f;
        r.waveSpacing = 150f * 60f;
        r.winWave = 0;
        r.dropZoneRadius = 160f;
        r.derelictRepair = false;
        r.fog = false;
        r.lighting = true;
        r.ambientLight = new Color(0.02f, 0.03f, 0.06f, 0.72f);
        r.spawns = waves();
        //what the base costs, whichever way it is built: six dynamos and up to three fields (built with cores), the core line, the
        //trunk, turrets or stabilizers; the cores a synchronization burns come from the core line (see the docs' economy)
        r.loadout = ItemStack.list(ferroprism, 1600, conductiveScrap, 800, structuralAlloy, 1000, recoveredCircuit, 520, resonanceCrystal, 100, resonanceCore, 100);
        r.blockWhitelist = true;
        r.hideBannedBlocks = true;
        r.bannedBlocks.addAll(transitBelt, cargoRouter, cargoJunction, cargoBridge, cargoUnloader, resonanceLink,
            surveyDrill, scrapCollector, reinforcedDrill, crystalExtractor,
            structuralForge, scrapRecycler, alloyRefinery, combustionEngine, powerAccumulator, componentAssembler, experimentalResonator,
            reinforcedWall, heavyWall, kineticTurret, pulseTurret, stabilizerPylon, resonanceDynamo, containmentField, surveyRig);
        r.tags.put(FirstAwakening.tagScenario, DeepSignal.scenarioId);
        r.tags.put(PlanetaryActivity.tagEnabled, "true");
        r.tags.put(PlanetaryActivity.tagResponse, "true");
        r.tags.put(AncientSite.tagEnabled, "true");
        StringBuilder g = new StringBuilder();
        for(int[] gate : gateTiles) g.append(g.length() == 0 ? "" : ";").append(gate[0]).append(',').append(gate[1]);
        r.tags.put(PlanetaryActivity.tagGates, g.toString());
        return r;
    }

    /** Endless waves that grow slowly and stop growing: crawlers from the first, sentinels from the fifth, a second crawler group from the ninth. */
    public static Seq<SpawnGroup> waves(){
        Seq<SpawnGroup> out = new Seq<>();
        out.add(ResonanceWorksMap.group(VUnits.rustCrawler, 0, 2, 5f, 4));
        out.add(ResonanceWorksMap.group(VUnits.ancientSentinel, 4, 1, 6f, 2));
        out.add(ResonanceWorksMap.group(VUnits.rustCrawler, 8, 1, 6f, 2));
        return out;
    }

    /** World markers: the terminal and the three relays. */
    public static void addMarkers(){
        float cx = (terminalX + 1.5f) * tilesize, cy = (terminalY + 1.5f) * tilesize;
        ShapeTextMarker marker = new ShapeTextMarker("@vesper.marker.terminal", cx, cy, 26f, 22.5f, 26f);
        marker.sides = 8;
        marker.color = new Color(VPal.resonanceLight);
        marker.minimap = 1;
        state.markers.add(1, marker);
        for(int i = 0; i < nodes.length; i++){
            TextMarker m = new TextMarker("@vesper.marker.node", (nodes[i][0] + 1f) * tilesize, (nodes[i][1] + 4f) * tilesize, 1f,
                (byte)(WorldLabel.flagBackground | WorldLabel.flagOutline));
            m.minimap = 1;
            state.markers.add(2 + i, m);
        }
    }

    public static StringMap tags(){
        return StringMap.of(
            "name", "Deep Signal",
            "author", "Vesper",
            "description", "Vesper 0.7 map, Chapter Two. Below the basin the builders' underworks still run an automatic protocol. Survey the control terminal, bring its three relays online with power and resonance cores, and keep all three running through a synchronization.",
            "genfilters", "[]"
        );
    }
}
