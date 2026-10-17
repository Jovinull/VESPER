package vesper.harness.maps;

import arc.math.*;
import arc.struct.*;
import arc.util.noise.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.world.*;
import mindustry.world.blocks.environment.*;

/**
 * Drawing helpers for the generated Silent Basin maps. Shapes raise an "open" field; {@link #carve} turns every
 * tile whose field (plus noise) is positive into walkable ground and the rest into the given wall. Coordinates are
 * tiles, y-up; blocks are placed by the bottom-left tile of their footprint.
 */
public class Terrain{
    public final Tiles tiles;
    public final int width, height, seed;
    /** Decorations that did not fit (reported by the maps suite; a layout must leave none). */
    public final Seq<String> skipped;
    final float[] open;

    public Terrain(Tiles tiles, int width, int height, int seed, Seq<String> skipped){
        this.tiles = tiles;
        this.width = width;
        this.height = height;
        this.seed = seed;
        this.skipped = skipped;
        open = new float[width * height];
        java.util.Arrays.fill(open, -99f);
    }

    /** Fills the map: open ground of the given floor where the field is positive, the wall elsewhere and on a 3-tile border. */
    public void carve(Floor floor, Block wall){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float n = Simplex.noise2d(seed, 3, 0.5, 1 / 7.0, x, y) * 3f - 1.5f;
                float v = open[x + y * width] + n;
                boolean border = x < 3 || y < 3 || x >= width - 3 || y >= height - 3;
                Block block = v > 0f && !border ? Blocks.air : wall;
                tiles.set(x, y, new Tile(x, y, floor, Blocks.air, block));
            }
        }
    }

    public void ellipse(float cx, float cy, float rx, float ry){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.sqrt(Mathf.sqr((x - cx) / rx) + Mathf.sqr((y - cy) / ry));
                raise(x, y, (1f - d) * Math.min(rx, ry));
            }
        }
    }

    public void capsule(float r, float... pts){
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
    public void rock(float cx, float cy, float r){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.dst(x, y, cx, cy) - r;
                int i = x + y * width;
                if(d < open[i]) open[i] = d;
            }
        }
    }

    void raise(int x, int y, float v){
        int i = x + y * width;
        if(v > open[i]) open[i] = v;
    }

    public boolean walkable(int x, int y){
        Tile t = tiles.get(x, y);
        return t != null && t.block() == Blocks.air;
    }

    public void floorBlob(Floor floor, float cx, float cy, float rx, float ry, float roughness){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.sqrt(Mathf.sqr((x - cx) / rx) + Mathf.sqr((y - cy) / ry));
                float n = Simplex.noise2d(seed + floor.id, 3, 0.5, 1 / 5.0, x, y) - 0.5f;
                if(d + n * roughness * 2f < 1f) tiles.getn(x, y).setFloor(floor);
            }
        }
    }

    /** A broken plated road: a strip along the polyline with gaps where the plates are gone. */
    public void road(Floor floor, float halfWidth, float gaps, float... pts){
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

    public void ore(Block ore, float cx, float cy, float rx, float ry){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                float d = Mathf.sqrt(Mathf.sqr((x - cx) / rx) + Mathf.sqr((y - cy) / ry));
                float n = Simplex.noise2d(seed + ore.id, 2, 0.5, 1 / 3.0, x, y) - 0.5f;
                if(d + n * 0.5f < 1f && walkable(x, y)) tiles.getn(x, y).setOverlay(ore);
            }
        }
    }

    /** Places a decoration at the nearest footprint of open ground (within 6 tiles) around the given one. */
    public void placeNear(Block block, int left, int bottom, Team team){
        for(int r = 0; r <= 6; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    if(Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if(fits(block, left + dx, bottom + dy, 1)){
                        place(block, left + dx, bottom + dy, team);
                        return;
                    }
                }
            }
        }
        skipped.add(block.name + " near " + left + "," + bottom);
    }

    /** Whether the footprint and a margin around it are open ground (decorations never seal a passage). */
    public boolean fits(Block block, int left, int bottom, int margin){
        for(int x = left - margin; x < left + block.size + margin; x++){
            for(int y = bottom - margin; y < bottom + block.size + margin; y++){
                if(!walkable(x, y)) return false;
            }
        }
        return true;
    }

    /** Places a block by the bottom-left tile of its footprint; the footprint must be walkable ground. */
    public void place(Block block, int left, int bottom, Team team){
        for(int x = left; x < left + block.size; x++){
            for(int y = bottom; y < bottom + block.size; y++){
                if(!walkable(x, y)){
                    skipped.add(block.name + " at " + left + "," + bottom);
                    return;
                }
            }
        }
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, 0);
    }

    /** The open tile nearest the target with room around it (a 3x3 of ground), or null within 8 tiles. */
    public int[] openNear(int tx, int ty){
        for(int r = 0; r <= 8; r++){
            for(int dx = -r; dx <= r; dx++){
                for(int dy = -r; dy <= r; dy++){
                    int x = tx + dx, y = ty + dy;
                    boolean room = true;
                    for(int ox = -1; ox <= 1; ox++) for(int oy = -1; oy <= 1; oy++) room &= walkable(x + ox, y + oy);
                    if(room) return new int[]{x, y};
                }
            }
        }
        return null;
    }
}
