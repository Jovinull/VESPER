package vesper.harness;

import arc.math.geom.*;
import arc.struct.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

import static mindustry.Vars.*;

/**
 * The Vesper gallery: a sandbox map with two working production chains, a defensive line and a showroom of every
 * block. Everything belongs to the player; the map's rules enable cheat for the player team so idle showroom
 * machines still animate and glow (a separate team would draw foreign-team markers and be shot by turrets).
 * Coordinates are tiles, y-up; {@link #place} takes the bottom-left tile of the footprint.
 */
public class Gallery{
    public static final int width = 78, height = 48;
    public static final Team player = Team.sharded;

    /** Power links to make once the world is running: pairs of bottom-left tiles. */
    public static final Seq<int[]> links = new Seq<>();

    static Block b(String name){
        Block block = content.block("vesper-" + name);
        if(block == null) throw new IllegalStateException("Missing Vesper content: " + name);
        return block;
    }

    public static void generate(Tiles tiles){
        links.clear();
        Floor basalt = (Floor)b("machine-basalt"), soil = (Floor)b("scrap-soil"), dust = (Floor)b("vitrified-dust"), plating = (Floor)b("rusted-plating");

        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float n = Simplex.noise2d(7, 2, 0.5, 1 / 9.0, x, y) * 5f - 2.5f;
                Floor floor = basalt;
                if(x + n < 15 && y + n < 13) floor = soil;
                if(x + n > 36 && y + n < 19 && x < 44 + n) floor = dust;
                if(x >= 44 && y >= 21) floor = plating;
                if(x >= 44 && y < 21 && y + n < 19) floor = dust;
                tiles.set(x, y, new Tile(x, y, floor, Blocks.air, Blocks.air));
            }
        }

        // --- chain A (player): ferroprism + vitreous carbon -> belts -> structural forge -> core
        ore(tiles, "ore-ferroprism", 2, 31, 8, 3);
        ore(tiles, "ore-vitreous-carbon", 2, 25, 8, 3);
        for(int x = 3; x <= 7; x += 2){
            place(tiles, "survey-drill", x, 31, 0, player);
            place(tiles, "survey-drill", x, 26, 0, player);
        }
        belt(tiles, 3, 30, 10, 30, 0, player);
        belt(tiles, 3, 28, 10, 28, 0, player);
        place(tiles, "transit-belt", 11, 28, 1, player);
        place(tiles, "structural-forge", 11, 29, 0, player);
        place(tiles, "transit-belt", 13, 29, 0, player);
        place(tiles, "core-foothold", 14, 28, 0, player);

        // scrap field with a collector (decorative, unpowered recycler beside it)
        ore(tiles, "ore-conductive-scrap", 2, 4, 7, 6);
        place(tiles, "scrap-collector", 3, 5, 0, player);
        place(tiles, "scrap-collector", 6, 7, 0, player);

        // --- chain B (player): carbon drills feed engines -> resonance links -> refinery -> alloy -> kinetic turret
        ore(tiles, "ore-vitreous-carbon", 18, 4, 9, 7);
        for(int y : new int[]{5, 8}){
            place(tiles, "survey-drill", 19, y, 0, player);
            place(tiles, "combustion-engine", 21, y, 0, player);
            place(tiles, "survey-drill", 23, y, 0, player);
        }
        place(tiles, "power-accumulator", 21, 11, 0, player);
        place(tiles, "resonance-link", 25, 12, 0, player);
        place(tiles, "resonance-link", 33, 13, 0, player);

        ore(tiles, "ore-ferroprism", 27, 3, 2, 6);
        ore(tiles, "ore-vitreous-carbon", 32, 3, 2, 6);
        place(tiles, "survey-drill", 27, 4, 0, player);
        place(tiles, "survey-drill", 27, 6, 0, player);
        place(tiles, "alloy-refinery", 29, 4, 0, player);
        place(tiles, "survey-drill", 32, 4, 0, player);
        place(tiles, "survey-drill", 32, 6, 0, player);
        belt(tiles, 30, 7, 30, 9, 1, player);
        place(tiles, "kinetic-turret", 30, 10, 0, player);
        place(tiles, "pulse-turret", 34, 10, 0, player);
        place(tiles, "ancient-platform", 37, 9, 0, player);

        link(25, 12, 21, 5);
        link(25, 12, 21, 8);
        link(25, 12, 21, 11);
        link(25, 12, 33, 13);
        link(33, 13, 29, 4);
        link(33, 13, 34, 10);
        link(33, 13, 37, 9);

        // defensive line
        for(int y = 2; y <= 16; y += 2) place(tiles, "heavy-wall", 42, y, 0, player);
        for(int y = 2; y <= 17; y++) place(tiles, "reinforced-wall", 41, y, 0, player);

        // --- showroom on the old rusted deck
        place(tiles, "derelict-reactor", 46, 41, 0, player);
        place(tiles, "stabilizer-pylon", 52, 43, 0, player);
        place(tiles, "stabilizer-pylon", 52, 40, 0, player);
        place(tiles, "data-terminal", 56, 42, 0, player);
        place(tiles, "recoverable-ruin", 60, 41, 0, player);
        ore(tiles, "ore-resonance-crystal", 65, 40, 5, 5);
        place(tiles, "crystal-extractor", 66, 42, 0, player);
        place(tiles, "experimental-resonator", 70, 41, 0, player);

        place(tiles, "structural-forge", 46, 35, 0, player);
        place(tiles, "scrap-recycler", 49, 35, 0, player);
        place(tiles, "alloy-refinery", 52, 35, 0, player);
        place(tiles, "mineral-compressor", 56, 35, 0, player);
        place(tiles, "combustion-engine", 59, 35, 0, player);
        place(tiles, "power-accumulator", 62, 35, 0, player);
        place(tiles, "component-assembler", 65, 35, 0, player);
        place(tiles, "resonance-link", 69, 36, 0, player);

        ore(tiles, "ore-ferroprism", 46, 29, 2, 2);
        ore(tiles, "ore-vitreous-carbon", 49, 28, 3, 3);
        ore(tiles, "ore-conductive-scrap", 53, 29, 2, 2);
        ore(tiles, "ore-resonance-crystal", 56, 29, 2, 2);
        place(tiles, "survey-drill", 46, 29, 0, player);
        place(tiles, "reinforced-drill", 49, 28, 0, player);
        place(tiles, "scrap-collector", 53, 29, 0, player);
        place(tiles, "crystal-extractor", 56, 29, 0, player);
        belt(tiles, 59, 29, 61, 29, 0, player);
        place(tiles, "cargo-junction", 62, 29, 0, player);
        place(tiles, "transit-belt", 63, 29, 0, player);
        place(tiles, "cargo-router", 64, 29, 0, player);
        place(tiles, "transit-belt", 62, 30, 3, player);
        place(tiles, "transit-belt", 62, 28, 3, player);
        place(tiles, "cargo-bridge", 66, 29, 0, player);
        place(tiles, "cargo-bridge", 70, 29, 0, player);
        link(66, 29, 70, 29);
        belt(tiles, 59, 31, 66, 31, 0, player);
        for(int x = 68; x <= 73; x++) place(tiles, "industrial-conduit", x, 31, 0, player);

        for(int x = 46; x <= 48; x++) place(tiles, "reinforced-wall", x, 24, 0, player);
        place(tiles, "heavy-wall", 49, 23, 0, player);
        place(tiles, "kinetic-turret", 52, 23, 0, player);
        place(tiles, "pulse-turret", 55, 23, 0, player);
        place(tiles, "ancient-platform", 58, 22, 0, player);
        place(tiles, "core-foothold", 63, 22, 0, player);
    }

    static void ore(Tiles tiles, String name, int x0, int y0, int w, int h){
        Block ore = b(name);
        for(int x = x0; x < x0 + w; x++){
            for(int y = y0; y < y0 + h; y++){
                Tile t = tiles.get(x, y);
                if(t != null) t.setOverlay(ore);
            }
        }
    }

    static void belt(Tiles tiles, int x0, int y0, int x1, int y1, int rotation, Team team){
        for(int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++){
            for(int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++){
                place(tiles, "transit-belt", x, y, rotation, team);
            }
        }
    }

    /** Places a block by the bottom-left tile of its footprint. */
    public static Tile place(Tiles tiles, String name, int left, int bottom, int rotation, Team team){
        Block block = b(name);
        int off = (block.size - 1) / 2;
        Tile tile = tiles.get(left + off, bottom + off);
        tile.setBlock(block, team, rotation);
        return tile;
    }

    static void link(int x0, int y0, int x1, int y1){
        links.add(new int[]{x0, y0, x1, y1});
    }

    /** Connects the power links recorded during generation; call once the world is live. */
    public static void connectLinks(){
        for(int[] l : links){
            Building from = world.build(l[0], l[1]);
            Block target = world.tile(l[2], l[3]).block();
            int off = (target.size - 1) / 2;
            Building to = world.build(l[2] + off, l[3] + off);
            if(from != null && to != null){
                from.configureAny(to.pos());
            }
        }
    }

    /** Centre of the footprint whose bottom-left tile is (x, y), in world units. */
    public static Vec2 worldAt(float x, float y){
        return new Vec2(x * tilesize, y * tilesize);
    }
}
