package vesper.harness.suites;

import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.maps.Map;
import mindustry.world.*;

import static mindustry.Vars.*;

/** Starting worlds the way the game does, without the menus. */
public class Worlds{
    /** The Vesper map with this name, as listed in Play > Custom Game. */
    public static Map bundled(String name){
        Map map = maps.all().find(m -> m.mod != null && m.mod.name.equals("vesper") && m.name().equals(name));
        if(map == null) throw new IllegalStateException("bundled map not found: " + name);
        return map;
    }

    /** Same steps as Control.playMap: load, apply the map's rules for the mode, start. */
    public static void play(Map map, Gamemode mode){
        logic.reset();
        Rules rules = map.applyRules(mode);
        world.loadMap(map, rules);
        state.rules = rules;
        state.rules.sector = null;
        state.rules.editor = false;
        logic.play();
        if(ui.restart != null && ui.restart.isShown()) ui.restart.hide();
    }

    public static void generate(int width, int height, arc.func.Cons<Tiles> generator, Rules rules){
        logic.reset();
        world.loadGenerator(width, height, generator);
        state.rules = rules;
        logic.play();
    }

    public static @Nullable Building build(int x, int y){
        return world.build(x, y);
    }

    /**
     * Tiles whose floor, overlay or block does not come from Vesper (the spawn marker excepted). Catches the game's
     * default generation filters, which scatter vanilla ores over a map whose tags do not set "genfilters".
     */
    public static int foreignTiles(){
        int count = 0;
        for(Tile t : world.tiles){
            if(foreign(t.floor()) || (t.overlay() != Blocks.air && t.overlay() != Blocks.spawn && foreign(t.overlay())) || (t.block() != Blocks.air && foreign(t.block()))) count++;
        }
        return count;
    }

    static boolean foreign(Block block){
        return block.minfo.mod == null || !block.minfo.mod.name.equals("vesper");
    }

    /** Centre tile of a footprint given by its bottom-left tile. */
    public static @Nullable Building at(int left, int bottom, int size){
        int off = (size - 1) / 2;
        return world.build(left + off, bottom + off);
    }
}
