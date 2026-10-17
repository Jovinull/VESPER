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
import vesper.world.DerelictReactor.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Resonance Fault - an experimental map of The Silent Basin for the planetary response:
 * <pre>
 *   W gate  ======== the Resonance Fault: crystal deposits, pylons, the primed reactor ========  E gate    NE: landing zone
 *                     |  the valley (choke)                                                              |  the east lane
 *   SW: the foothold (core, basic ores, a small sheltered crystal vein)  <------------------------------'
 * </pre>
 * The waves come down the east lane into the foothold; the fault is off their path, so a quiet base keeps its
 * extractors out of the fighting. What comes out of the fault gates (the awakening response, patrols) lands right
 * among the extractors. The reactor starts primed: igniting it is the loud road.
 */
public class ResonanceFaultMap{
    public static final int width = 118, height = 80;
    public static final Team player = Team.sharded;

    public static final int coreX = 22, coreY = 18;          //centre of the 3x3 core
    public static final int reactorX = 52, reactorY = 45;    //bottom-left of the 4x4 reactor
    public static final int spawnX = 108, spawnY = 72;
    static final int[][] gateTargets = {{13, 59}, {86, 57}};
    public static final Seq<int[]> gateTiles = new Seq<>();

    static final int seed = 7361;
    public static final Seq<String> skipped = new Seq<>();

