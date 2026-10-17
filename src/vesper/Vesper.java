package vesper;

import mindustry.mod.*;
import vesper.content.*;
import vesper.systems.*;

public class Vesper extends Mod{
    /** World systems. They live for the whole session; their state belongs to the current world only. */
    public static PlanetaryActivity activity;

    @Override
    public void init(){
        activity = new PlanetaryActivity();
    }

    @Override
    public void loadContent(){
        VItems.load();
        VUnits.load();
        VBlocks.load();
    }
}
