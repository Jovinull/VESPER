package vesper.world;

import arc.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.ui.*;
import mindustry.world.blocks.power.*;
import mindustry.world.meta.*;
import vesper.*;
import vesper.content.*;
import vesper.graphics.*;

/**
 * Burns resonance cores for power. Each core holds a fixed amount of energy ({@link #powerProduction} x
 * {@link #itemDuration}); the basin's resonance raises how fast that energy can be drawn (more output per tick, the
 * same energy per core), and a pulse raises it further. It follows the load: it produces, and burns cores, only for
 * what its power grid actually takes (consumers, or batteries with room), so a core is never spent on power nobody
 * uses. Working, it is heavy industry for the planetary activity.
 */
public class ResonanceDynamo extends ConsumeGenerator{
    /** How fast the output follows the load (share of the gap closed per tick; below 1/n so n dynamos on a grid settle). */
    public float throttleSpeed = 0.1f;

    public ResonanceDynamo(String name){
        super(name);
    }

    @Override
    public void setStats(){
        super.setStats();
        VStats.addResonance(stats);
        stats.add(VStats.coreEnergy, Core.bundle.format("vesper.stat.core-energy", (int)(powerProduction * itemDuration)));
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("resonance", (ResonanceDynamoBuild b) -> VStats.resonanceBar(b::resonance));
        addBar("load", (ResonanceDynamoBuild b) -> new Bar(
            () -> Core.bundle.format("vesper.dynamo.load", (int)(b.throttle * 100f), Strings.autoFixed(b.coresPerMinute(), 1)),
            () -> VPal.industrial, () -> b.throttle));
    }

    public class ResonanceDynamoBuild extends ConsumeGeneratorBuild{
        /** Share of its current maximum output the grid takes (0-1), smoothed. */
        public float throttle;
        /** Cores it has burned since it was built (saved). */
        public int coresBurned;

        public float resonance(){
            return Vesper.activity.resonance(this);
        }

        /** Cores burned per minute at the current output. */
        public float coresPerMinute(){
            return efficiency > 0f ? 3600f / itemDuration * resonance() * throttle : 0f;
        }

        /**
         * The share of its maximum output the grid takes right now, in power per tick: what its consumers ask for and
         * its batteries still have room for, less what the other generators supply. Worked out as rates from the grid's
         * members, so the order in which the grid and the generators update, and the length of the tick, do not matter.
         */
        float demand(float max){
            var graph = power.graph;
            float tick = Math.max(Time.delta, 0.0001f);
            float others = 0f;
            for(var p : graph.producers) if(p != this) others += p.getPowerProduction() * p.timeScale();
            float room = Math.max(graph.getLastCapacity() - graph.getLastPowerStored(), 0f) / tick;
            return Mathf.clamp((graph.getPowerNeeded() / tick + room - others) / max);
        }

        @Override
        public void updateTile(){
            boolean valid = efficiency > 0f;
            float multiplier = resonance();
            float target = valid ? demand(powerProduction * multiplier) : 0f;
            throttle = Math.abs(target - throttle) < 0.002f ? target : Mathf.lerpDelta(throttle, target, throttleSpeed);
            warmup = Mathf.lerpDelta(warmup, valid && throttle > 0.01f ? 1f : 0f, warmupSpeed);
            productionEfficiency = valid ? multiplier * throttle : 0f;
            totalTime += warmup * multiplier * Time.delta;

            if(valid && throttle > 0.001f){
                //a new core when the last one is spent (what was drawn past it comes out of the new one, so every core gives
                //exactly its energy); its energy is drawn as fast as the resonance and the load allow
                if(generateTime <= 0f){
                    consume();
                    coresBurned++;
                    consumeEffect.at(x, y);
                    generateTime += 1f;
                }
                generateTime -= delta() * multiplier * throttle / itemDuration;
                if(Mathf.chanceDelta(effectChance * throttle)) generateEffect.at(x + Mathf.range(generateEffectRange), y + Mathf.range(generateEffectRange));
            }
        }

        //version 1 is the generator's own (the fuel left in the core being burned)
        @Override
        public byte version(){
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(throttle);
            write.i(coresBurned);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                throttle = read.f();
                coresBurned = read.i();
            }
        }
    }
}
