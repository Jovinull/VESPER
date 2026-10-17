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
    /** Decorations that did not fit (reported by the maps suite; the layout must leave none). */
    public static final Seq<String> skipped = new Seq<>();

    public static void generate(Tiles tiles){
        Floor basalt = (Floor)machineBasalt, soil = (Floor)scrapSoil, dust = (Floor)vitrifiedDust, plating = (Floor)rustedPlating;
        skipped.clear();
        Terrain t = new Terrain(tiles, width, height, seed, skipped);

        // --- carve the walkable world
        t.ellipse(36, 27, 30, 19);                                         //foothold plateau
        t.ellipse(12, 31, 9, 9);                                           //west scrap nook
        t.ellipse(53, 49, 7, 5);                                           //north-east alcove
        t.capsule(8f, 26, 40, 24, 52, 27, 64);                             //valley to the facility
        t.ellipse(27, 80, 23, 15);                                         //ancient facility
        t.capsule(8f, 58, 38, 72, 46);                                     //main lane, lower part
        t.capsule(4.5f, 72, 46, 80, 50);                                   //the Gate (choke)
        t.capsule(8f, 80, 50, 96, 62, 116, 74, 136, 86);                   //main lane, upper part
        t.ellipse(139, 89, 9, 8);                                          //landing zone
        t.capsule(6f, 116, 74, 126, 56, 118, 34, 98, 18, 78, 14);          //south loop
        t.capsule(4f, 78, 14, 70, 15);                                     //the South Pass (choke)
        t.capsule(6f, 70, 15, 60, 17);
        t.ellipse(100, 38, 13, 10);                                        //the Basin
        t.capsule(5f, 106, 36, 118, 34);
        //outcrops that break up the open fields without closing any route
        t.rock(61, 29, 2.6f);
        t.rock(48, 41, 2f);
        t.rock(87, 57, 2.2f);
        t.rock(125, 64, 2.4f);
        t.rock(104, 22, 2.2f);
        t.rock(9, 22, 2f);
        t.rock(36, 70, 1.8f);

        t.carve(basalt, machineBasaltWall);

        // --- floors: plating where the builders laid it, soil where scrap was dumped, glass dust where it burned
        t.floorBlob(soil, 14, 28, 10, 9, 0.35f);
        t.floorBlob(soil, 42, 12, 13, 5, 0.35f);
        t.floorBlob(soil, 10, 70, 6, 5, 0.4f);
        t.floorBlob(dust, 100, 38, 14, 11, 0.3f);
        t.floorBlob(dust, 135, 86, 14, 10, 0.35f);
        t.floorBlob(dust, 88, 57, 6, 4, 0.5f);
        t.floorBlob(dust, 120, 50, 5, 7, 0.5f);
        t.floorBlob(plating, coreX, coreY, 5.5f, 5.5f, 0.2f);
        t.floorBlob(plating, 26, 80, 14, 11, 0.25f);
        t.floorBlob(plating, 100, 38, 5, 4, 0.5f);
        t.floorBlob(plating, 78, 49, 3, 3, 0.5f);
        t.road(plating, 1.6f, 0.33f, 26, 68, 25, 52, 27, 40, 28, 32);
        //the old transit road the machines still follow down from the landing zone
        t.road(plating, 1.4f, 0.45f, 62, 40, 80, 50, 96, 62, 116, 74, 134, 85);
        t.floorBlob(soil, 88, 61, 4, 3, 0.6f);
        t.floorBlob(soil, 108, 70, 4, 3, 0.6f);
        t.floorBlob(soil, 82, 17, 5, 3, 0.6f);

        // --- ores: enough around the core to start, more up the valley, the richest in the exposed Basin
        t.ore(oreFerroprism, 39, 23, 3.8f, 2.8f);
        t.ore(oreFerroprism, 20, 37, 3.2f, 2.6f);
        t.ore(oreVitreousCarbon, 33, 14, 4.2f, 2.5f);
        t.ore(oreVitreousCarbon, 16, 19, 3f, 2.7f);
        t.ore(oreConductiveScrap, 11, 29, 3.2f, 3.6f);
        t.ore(oreConductiveScrap, 47, 33, 2.6f, 2.2f);
        t.ore(oreFerroprism, 53, 49, 3f, 2.2f);
        t.ore(oreFerroprism, 14, 67, 3.2f, 2.6f);
        t.ore(oreVitreousCarbon, 40, 68, 3.4f, 2.5f);
        t.ore(oreConductiveScrap, 45, 78, 2.6f, 3.2f);
        t.ore(oreResonanceCrystal, 27, 92, 3.6f, 1.8f);
        t.ore(oreResonanceCrystal, 8, 82, 2f, 2.6f);
        t.ore(oreFerroprism, 96, 40, 4.2f, 3f);
        t.ore(oreVitreousCarbon, 104, 33, 3.2f, 2.5f);
        t.ore(oreConductiveScrap, 103, 44, 2.6f, 2.1f);

        // --- the ancient facility: the reactor ringed by pylons, terminals and the two breaches (gates)
        t.place(derelictReactor, reactorX, reactorY, player);
        t.placeNear(stabilizerPylon, 17, 85, Team.derelict);
        t.placeNear(stabilizerPylon, 33, 85, Team.derelict);
        t.placeNear(stabilizerPylon, 17, 73, Team.derelict);
        t.placeNear(stabilizerPylon, 33, 73, Team.derelict);
        t.placeNear(dataTerminal, 38, 79, Team.derelict);
        t.placeNear(dataTerminal, 11, 79, Team.derelict);
        t.placeNear(recoverableRuin, 8, 88, Team.derelict);
        t.placeNear(recoverableRuin, 44, 89, Team.derelict);
        // gateposts where the old road leaves the valley, and relics along the routes
        t.placeNear(stabilizerPylon, 19, 57, Team.derelict);
        t.placeNear(stabilizerPylon, 31, 57, Team.derelict);
        t.placeNear(recoverableRuin, 88, 61, Team.derelict);
        t.placeNear(recoverableRuin, 108, 70, Team.derelict);
        t.placeNear(recoverableRuin, 97, 46, Team.derelict);
        t.placeNear(stabilizerPylon, 107, 40, Team.derelict);
        t.placeNear(dataTerminal, 82, 17, Team.derelict);
        t.placeNear(recoverableRuin, 7, 41, Team.derelict);

        // --- the breaches the ancient machines come out of: nearest open tile with room around it
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
}
