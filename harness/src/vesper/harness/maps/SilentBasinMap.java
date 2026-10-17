package vesper.harness.maps;

import arc.graphics.*;
import arc.math.*;
import arc.struct.*;
import arc.util.noise.*;
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
 * First Awakening - The Silent Basin. A hand-composed layout drawn with noisy shapes:
 * <pre>
 *   NW: the ancient facility (derelict reactor, pylons, gates)      NE: enemy landing zone
 *    |  valley with an old plated road                              |  the main lane descends SW
 *   SW: the foothold plateau (core, starting ores)  <- the Gate ----'
 *          '------------- the South Pass <- south loop past the Basin (rich, exposed deposits)
 * </pre>
 * Coordinates are tiles, y-up. Everything that is not carved open is basalt ridge.
 */
public class SilentBasinMap{
    public static final int width = 150, height = 100;
    public static final Team player = Team.sharded;

    public static final int coreX = 26, coreY = 26;          //centre of the 3x3 core
    public static final int reactorX = 24, reactorY = 78;    //bottom-left of the 4x4 reactor
    public static final int spawnX = 140, spawnY = 89;
    /** Where the facility's breaches should be; generate() snaps them to open ground (see {@link #gateTiles}). */
    static final int[][] gateTargets = {{11, 86}, {43, 87}};
    public static final Seq<int[]> gateTiles = new Seq<>();

    static final int seed = 4127;
    static float[] open;
    /** Decorations that did not fit (reported by the maps suite; the layout must leave none). */
    public static final Seq<String> skipped = new Seq<>();

