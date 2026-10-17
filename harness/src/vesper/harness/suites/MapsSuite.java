package vesper.harness.suites;

import arc.files.*;
import arc.graphics.*;
import arc.math.geom.*;
import arc.struct.*;
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
 * Generates both maps (written to vesper-harness/maps/, imported by ./gradlew importMaps), with a one pixel per
 * tile preview, and checks the structure of The Silent Basin: one core, one spawn, the reactor, reachable gates,
 * ground paths to the core and enough starting ore around it.
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
                char c = t.block() == VBlocks.machineBasaltWall ? '#' : t.build != null ? 'B' : t.overlay() == Blocks.spawn ? 'P'
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

    /** Breadth-first search over non-solid tiles (rock and ruins block), ending next to the target building. */
    static boolean reachable(Tile from, Tile target){
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
                if(n.build != null && n.build == target.build) return true;
                if(!n.solid()) queue.addLast(n.pos());
            }
        }
        return false;
    }
}
