package vesper.world;

import arc.math.*;
import mindustry.world.blocks.production.*;
import vesper.*;
import vesper.content.*;

/**
 * A drill that feeds on the basin's resonance: it mines faster as planetary activity rises and inside a resonance
 * pulse (see {@link vesper.systems.PlanetaryActivity#resonance}). Outside Vesper scenarios it is a plain drill.
 */
public class ResonantDrill extends Drill{
    public ResonantDrill(String name){
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
        addBar("resonance", (ResonantDrillBuild b) -> VStats.resonanceBar(b::resonance));
    }

    public class ResonantDrillBuild extends DrillBuild{
        public float resonance(){
            return Vesper.activity.resonance(this);
        }

        @Override
        public float efficiencyScale(){
            //a drill applies its efficiency twice (to the speed and to the warmup that multiplies it)
            return Mathf.sqrt(resonance());
        }
    }
}