    public static void generate(Tiles tiles){
        Floor basalt = (Floor)machineBasalt, soil = (Floor)scrapSoil, dust = (Floor)vitrifiedDust, plating = (Floor)rustedPlating;
        skipped.clear();
        Terrain t = new Terrain(tiles, width, height, seed, skipped);

        // --- carve the walkable world
        t.ellipse(25, 20, 21, 14);                                       //the foothold
        t.ellipse(8, 27, 6, 6);                                          //west scrap nook
        t.capsule(6f, 26, 32, 30, 46);                                   //the valley up to the fault (choke)
        t.capsule(8.5f, 16, 58, 34, 56, 52, 59, 70, 60);                 //the Resonance Fault
        t.capsule(7f, 70, 60, 85, 57);                                   //its narrowing east end
        t.ellipse(54, 49, 9, 6);                                         //the reactor deck, south of the fault
        t.ellipse(108, 71, 7, 6);                                        //landing zone
        t.capsule(6.5f, 107, 64, 105, 44, 92, 28, 70, 20, 46, 18);       //the east lane down to the foothold
        t.rock(95, 58, 3f);                                              //keeps the fault and the lane apart
        t.rock(41, 25, 2f);
        t.rock(60, 26, 2.2f);
        t.rock(98, 36, 2f);
        t.rock(44, 62, 1.8f);
        t.rock(76, 62, 1.8f);
        t.carve(basalt, machineBasaltWall);

        // --- floors: plating where the builders laid it, glass dust where the fault burned
        t.floorBlob(soil, 9, 26, 6, 5, 0.4f);
        t.floorBlob(soil, 34, 10, 9, 4, 0.35f);
        t.floorBlob(dust, 56, 58, 40, 4, 0.45f);
        t.floorBlob(dust, 108, 71, 8, 7, 0.35f);
        t.floorBlob(plating, coreX, coreY, 5.5f, 5.5f, 0.2f);
        t.floorBlob(plating, 54, 48, 7, 5, 0.25f);
        t.road(plating, 1.4f, 0.4f, 16, 58, 34, 56, 52, 59, 70, 60, 85, 57);
        t.road(plating, 1.2f, 0.45f, 28, 33, 30, 45, 52, 47);

        // --- ores: a foothold economy, a small sheltered crystal vein, and the rich deposits along the fault
        t.ore(oreFerroprism, 36, 14, 3.8f, 2.8f);
        t.ore(oreFerroprism, 13, 32, 3f, 2.4f);
        t.ore(oreVitreousCarbon, 32, 8, 4.4f, 2.4f);
        t.ore(oreVitreousCarbon, 13, 11, 3.4f, 2.6f);
        t.ore(oreConductiveScrap, 7, 26, 3f, 3.4f);
        t.ore(oreConductiveScrap, 40, 20, 2.6f, 2.2f);
        t.ore(oreResonanceCrystal, 33, 26, 2.6f, 2f);
        t.ore(oreResonanceCrystal, 27, 58, 3.6f, 2.4f);
        t.ore(oreResonanceCrystal, 43, 57, 3.8f, 2.4f);
        t.ore(oreResonanceCrystal, 62, 61, 3.8f, 2.3f);
        t.ore(oreResonanceCrystal, 80, 59, 3.6f, 2.3f);
        t.ore(oreVitreousCarbon, 70, 53, 3f, 2.2f);

        // --- the fault: the primed reactor on its deck, pylons along the crack, the gates at both ends
        t.place(derelictReactor, reactorX, reactorY, player);
        t.placeNear(stabilizerPylon, 21, 62, Team.derelict);
        t.placeNear(stabilizerPylon, 52, 63, Team.derelict);
        t.placeNear(stabilizerPylon, 82, 61, Team.derelict);
        t.placeNear(dataTerminal, 45, 46, Team.derelict);
        t.placeNear(dataTerminal, 61, 46, Team.derelict);
        t.placeNear(recoverableRuin, 70, 63, Team.derelict);
        t.placeNear(recoverableRuin, 35, 61, Team.derelict);
        t.placeNear(recoverableRuin, 95, 40, Team.derelict);
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

    /** The reactor starts restored: only the ignition is left to decide. */
    public static void primeReactor(){
        if(world.build(reactorX + 1, reactorY + 1) instanceof ReactorBuild r){
            r.handleStack(structuralAlloy, 300, null);
            r.handleStack(recoveredCircuit, 100, null);
        }
    }

    /** Map rules: native waves without a win wave (the mission declares victory), Vesper blocks only, the 0.3 tags. */
    public static Rules rules(){
        Rules r = new Rules();
        r.waves = true;
        r.waveTimer = true;
        r.waveSending = true;
        r.initialWaveSpacing = 3f * 60f * 60f;
        r.waveSpacing = 120f * 60f;
        r.winWave = 0;
        r.dropZoneRadius = 160f;
        r.derelictRepair = false;
        r.fog = false;
        r.spawns = waves();
        r.loadout = ItemStack.list(ferroprism, 900, conductiveScrap, 400, structuralAlloy, 120, recoveredCircuit, 150);
        r.blockWhitelist = true;
        r.hideBannedBlocks = true;
        r.bannedBlocks.addAll(transitBelt, cargoRouter, cargoJunction, cargoBridge, resonanceLink,
            surveyDrill, scrapCollector, reinforcedDrill, crystalExtractor,
            structuralForge, scrapRecycler, alloyRefinery, combustionEngine, powerAccumulator, componentAssembler, experimentalResonator,
            reinforcedWall, heavyWall, kineticTurret, pulseTurret, stabilizerPylon);
        r.tags.put(FirstAwakening.tagScenario, ResonanceFault.scenarioId);
        r.tags.put(PlanetaryActivity.tagEnabled, "true");
        r.tags.put(PlanetaryActivity.tagResponse, "true");
        StringBuilder g = new StringBuilder();
        for(int[] gate : gateTiles) g.append(g.length() == 0 ? "" : ";").append(gate[0]).append(',').append(gate[1]);
        r.tags.put(PlanetaryActivity.tagGates, g.toString());
        return r;
    }

    /**
     * Endless waves that grow slowly (time pressure, not the main threat of this map): crawlers from the first,
     * a sentinel from the fourth, a second crawler group from the seventh. There is no win wave: a slow strategy
     * simply meets more of them. Waves 1-11: 2, 2, 2, 3+1, 3+1, 3+1, 5+1, 5+2, 5+2, 7+2, 7+2 (crawlers + sentinels).
     */
    public static Seq<SpawnGroup> waves(){
        Seq<SpawnGroup> out = new Seq<>();
        out.add(group(VUnits.rustCrawler, 0, 2, 3f, 6));
        out.add(group(VUnits.ancientSentinel, 3, 1, 4f, 3));
        out.add(group(VUnits.rustCrawler, 6, 1, 3f, 4));
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

    /** World marker naming the reactor. */
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
            "name", "Resonance Fault - The Silent Basin",
            "author", "Vesper",
            "description", "Vesper 0.3 experimental map. Mine the resonance crystal of a fault and store it in your core. Stabilizers keep the basin quiet and slow; the primed reactor and heavy industry make the crystal flow up to twice as fast, and wake what sleeps in the fault.",
            "genfilters", "[]"
        );
    }
}
