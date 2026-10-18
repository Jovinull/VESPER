package vesper.content;

import arc.graphics.*;
import mindustry.content.*;
import mindustry.graphics.g3d.*;
import mindustry.type.*;
import vesper.campaign.*;
import vesper.graphics.*;

public class VPlanets{
    public static Planet vesper;

    public static void load(){
        //orbits the vanilla sun; only the chapter's preset sectors can be landed on
        vesper = new Planet("planet", Planets.sun, 1f, 2){{
            generator = new VesperGenerator();
            meshLoader = () -> new HexMesh(this, 6);
            alwaysUnlocked = true;
            //the tree would restrict its content to this planet, and Vesper's custom maps (planet Serpulo, the rules'
            //default) could no longer build it; VBlocks.vesperOnly keeps the blocks out of the other campaigns instead
            autoAssignPlanet = false;
            startSector = VSectors.firstAwakeningId;
            allowLaunchToNumbered = false;
            //each map is authored whole: its own core and loadout, its buildings untouched on landing, its own lighting
            prebuildBase = false;
            updateLighting = false;
            clearSectorOnLose = true;
            defaultCore = VBlocks.coreFoothold;
            iconColor = VPal.resonance;
            //a thin haze of dust over bare metal; the resonance shows in the seams, not in the sky
            atmosphereColor = Color.valueOf("232930");
            atmosphereRadIn = 0f;
            atmosphereRadOut = 0.1f;
            landCloudColor = VPal.steel.cpy().a(0.5f);
            orbitSpacing = 2.5f;
        }};
    }
}
