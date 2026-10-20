package vesper.content;

import mindustry.maps.generators.*;
import mindustry.mod.Mods.*;
import mindustry.type.*;
import vesper.campaign.*;

import static mindustry.Vars.*;

public class VSectors{
    /** Fixed sector ids on the planet grid; sector saves and sector info are keyed by them, so they never change. */
    public static final int firstAwakeningId = 12, resonanceFaultId = 77, buriedFoundryId = 27, deepSignalId = 42;

    public static SectorPreset firstAwakening, resonanceFault, buriedFoundry;
    /** The region below the Basin, the first sector of Chapter Two: available once Chapter One is complete. */
    public static SectorPreset deepSignal;

    public static void load(){
        //the shipped maps, unchanged: they keep their rules, loadout and win condition (captureWave -1: the scenario
        //arms its own win wave or declares the victory; 0 would read as an enemy base on the planet map)
        firstAwakening = new SectorPreset("first-awakening", VPlanets.vesper, firstAwakeningId){{
            alwaysUnlocked = true;
            captureWave = -1;
            difficulty = 3f;
            rules = r -> {};
        }};

        resonanceFault = new SectorPreset("resonance-fault", VPlanets.vesper, resonanceFaultId){{
            captureWave = -1;
            difficulty = 5f;
            rules = r -> {};
        }};

        buriedFoundry = new SectorPreset("buried-foundry", VPlanets.vesper, buriedFoundryId){{
            captureWave = -1;
            difficulty = 6f;
            rules = r -> {};
        }};

        //Chapter Two's first sector, on the id the locked marker always had. Its map ships with the mod (the harness
        //generates it): a development build that has not generated it yet lands on the gallery instead of failing to load
        boolean shipped = tree != null && tree.get("maps/deep-signal." + mapExtension).exists();
        deepSignal = new SectorPreset("deep-signal", (LoadedMod)null){{
            generator = new FileMapGenerator(maps == null ? null : maps.loadInternalMap(shipped ? "deep-signal" : "vesper-gallery"), this);
            captureWave = -1;
            difficulty = 8f;
            rules = r -> {};
        }};
        deepSignal.initialize(VPlanets.vesper, deepSignalId);

        Chapter.sectors.addAll(firstAwakening, resonanceFault, buriedFoundry);
        Chapter.deeper.add(deepSignal);
        Chapter.sectors.each(Chapter::freeLaunch);
        Chapter.deeper.each(Chapter::freeLaunch);
    }
}