    public static void generate(Tiles tiles){
        Floor basalt = (Floor)machineBasalt, soil = (Floor)scrapSoil, dust = (Floor)vitrifiedDust, plating = (Floor)rustedPlating;
        skipped.clear();
        open = new float[width * height];
        java.util.Arrays.fill(open, -99f);

        // --- carve the walkable world
        ellipse(36, 27, 30, 19);                                         //foothold plateau
        ellipse(12, 31, 9, 9);                                           //west scrap nook
        ellipse(53, 49, 7, 5);                                           //north-east alcove
        capsule(8f, 26, 40, 24, 52, 27, 64);                             //valley to the facility
        ellipse(27, 80, 23, 15);                                         //ancient facility
        capsule(8f, 58, 38, 72, 46);                                     //main lane, lower part
        capsule(4.5f, 72, 46, 80, 50);                                   //the Gate (choke)
        capsule(8f, 80, 50, 96, 62, 116, 74, 136, 86);                   //main lane, upper part
        ellipse(139, 89, 9, 8);                                          //landing zone
        capsule(6f, 116, 74, 126, 56, 118, 34, 98, 18, 78, 14);          //south loop
        capsule(4f, 78, 14, 70, 15);                                     //the South Pass (choke)
        capsule(6f, 70, 15, 60, 17);
        ellipse(100, 38, 13, 10);                                        //the Basin
        capsule(5f, 106, 36, 118, 34);
        //outcrops that break up the open fields without closing any route
        rock(61, 29, 2.6f);
        rock(48, 41, 2f);
        rock(87, 57, 2.2f);
        rock(125, 64, 2.4f);
        rock(104, 22, 2.2f);
        rock(9, 22, 2f);
        rock(36, 70, 1.8f);

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float n = Simplex.noise2d(seed, 3, 0.5, 1 / 7.0, x, y) * 3f - 1.5f;
                float v = open[x + y * width] + n;
                boolean border = x < 3 || y < 3 || x >= width - 3 || y >= height - 3;
                Block wall = v > 0f && !border ? Blocks.air : machineBasaltWall;
                tiles.set(x, y, new Tile(x, y, basalt, Blocks.air, wall));
            }
        }

        // --- floors: plating where the builders laid it, soil where scrap was dumped, glass dust where it burned
        floorBlob(tiles, soil, 14, 28, 10, 9, 0.35f);
        floorBlob(tiles, soil, 42, 12, 13, 5, 0.35f);
        floorBlob(tiles, soil, 10, 70, 6, 5, 0.4f);
        floorBlob(tiles, dust, 100, 38, 14, 11, 0.3f);
        floorBlob(tiles, dust, 135, 86, 14, 10, 0.35f);
        floorBlob(tiles, dust, 88, 57, 6, 4, 0.5f);
        floorBlob(tiles, dust, 120, 50, 5, 7, 0.5f);
        floorBlob(tiles, plating, coreX, coreY, 5.5f, 5.5f, 0.2f);
        floorBlob(tiles, plating, 26, 80, 14, 11, 0.25f);
        floorBlob(tiles, plating, 100, 38, 5, 4, 0.5f);
        floorBlob(tiles, plating, 78, 49, 3, 3, 0.5f);
        road(tiles, plating, 1.6f, 0.33f, 26, 68, 25, 52, 27, 40, 28, 32);
        //the old transit road the machines still follow down from the landing zone
        road(tiles, plating, 1.4f, 0.45f, 62, 40, 80, 50, 96, 62, 116, 74, 134, 85);
        floorBlob(tiles, soil, 88, 61, 4, 3, 0.6f);
        floorBlob(tiles, soil, 108, 70, 4, 3, 0.6f);
        floorBlob(tiles, soil, 82, 17, 5, 3, 0.6f);

        // --- ores: enough around the core to start, more up the valley, the richest in the exposed Basin
        ore(tiles, oreFerroprism, 39, 23, 3.8f, 2.8f);
        ore(tiles, oreFerroprism, 20, 37, 3.2f, 2.6f);
        ore(tiles, oreVitreousCarbon, 33, 14, 4.2f, 2.5f);
        ore(tiles, oreVitreousCarbon, 16, 19, 3f, 2.7f);
        ore(tiles, oreConductiveScrap, 11, 29, 3.2f, 3.6f);
        ore(tiles, oreConductiveScrap, 47, 33, 2.6f, 2.2f);
        ore(tiles, oreFerroprism, 53, 49, 3f, 2.2f);
        ore(tiles, oreFerroprism, 14, 67, 3.2f, 2.6f);
        ore(tiles, oreVitreousCarbon, 40, 68, 3.4f, 2.5f);
        ore(tiles, oreConductiveScrap, 45, 78, 2.6f, 3.2f);
        ore(tiles, oreResonanceCrystal, 27, 92, 3.6f, 1.8f);
        ore(tiles, oreResonanceCrystal, 8, 82, 2f, 2.6f);
        ore(tiles, oreFerroprism, 96, 40, 4.2f, 3f);
        ore(tiles, oreVitreousCarbon, 104, 33, 3.2f, 2.5f);
        ore(tiles, oreConductiveScrap, 103, 44, 2.6f, 2.1f);

        // --- the ancient facility: the reactor ringed by pylons, terminals and the two breaches (gates)
        place(tiles, derelictReactor, reactorX, reactorY, player);
        placeNear(tiles, stabilizerPylon, 17, 85, Team.derelict);
        placeNear(tiles, stabilizerPylon, 33, 85, Team.derelict);
        placeNear(tiles, stabilizerPylon, 17, 73, Team.derelict);
        placeNear(tiles, stabilizerPylon, 33, 73, Team.derelict);
        placeNear(tiles, dataTerminal, 38, 79, Team.derelict);
        placeNear(tiles, dataTerminal, 11, 79, Team.derelict);
        placeNear(tiles, recoverableRuin, 8, 88, Team.derelict);
        placeNear(tiles, recoverableRuin, 44, 89, Team.derelict);
        // gateposts where the old road leaves the valley, and relics along the routes
        placeNear(tiles, stabilizerPylon, 19, 57, Team.derelict);
        placeNear(tiles, stabilizerPylon, 31, 57, Team.derelict);
        placeNear(tiles, recoverableRuin, 88, 61, Team.derelict);
        placeNear(tiles, recoverableRuin, 108, 70, Team.derelict);
        placeNear(tiles, recoverableRuin, 97, 46, Team.derelict);
        placeNear(tiles, stabilizerPylon, 107, 40, Team.derelict);
        placeNear(tiles, dataTerminal, 82, 17, Team.derelict);
        placeNear(tiles, recoverableRuin, 7, 41, Team.derelict);

        // --- the breaches the ancient machines come out of: nearest open tile with room around it
        gateTiles.clear();
        for(int[] g : gateTargets){
            int[] best = null;
            for(int r = 0; r <= 8 && best == null; r++){
                for(int dx = -r; dx <= r && best == null; dx++){
                    for(int dy = -r; dy <= r && best == null; dy++){
                        int x = g[0] + dx, y = g[1] + dy;
                        boolean room = true;
                        for(int ox = -1; ox <= 1; ox++) for(int oy = -1; oy <= 1; oy++) room &= walkable(tiles, x + ox, y + oy);
                        if(room) best = new int[]{x, y};
                    }
                }
            }
            if(best == null) skipped.add("gate near " + g[0] + "," + g[1]);
            else gateTiles.add(best);
        }

        // --- the foothold and the enemy landing zone
        place(tiles, coreFoothold, coreX - 1, coreY - 1, player);
        tiles.getn(spawnX, spawnY).setOverlay(Blocks.spawn);
    }

    /** Map rules: native waves, limited loadout, Vesper blocks only, and the tags that enable the Vesper systems. */
    public static Rules rules(){
        Rules r = new Rules();
        r.waves = true;
        r.waveTimer = true;
        r.waveSending = true;
        r.initialWaveSpacing = 4f * 60f * 60f;
        r.waveSpacing = 150f * 60f;
        r.winWave = 0;                       //armed by the mission when the basin awakens
        r.dropZoneRadius = 200f;
        r.derelictRepair = false;
        r.fog = false;
        r.spawns = waves();
        r.loadout = ItemStack.list(ferroprism, 300, conductiveScrap, 120);
        r.blockWhitelist = true;
        r.hideBannedBlocks = true;
        r.bannedBlocks.addAll(transitBelt, cargoRouter, cargoJunction, cargoBridge, resonanceLink,
            surveyDrill, scrapCollector, reinforcedDrill, crystalExtractor,
            structuralForge, scrapRecycler, alloyRefinery, combustionEngine, powerAccumulator, componentAssembler,
            reinforcedWall, heavyWall, kineticTurret, pulseTurret);
        r.tags.put(FirstAwakening.tagScenario, FirstAwakening.scenarioId);
        r.tags.put(PlanetaryActivity.tagEnabled, "true");
        StringBuilder g = new StringBuilder();
        for(int[] gate : gateTiles) g.append(g.length() == 0 ? "" : ";").append(gate[0]).append(',').append(gate[1]);
        r.tags.put(PlanetaryActivity.tagGates, g.toString());
        return r;
    }

    /** Six attack waves: scouting crawlers, then sentinels; waves 5-6 are the awakening (held until it happens). */
    public static Seq<SpawnGroup> waves(){
        Seq<SpawnGroup> out = new Seq<>();
        int[][] plan = {
            //crawlers, sentinels per wave (index = wave - 1)
            {2, 0}, {3, 0}, {3, 1}, {4, 2}, {4, 2}, {5, 3}
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

    /** World marker naming the reactor, so the objective's target is easy to find. */
    public static void addMarkers(){
        float cx = (reactorX + 1.5f) * tilesize, cy = (reactorY + 1.5f) * tilesize;
        ShapeTextMarker marker = new ShapeTextMarker("@vesper.marker.reactor", cx, cy, 26f, 22.5f, 26f);
        marker.sides = 8;
        marker.color = new Color(VPal.resonanceLight);
        marker.minimap = 1;
        state.markers.add(1, marker);
    }

    public static StringMap tags(){
        return StringMap.of(
            "name", "First Awakening - The Silent Basin",
            "author", "Vesper",
            "description", "Vesper 0.2 vertical slice. Build a foothold in a dead industrial basin, restore the derelict reactor in the north-west and survive what it wakes.",
            //no generation filters: without this tag the game scatters random vanilla ores over the map on every load
            "genfilters", "[]"
        );
    }

    // --- shape helpers: each region raises the "open" field; positive (plus noise) is walkable

    static void ellipse(float cx, float cy, float rx, float ry){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.sqrt(Mathf.sqr((x - cx) / rx) + Mathf.sqr((y - cy) / ry));
                raise(x, y, (1f - d) * Math.min(rx, ry));
            }
        }
    }

    static void capsule(float r, float... pts){
        for(int i = 0; i + 3 < pts.length; i += 2){
            float ax = pts[i], ay = pts[i + 1], bx = pts[i + 2], by = pts[i + 3];
            for(int x = 0; x < width; x++){
                for(int y = 0; y < height; y++){
                    raise(x, y, r - segment(x, y, ax, ay, bx, by));
                }
            }
        }
    }

    static float segment(float px, float py, float ax, float ay, float bx, float by){
        float dx = bx - ax, dy = by - ay;
        float t = Mathf.clamp(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy));
        return Mathf.dst(px, py, ax + dx * t, ay + dy * t);
    }

    /** Lowers the open field into a small rock island. */
    static void rock(float cx, float cy, float r){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.dst(x, y, cx, cy) - r;
                int i = x + y * width;
                if(d < open[i]) open[i] = d;
            }
        }
    }

    static void raise(int x, int y, float v){
        int i = x + y * width;
        if(v > open[i]) open[i] = v;
    }

    static boolean walkable(Tiles tiles, int x, int y){
        Tile t = tiles.get(x, y);
        return t != null && t.block() == Blocks.air;
    }

    static void floorBlob(Tiles tiles, Floor floor, float cx, float cy, float rx, float ry, float roughness){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.sqrt(Mathf.sqr((x - cx) / rx) + Mathf.sqr((y - cy) / ry));
                float n = Simplex.noise2d(seed + floor.id, 3, 0.5, 1 / 5.0, x, y) - 0.5f;
                if(d + n * roughness * 2f < 1f) tiles.getn(x, y).setFloor(floor);
            }
        }
    }

    /** A broken plated road: a 3-wide strip along the polyline with gaps where the plates are gone. */
    static void road(Tiles tiles, Floor floor, float halfWidth, float gaps, float... pts){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float best = 99f;
                for(int i = 0; i + 3 < pts.length; i += 2){
                    best = Math.min(best, segment(x, y, pts[i], pts[i + 1], pts[i + 2], pts[i + 3]));
                }
                if(best < halfWidth && Simplex.noise2d(seed + 77, 2, 0.5, 1 / 3.0, x, y) > gaps) tiles.getn(x, y).setFloor(floor);
            }
        }
    }

    static void ore(Tiles tiles, Block ore, float cx, float cy, float rx, float ry){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.sqrt(Mathf.sqr((x - cx) / rx) + Mathf.sqr((y - cy) / ry));
                float n = Simplex.noise2d(seed + ore.id, 2, 0.5, 1 / 3.0, x, y) - 0.5f;
                if(d + n * 0.5f < 1f && walkable(tiles, x, y)) tiles.getn(x, y).setOverlay(ore);
            }
        }
    }

    /** Places a decoration at the nearest footprint of open ground (within 6 tiles) around the given one. */
    static void placeNear(Tiles tiles, Block block, int left, int bottom, Team team){
        for(int r = 0; r <= 6; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if(fits(tiles, block, left + dx, bottom + dy, 1)){
                        place(tiles, block, left + dx, bottom + dy, team);
                        return;
                    }
                }
            }
        }
        skipped.add(block.name + " near " + left + "," + bottom);
    }

    /** Whether the footprint and a margin around it are open ground (decorations never seal a passage). */
    static boolean fits(Tiles tiles, Block block, int left, int bottom, int margin){
        for(int x = left - margin; x < left + block.size + margin; x++){
            for(int y = bottom - margin; y < bottom + block.size + margin; y++){
                if(!walkable(tiles, x, y)) return false;
            }
        }
        return true;
    }

    /** Places a block by the bottom-left tile of its footprint; the footprint must be walkable ground. */
    static void place(Tiles tiles, Block block, int left, int bottom, Team team){
        for(int x = left; x < left + block.size; x++){
            for(int y = bottom; y < bottom + block.size; y++){
                if(!walkable(tiles, x, y)){
                    skipped.add(block.name + " at " + left + "," + bottom);
                    return;
                }
            }
        }
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, 0);
    }
}
