package vesper.harness.suites;

import arc.files.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.world.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.harness.maps.*;

import static mindustry.Vars.*;

/**
 * Generates the maps (written to vesper-harness/maps/, imported by ./gradlew importMaps), with a one pixel per
 * tile preview, and checks the structure of the Silent Basin maps: one core, one spawn, the reactor, reachable
 * gates, ground paths to the core and enough starting ore around it; for Resonance Fault also the primed reactor
 * and crystal both sheltered and along the fault.
 */
public class MapsSuite implements VesperHarness.Suite{
    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> {
            logic.reset();
            world.loadGenerator(Gallery.width, Gallery.height, Gallery::generate);
            state.rules = Gallery.rules();
            logic.play();
        });
        s.wait(0.3f);
        s.run(() -> {
            Gallery.connectLinks();
            save(h, "vesper-gallery", Gallery.tags());
        });

        s.run(() -> {
            logic.reset();
            world.loadGenerator(SilentBasinMap.width, SilentBasinMap.height, SilentBasinMap::generate);
            state.rules = SilentBasinMap.rules();
            SilentBasinMap.addMarkers();
            logic.play();
        });
        s.wait(0.3f);
        s.run(() -> {
            save(h, "first-awakening", SilentBasinMap.tags());
            dump(h.out.child("maps").child("first-awakening-tiles.txt"));
            checkBasin(h);
            noAncientDeconstruction(h, "basin");
        });
        //composition overview: four quadrants and the key places
        s.run(() -> state.rules.waveTimer = false);
        h.shot("basin-01-foothold", 34, 28, 2f, false);
        h.shot("basin-02-facility", 27, 76, 2f, false);
        h.shot("basin-03-main-lane", 92, 60, 2f, false);
        h.shot("basin-04-south-basin", 100, 30, 2f, false);
        h.shot("basin-05-landing-zone", 128, 82, 2f, false);
        h.shot("basin-06-reactor-close", 26, 80, 4f, false);
        h.shot("basin-07-core-close", 28, 26, 4f, false);

        s.run(() -> {
            logic.reset();
            world.loadGenerator(ResonanceFaultMap.width, ResonanceFaultMap.height, ResonanceFaultMap::generate);
            state.rules = ResonanceFaultMap.rules();
            ResonanceFaultMap.addMarkers();
            logic.play();
        });
        s.wait(0.3f);
        s.run(() -> {
            ResonanceFaultMap.primeReactor();
            save(h, "resonance-fault", ResonanceFaultMap.tags());
            dump(h.out.child("maps").child("resonance-fault-tiles.txt"));
            checkFault(h);
            noAncientDeconstruction(h, "fault");
        });
        s.run(() -> state.rules.waveTimer = false);
        h.shot("fault-01-foothold", 26, 22, 2f, false);
        h.shot("fault-02-valley", 30, 40, 2f, false);
        h.shot("fault-03-west-fault", 32, 57, 2f, false);
        h.shot("fault-04-reactor", 54, 52, 2f, false);
        h.shot("fault-05-east-fault", 80, 58, 2f, false);
        h.shot("fault-06-east-lane", 98, 42, 2f, false);

        s.run(() -> {
            logic.reset();
            world.loadGenerator(BuriedFoundryMap.width, BuriedFoundryMap.height, BuriedFoundryMap::generate);
            state.rules = BuriedFoundryMap.rules();
            BuriedFoundryMap.addMarkers();
            logic.play();
        });
        s.wait(0.3f);
        s.run(() -> {
            save(h, "buried-foundry", BuriedFoundryMap.tags());
            dump(h.out.child("maps").child("buried-foundry-tiles.txt"));
            checkFoundry(h);
            noAncientDeconstruction(h, "foundry map");
        });
        s.run(() -> state.rules.waveTimer = false);
        h.shot("foundry-01-foothold", 24, 20, 2f, false);
        h.shot("foundry-02-trench", 36, 34, 2f, false);
        h.shot("foundry-03-hall", 56, 52, 1.6f, false);
        h.shot("foundry-04-west-alcove", 33, 51, 3f, false);
        h.shot("foundry-05-furnace", 55, 52, 3f, false);
        h.shot("foundry-06-archive", 56, 74, 3f, false);
        h.shot("foundry-07-east-lane", 92, 40, 2f, false);

        s.run(() -> {
            logic.reset();
            world.loadGenerator(ResonanceWorksMap.width, ResonanceWorksMap.height, ResonanceWorksMap::generate);
            state.rules = ResonanceWorksMap.rules();
            ResonanceWorksMap.addMarkers();
            logic.play();
        });
        s.wait(0.3f);
        s.run(() -> {
            save(h, "resonance-works", ResonanceWorksMap.tags());
            dump(h.out.child("maps").child("resonance-works-tiles.txt"));
            checkWorks(h);
            noAncientDeconstruction(h, "works map");
        });
        s.run(() -> state.rules.waveTimer = false);
        h.shot("works-01-foothold", 18, 18, 2f, false);
        h.shot("works-02-floor", 38, 34, 2.5f, false);
        h.shot("works-03-hall", 44, 51, 3f, false);
        h.shot("works-04-east-lane", 70, 36, 2f, false);

        s.run(() -> {
            logic.reset();
            world.loadGenerator(DeepSignalMap.width, DeepSignalMap.height, DeepSignalMap::generate);
            state.rules = DeepSignalMap.rules();
            DeepSignalMap.addMarkers();
            logic.play();
        });
        s.wait(0.3f);
        s.run(() -> {
            save(h, "deep-signal", DeepSignalMap.tags());
            dump(h.out.child("maps").child("deep-signal-tiles.txt"));
            checkDeep(h);
            noAncientDeconstruction(h, "deep map");
        });
        s.run(() -> state.rules.waveTimer = false);
        h.shot("deep-01-bay", 59, 13, 2f, false);
        h.shot("deep-02-fabrication", 94, 15, 2f, false);
        h.shot("deep-03-control-hall", 60, 45, 2f, false);
        h.shot("deep-04-west-relay", 15, 45, 2.5f, false);
        h.shot("deep-05-east-relay", 103, 45, 2.5f, false);
        h.shot("deep-06-north-relay", 60, 80, 2f, false);
        h.shot("deep-07-breach", 100, 84, 2f, false);
    }

    /** The tiles the reference layout builds the core logistics on: the unloader over the core, the trunk up the shaft, the hub and three branches. */
    public static Seq<int[]> deepRoute(){
        Seq<int[]> out = new Seq<>();
        for(int y = 13; y <= 43; y++) out.add(new int[]{58, y});
        for(int x = 15; x <= 57; x++) out.add(new int[]{x, 43});
        for(int x = 59; x <= 100; x++) out.add(new int[]{x, 43});
        for(int y = 44; y <= 76; y++) out.add(new int[]{58, y});
        return out;
    }

    static void checkDeep(VesperHarness h){
        Team player = DeepSignalMap.player;
        h.check("deep map: every decoration and seam fits the terrain", DeepSignalMap.skipped.isEmpty(), "skipped: " + DeepSignalMap.skipped);
        h.check("deep map: one player core, one ground spawn", player.data().cores.size == 1 && spawner.countSpawns() == 1,
            "cores " + player.data().cores.size + ", spawns " + spawner.countSpawns());
        var terminals = player.data().getBuildings(VBlocks.signalTerminal);
        var relays = player.data().getBuildings(VBlocks.signalNode);
        h.check("deep map: the control terminal and three relays, the player's, the terminal unexplored", terminals.size == 1 && relays.size == 3
            && terminals.first() instanceof vesper.world.SignalTerminal.TerminalBuild t && t.siteState == vesper.world.AncientSite.SiteState.unexplored, "");
        h.check("deep map: only the core, the terminal and the relays are the player's", player.data().buildings.size == 5, player.data().buildings.size + " player buildings");
        h.check("deep map: the relays cannot be deconstructed and draw fire", relays.allMatch(b -> !Build.validBreak(player, b.tileX(), b.tileY()))
            && VBlocks.signalNode.targetable, "");
        Tile core = player.core().tile;
        Tile spawn = spawner.getSpawns().first();
        h.check("deep map: ground path breach -> core", reachable(spawn, core), "from " + spawn.x + "," + spawn.y);
        h.check("deep map: three vents", DeepSignalMap.gateTiles.size == 3, "gates " + DeepSignalMap.gateTiles.size);
        for(int[] g : DeepSignalMap.gateTiles){
            Tile gate = world.tile(g[0], g[1]);
            h.check("deep map: vent " + g[0] + "," + g[1] + " open and connected to the core", gate != null && !gate.solid() && reachable(gate, core), "");
        }
        //what the reference layout needs at every relay: a dynamo west of it, two routers under both, all on open ground
        for(int[] n : DeepSignalMap.nodes){
            boolean room = Build.validPlace(VBlocks.resonanceDynamo, player, n[0] - 3 + 1, n[1] + 1, 0)
                && Build.validPlace(VBlocks.cargoRouter, player, n[0], n[1] - 1, 0) && Build.validPlace(VBlocks.cargoRouter, player, n[0] - 1, n[1] - 1, 0);
            h.check("deep map: room for a dynamo and routers at the relay " + n[0] + "," + n[1], room, "");
        }
        Seq<String> blocked = new Seq<>();
        for(int[] p : deepRoute()) if(!Build.validPlace(VBlocks.transitBelt, player, p[0], p[1], 0)) blocked.add(p[0] + "," + p[1]);
        h.check("deep map: the trunk and the three branches run on open ground", blocked.isEmpty(), blocked.size + " blocked: " + blocked);
        //the pulses land on the working relay machine nearest a vent: the north relay is that one, by a clear margin
        float[] nearest = new float[3];
        for(int i = 0; i < 3; i++){
            int[] n = DeepSignalMap.nodes[i];
            float best = Float.MAX_VALUE;
            for(int[] g : DeepSignalMap.gateTiles){
                //the relay, and the dynamo beside it
                best = Math.min(best, Math.min(Mathf.dst(n[0] + 1, n[1] + 1, g[0], g[1]), Mathf.dst(n[0] - 2, n[1] + 1, g[0], g[1])));
            }
            nearest[i] = best;
        }
        h.check("deep map: the north relay is the one nearest a vent (where the pulses land)", nearest[2] + 1f < Math.min(nearest[0], nearest[1]),
            "west " + Strings.fixed(nearest[0], 1) + ", east " + Strings.fixed(nearest[1], 1) + ", north " + Strings.fixed(nearest[2], 1));
        for(Block ore : new Block[]{VBlocks.oreFerroprism, VBlocks.oreVitreousCarbon, VBlocks.oreConductiveScrap}){
            int count = 0;
            for(Tile t : world.tiles){
                if(t.overlay() == ore && t.block() == Blocks.air && t.within(core, 22 * tilesize)) count++;
            }
            h.check("deep map: starting " + ore.name + " near the core", count >= 16, count + " tiles");
        }
        int crystal = 0;
        for(Tile t : world.tiles) if(t.overlay() == VBlocks.oreResonanceCrystal && t.block() == Blocks.air && t.x >= 76 && t.y <= 26) crystal++;
        h.check("deep map: crystal for three extractors in the fabrication hall", crystal >= 12, crystal + " tiles");
        int carbon = 0;
        for(int x = 102; x < 108; x++) for(int y = 18; y < 20; y++) if(world.tile(x, y).overlay() == VBlocks.oreVitreousCarbon && world.tile(x, y).block() == Blocks.air) carbon++;
        h.check("deep map: a carbon seam for three drills of their own in the fabrication hall", carbon == 12, carbon + " tiles");
        h.check("deep map: the underworks are dark (lighting on), a Chapter Two scenario with the response and the archaeology tag", state.rules.lighting
            && vesper.mission.DeepSignal.scenarioId.equals(state.rules.tags.get(vesper.mission.FirstAwakening.tagScenario))
            && state.rules.tags.getBool(vesper.world.AncientSite.tagEnabled) && state.rules.tags.getBool(vesper.systems.PlanetaryActivity.tagResponse), "");
    }

    static void checkWorks(VesperHarness h){
        Team player = ResonanceWorksMap.player;
        h.check("works map: every decoration and seam fits the terrain", ResonanceWorksMap.skipped.isEmpty(), "skipped: " + ResonanceWorksMap.skipped);
        h.check("works map: one player core, one ground spawn, one relay", player.data().cores.size == 1 && spawner.countSpawns() == 1
            && player.data().getBuildings(VBlocks.worksRelay).size == 1, "cores " + player.data().cores.size + ", spawns " + spawner.countSpawns());
        h.check("works map: the gate is on open ground", ResonanceWorksMap.gateTiles.size == 1, "gates " + ResonanceWorksMap.gateTiles.size);
        h.check("works map: the relay cannot be deconstructed and enemies ignore it", !Build.validBreak(player, ResonanceWorksMap.relayX + 1, ResonanceWorksMap.relayY + 1)
            && !VBlocks.worksRelay.targetable, "");
        h.check("works map: Chapter One's tags are absent (a standalone map)", !state.rules.tags.containsKey(vesper.world.AncientSite.tagEnabled)
            && state.rules.sector == null, "");
    }

    /**
     * What the builders left is not the player's to take apart: deconstructing it would refund half of a cost nobody
     * paid, and the reactor and the archaeology sites carry a mission. Checked for the player's team on every block of
     * an ancient kind, whoever owns it.
     */
    static void noAncientDeconstruction(VesperHarness h, String map){
        Team player = state.rules.defaultTeam;
        Seq<String> breakable = new Seq<>();
        for(Building b : Groups.build){
            if((b.block instanceof vesper.world.AncientStructure || b.block instanceof vesper.world.AncientSite || b.block instanceof vesper.world.DerelictReactor
                || (b.block instanceof vesper.world.Stabilizer && b.team == Team.derelict)) && Build.validBreak(player, b.tileX(), b.tileY())){
                breakable.add(b.block.name.replace("vesper-", "") + "@" + b.tileX() + "," + b.tileY() + "(" + b.team.name + ")");
            }
        }
        h.check(map + ": no ancient structure can be deconstructed by the player", breakable.isEmpty(), breakable.size + ": " + breakable);
    }

    static void checkFoundry(VesperHarness h){
        Team player = BuriedFoundryMap.player;
        h.check("foundry map: every decoration fits the terrain", BuriedFoundryMap.skipped.isEmpty(), "skipped: " + BuriedFoundryMap.skipped);
        h.check("foundry map: one player core", player.data().cores.size == 1, "cores " + player.data().cores.size);
        h.check("foundry map: one ground spawn", spawner.countSpawns() == 1, "spawns " + spawner.countSpawns());
        h.check("foundry map: two buried recoverers and the archive, the player's and unexplored",
            player.data().getBuildings(VBlocks.ancientRecoverer).size == 2 && player.data().getBuildings(VBlocks.archiveTerminal).size == 1
            && player.data().buildings.allMatch(b -> !(b instanceof vesper.world.AncientSite.SiteBuild site) || site.siteState == vesper.world.AncientSite.SiteState.unexplored), "");
        h.check("foundry map: only the core and the installations are the player's", player.data().buildings.size == 4, player.data().buildings.size + " player buildings");
        Tile core = player.core().tile;
        Tile spawn = spawner.getSpawns().first();
        h.check("foundry map: ground path landing zone -> core", reachable(spawn, core), "from " + spawn.x + "," + spawn.y);
        h.check("foundry map: two gates", BuriedFoundryMap.gateTiles.size == 2, "gates " + BuriedFoundryMap.gateTiles.size);
        for(int[] g : BuriedFoundryMap.gateTiles){
            Tile gate = world.tile(g[0], g[1]);
            h.check("foundry map: gate " + g[0] + "," + g[1] + " open and connected to the core", gate != null && !gate.solid() && reachable(gate, core), "");
        }
        //the waves must not cross the hall: with the trench closed, the landing zone cannot reach it
        Tile hall = world.tile(BuriedFoundryMap.furnaceX - 6, BuriedFoundryMap.furnaceY + 2);
        h.check("foundry map: the hall is off the wave path (only the trench leads there)", hall != null && !hall.solid() && reachable(hall, core)
            && !reachable(spawn, hall, t -> t.x >= 27 && t.x <= 45 && t.y >= 27 && t.y <= 44), "");
        for(Block ore : new Block[]{VBlocks.oreFerroprism, VBlocks.oreVitreousCarbon, VBlocks.oreConductiveScrap}){
            int count = 0;
            for(Tile t : world.tiles){
                if(t.overlay() == ore && t.block() == Blocks.air && t.within(core, 22 * tilesize)) count++;
            }
            h.check("foundry map: starting " + ore.name + " near the core", count >= 24, count + " tiles");
        }
        int crystal = 0;
        for(Tile t : world.tiles) if(t.overlay() == VBlocks.oreResonanceCrystal && t.block() == Blocks.air) crystal++;
        h.check("foundry map: resonance crystal to restore and feed the machines", crystal >= 30, crystal + " tiles");
    }

    static void checkFault(VesperHarness h){
        Team player = ResonanceFaultMap.player;
        h.check("fault map: every decoration fits the terrain", ResonanceFaultMap.skipped.isEmpty(), "skipped: " + ResonanceFaultMap.skipped);
        h.check("fault map: one player core", player.data().cores.size == 1, "cores " + player.data().cores.size);
        h.check("fault map: one ground spawn", spawner.countSpawns() == 1, "spawns " + spawner.countSpawns());
        var reactor = player.data().getBuildings(VBlocks.derelictReactor);
        h.check("fault map: the reactor starts primed", reactor.size == 1 && reactor.first() instanceof vesper.world.DerelictReactor.ReactorBuild r && r.primed() && !r.ignited, "");
        Tile core = player.core().tile;
        Tile spawn = spawner.getSpawns().first();
        h.check("fault map: ground path landing zone -> core", reachable(spawn, core), "from " + spawn.x + "," + spawn.y);
        h.check("fault map: two gates", ResonanceFaultMap.gateTiles.size == 2, "gates " + ResonanceFaultMap.gateTiles.size);
        //the waves must not cross the fault: with the valley closed, the landing zone cannot reach it at all
        Tile westGate = ResonanceFaultMap.gateTiles.isEmpty() ? null : world.tile(ResonanceFaultMap.gateTiles.first()[0], ResonanceFaultMap.gateTiles.first()[1]);
        h.check("fault map: the fault is off the wave path (only the valley leads there)", westGate != null && reachable(spawn, core)
            && !reachable(spawn, westGate, t -> t.y >= 32 && t.y <= 46 && t.x <= 38), "");
        for(int[] g : ResonanceFaultMap.gateTiles){
            Tile gate = world.tile(g[0], g[1]);
            h.check("fault map: gate " + g[0] + "," + g[1] + " open and connected to the core", gate != null && !gate.solid() && reachable(gate, core), "");
        }
        for(Block ore : new Block[]{VBlocks.oreFerroprism, VBlocks.oreVitreousCarbon, VBlocks.oreConductiveScrap}){
            int count = 0;
            for(Tile t : world.tiles){
                if(t.overlay() == ore && t.block() == Blocks.air && t.within(core, 22 * tilesize)) count++;
            }
            h.check("fault map: starting " + ore.name + " near the core", count >= 24, count + " tiles");
        }
        //crystal: a small vein the foothold can work safely, and most of it out along the fault
        int sheltered = 0, fault = 0;
        for(Tile t : world.tiles){
            if(t.overlay() != VBlocks.oreResonanceCrystal || t.block() != Blocks.air) continue;
            if(t.within(core, 22 * tilesize)) sheltered++;
            else if(t.y >= 50) fault++;
        }
        int shelteredTiles = sheltered, faultTiles = fault;
        h.check("fault map: sheltered crystal vein near the foothold", sheltered >= 8 && sheltered <= 24, sheltered + " tiles");
        h.check("fault map: rich crystal deposits along the fault", fault >= 80, fault + " tiles");
        h.line("info: fault map crystal tiles: sheltered " + shelteredTiles + ", along the fault " + faultTiles);
    }

    static void save(VesperHarness h, String name, StringMap tags){
        try{
            Fi dir = h.out.child("maps");
            Fi file = dir.child(name + ".msav");
            MapIO.writeMap(file, new Map(tags));
            Pixmap preview = MapIO.generatePreview(world.tiles);
            PixmapIO.writePng(dir.child(name + "-preview.png"), preview);
            preview.dispose();
            h.pass("map written: " + name, file.length() + " bytes, " + world.width() + "x" + world.height() + ", buildings " + Groups.build.size());
        }catch(Throwable t){
            h.fail("map written: " + name, t.toString());
        }
    }

    /** Tile map for planning: # rock, . ground, ores F C S X, = plating, B building, P spawn (top row = highest y). */
    static void dump(Fi file){
        StringBuilder sb = new StringBuilder();
        for(int y = world.height() - 1; y >= 0; y--){
            sb.append(String.format("%3d ", y));
            for(int x = 0; x < world.width(); x++){
                Tile t = world.tile(x, y);
                char c = t.block() instanceof mindustry.world.blocks.environment.StaticWall ? '#' : t.build != null ? 'B' : t.overlay() == Blocks.spawn ? 'P'
                    : t.overlay() == VBlocks.oreFerroprism ? 'F' : t.overlay() == VBlocks.oreVitreousCarbon ? 'C'
                    : t.overlay() == VBlocks.oreConductiveScrap ? 'S' : t.overlay() == VBlocks.oreResonanceCrystal ? 'X'
                    : t.floor() == VBlocks.rustedPlating ? '=' : '.';
                sb.append(c);
            }
            sb.append((char)10);
        }
        sb.append("    ");
        for(int x = 0; x < world.width(); x++) sb.append(x % 10 == 0 ? (char)('0' + (x / 10) % 10) : ' ');
        sb.append((char)10);
        file.writeString(sb.toString());
    }

    static void checkBasin(VesperHarness h){
        Team player = SilentBasinMap.player;
        h.check("basin: every decoration fits the terrain", SilentBasinMap.skipped.isEmpty(), "skipped: " + SilentBasinMap.skipped);
        h.check("basin: one player core", player.data().cores.size == 1, "cores " + player.data().cores.size);
        h.check("basin: one ground spawn", spawner.countSpawns() == 1, "spawns " + spawner.countSpawns());
        h.check("basin: reactor present", player.data().getBuildings(VBlocks.derelictReactor).size == 1, "");
        int playerBuildings = player.data().buildings.size;
        h.check("basin: only core and reactor belong to the player", playerBuildings == 2, playerBuildings + " player buildings");

        Tile core = player.core().tile;
        Tile spawn = spawner.getSpawns().first();
        h.check("basin: ground path landing zone -> core", reachable(spawn, core), "from " + spawn.x + "," + spawn.y);
        h.check("basin: two gates", SilentBasinMap.gateTiles.size == 2, "gates " + SilentBasinMap.gateTiles.size);
        for(int[] g : SilentBasinMap.gateTiles){
            Tile gate = world.tile(g[0], g[1]);
            h.check("basin: gate " + g[0] + "," + g[1] + " open and connected to the core", gate != null && !gate.solid() && reachable(gate, core), "");
        }

        //starting resources: every basic ore within 22 tiles of the core, enough for several drills
        for(Block ore : new Block[]{VBlocks.oreFerroprism, VBlocks.oreVitreousCarbon, VBlocks.oreConductiveScrap}){
            int count = 0;
            for(Tile t : world.tiles){
                if(t.overlay() == ore && t.block() == Blocks.air && t.within(core, 22 * tilesize)) count++;
            }
            h.check("basin: starting " + ore.name + " near the core", count >= 24, count + " tiles");
        }
    }

    /** Breadth-first search over non-solid tiles (rock and ruins block), ending next to the target building or on the target tile. */
    static boolean reachable(Tile from, Tile target){
        return reachable(from, target, t -> false);
    }

    static boolean reachable(Tile from, Tile target, arc.func.Boolf<Tile> closed){
        boolean[] seen = new boolean[world.width() * world.height()];
        IntQueue queue = new IntQueue();
        queue.addLast(from.pos());
        seen[from.array()] = true;
        while(!queue.isEmpty()){
            Tile t = world.tile(queue.removeFirst());
            for(Point2 d : Geometry.d4){
                Tile n = world.tile(t.x + d.x, t.y + d.y);
                if(n == null || seen[n.array()]) continue;
                seen[n.array()] = true;
                if(n == target || (n.build != null && n.build == target.build)) return true;
                if(!n.solid() && !closed.get(n)) queue.addLast(n.pos());
            }
        }
        return false;
    }
}
