package vesper;

import arc.util.*;
import mindustry.mod.*;
import vesper.content.*;
import vesper.mission.*;
import vesper.systems.*;
import vesper.ui.*;

import static mindustry.Vars.*;

public class Vesper extends Mod{
    /** World systems. They live for the whole session; their state belongs to the current world only. */
    public static PlanetaryActivity activity;
    public static FirstAwakening mission;
    public static ResonanceFault fault;

    @Override
    public void init(){
        activity = new PlanetaryActivity();
        mission = new FirstAwakening();
        fault = new ResonanceFault();
        if(!headless) new BasinHud();
    }

    /** The mission of the current map, if it has one. */
    public static @Nullable Scenario scenario(){
        return mission.active() ? mission : fault.active() ? fault : null;
    }

    @Override
    public void loadContent(){
        VItems.load();
        VUnits.load();
        VBlocks.load();
    }
}
