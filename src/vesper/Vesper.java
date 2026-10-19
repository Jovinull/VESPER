package vesper;

import arc.util.*;
import mindustry.ctype.*;
import mindustry.mod.*;
import vesper.campaign.*;
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
    public static BuriedFoundry foundry;
    public static ResonanceWorks works;
    /** Chapter One of the campaign on the VESPER planet, and its dialogs (client only). */
    public static Chapter chapter;
    public static @Nullable ChapterUi chapterUi;

    @Override
    public void init(){
        activity = new PlanetaryActivity();
        mission = new FirstAwakening();
        fault = new ResonanceFault();
        foundry = new BuriedFoundry();
        works = new ResonanceWorks();
        chapter = new Chapter();
        if(!headless){
            chapterUi = new ChapterUi(new BasinHud());
        }
    }

    /** The mission of the current map, if it has one. */
    public static @Nullable Scenario scenario(){
        return mission.active() ? mission : fault.active() ? fault : foundry.active() ? foundry : works.active() ? works : null;
    }

    @Override
    public void loadContent(){
        VItems.load();
        VUnits.load();
        VBlocks.load();
        VPlanets.load();
        VSectors.load();
        VTechTree.load();

        //the Core Database lists everything Vesper adds under the planet's own tab
        content.each(c -> {
            if(c instanceof UnlockableContent u && c.minfo.mod != null && c.minfo.mod.name.equals("vesper")) u.databaseTabs.add(VPlanets.vesper);
        });
    }
}
