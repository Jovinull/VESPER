package vesper.world;

import mindustry.world.blocks.production.*;
import vesper.*;
import vesper.content.*;

/**
 * A crafter that feeds on the basin's resonance: it works faster as planetary activity rises and inside a resonance
 * pulse (see {@link vesper.systems.PlanetaryActivity#resonance}). Outside Vesper scenarios it is a plain crafter.
 */
public class ResonantCrafter extends GenericCrafter{
    public ResonantCrafter(String name){
        super(name);
    }

    @Override
    public void setStats(){
        super.setStats();
        VStats.addResonance(stats);
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("resonance", (ResonantCrafterBuild b) -> VStats.resonanceBar(b::resonance));
    }

    public class ResonantCrafterBuild extends GenericCrafterBuild{
        public float resonance(){
            return Vesper.activity.resonance(this);
        }

        @Override
        public float efficiencyScale(){
            return resonance();
        }
    }
}
