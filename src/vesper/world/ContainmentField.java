package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import vesper.*;
import vesper.content.*;
import vesper.graphics.*;
import vesper.systems.*;

import static mindustry.Vars.*;

/**
 * A containment field emitter: switched on, powered and fed resonance cores, it keeps the resonance pulses from
 * interfering with the industry within its reach (no slowdown, no overloaded stabilizers). It does not lower the
 * planetary activity: it protects, it does not stabilize. It runs only while a pulse is announced or running (see
 * {@link PlanetaryActivity#pulsing}); standing by it costs nothing. Running costs one core every {@link #upkeepTime} ticks;
 * absorbing a pulse that lands within reach costs {@link #absorbCost} more from its stock, paid when the pulse
 * lands. A field that cannot pay (off, unpowered or short of cores) is overloaded and protects nothing until the
 * pulse ends; one that stops mid-pulse (switched off, unpowered, starved, destroyed) leaves the machines it spared
 * to the pulse for the time left. Overlapping fields each pay for themselves. The server decides every payment;
 * clients see the result through the block's synced state.
 */
public class ContainmentField extends Block{
    /** Reach of the field from the emitter's centre (world units). */
    public float radius = 9f * tilesize;
    /** Ticks of operation one resonance core pays for, and the cores absorbing one pulse costs. */
    public float upkeepTime = 60f * 60f;
    public int absorbCost = 2;
    /** Share of its power it needs to hold anything back (an underpowered grid cannot run many fields on little power). */
    public float minPower = 0.9f;
    public Item fuel = VItems.resonanceCore;
    public Color fieldColor = VPal.resonanceLight;
    public TextureRegion glowRegion, lampRegion, fieldRegion;

    public ContainmentField(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        saveConfig = true;
        sync = true;
        hasItems = true;
        hasPower = true;
        itemCapacity = 10;
        emitLight = true;
        envEnabled = Env.any;

        config(Boolean.class, (FieldBuild build, Boolean online) -> build.online = online);
    }

    @Override
    public void init(){
        super.init();
        PlanetaryActivity.shieldReach = Math.max(PlanetaryActivity.shieldReach, radius);
    }

    @Override
    public void load(){
        super.load();
        glowRegion = Core.atlas.find(name + "-glow");
        lampRegion = Core.atlas.find("vesper-activity-indicator");
        fieldRegion = Core.atlas.find("vesper-energy-pulse");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.range, radius / tilesize, StatUnit.blocks);
        stats.add(VStats.containment, Core.bundle.format("vesper.stat.containment", Strings.autoFixed(upkeepTime / 60f, 1), absorbCost));
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("containment", (FieldBuild b) -> new Bar(
            () -> Core.bundle.get("vesper.field.state." + b.fieldStatus()),
            () -> b.overloaded() ? VPal.dangerLight : b.shielding() ? VPal.resonanceLight : b.ready() ? VPal.resonance : Color.gray,
            () -> b.ready() ? 1f : 0f));
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, radius, VPal.resonance);
    }

    public class FieldBuild extends Building implements PulseShield{
        /** Switched on by the player (saved, and kept when the block is rebuilt or copied). */
        public boolean online = true;
        /** Ticks of operation already paid for (a core is paid when its time starts). */
        public float upkeep;
        /** The pulse it absorbed and the pulse that overloaded it (numbered as {@link PlanetaryActivity#pulses}); -1 for none. */
        public int absorbedPulse = -1, overloadedPulse = -1;
        /** Cores it spent on upkeep and on pulses, since it was built (saved). */
        public int coresSpent;
        /** Smoothed operation and pulse reaction, for the visuals. */
        public float warmup, strain;
        boolean held;

        /** On, powered and paid for: it can absorb a pulse. */
        public boolean ready(){
            return online && needed() && efficiency >= minPower;
        }

        public boolean overloaded(){
            return overloadedPulse >= 0 && overloadedPulse == Vesper.activity.pulses && Vesper.activity.pulseActive > 0f;
        }

        public String fieldStatus(){
            return !online ? "off" : overloaded() ? "overloaded" : shielding() ? "absorbing" : !needed() ? "standby" : ready() ? "ready" :
                power.status < minPower ? "unpowered" : "starved";
        }

        @Override
        public BlockStatus status(){
            return !online ? BlockStatus.logicDisable : ready() ? BlockStatus.active : !needed() ? BlockStatus.noOutput : BlockStatus.noInput;
        }

        @Override
        public float shieldRadius(){
            return radius;
        }

        @Override
        public boolean shielding(){
            return absorbedPulse >= 0 && absorbedPulse == Vesper.activity.pulses && Vesper.activity.pulseActive > 0f && ready();
        }

        @Override
        public boolean absorbPulse(int pulse){
            //clients see the outcome at once, not with the next periodic block snapshot
            if(ready() && items.has(fuel, absorbCost)){
                items.remove(fuel, absorbCost);
                coresSpent += absorbCost;
                absorbedPulse = pulse;
                held = true;
                sync();
                return true;
            }
            overloadedPulse = pulse;
            sync();
            return false;
        }

        @Override
        public boolean consumeTriggerValid(){
            //the core being burned keeps it running until its time is spent
            return upkeep > 0f;
        }

        /** A pulse is announced or running: it only runs, and pays, while there is something to hold back. */
        public boolean needed(){
            return Vesper.activity.pulsing();
        }

        @Override
        public boolean shouldConsume(){
            return online && needed();
        }

        @Override
        public void updateTile(){
            if(ready()){
                //pay first, so every tick of protection is paid for
                if(upkeep <= 0f){
                    consume();
                    coresSpent++;
                    upkeep += upkeepTime;
                }
                upkeep = Math.max(0f, upkeep - Time.delta);
            }
            if(!net.client()){
                boolean holding = shielding();
                //stopped mid-pulse: the absorption is lost and the machines it spared feel the rest of the pulse
                if(held && !holding && Vesper.activity.pulseActive > 0f) drop();
                held = holding;
            }
            warmup = Mathf.approachDelta(warmup, ready() ? 1f : 0f, 1f / 60f);
            strain = Mathf.approachDelta(strain, shielding() ? 1f : overloaded() ? 0.6f : 0f, 1f / 30f);
        }

        /** Sends this field's state to the clients now (the server's periodic block snapshots come every few seconds). */
        void sync(){
            if(net.server()) NetServer.syncBuilding(this);
        }

        void drop(){
            absorbedPulse = -1;
            held = false;
            Vesper.activity.unshield(this, radius);
            if(isValid()) sync();
        }

        @Override
        public void onRemoved(){
            if(!net.client() && held && shielding()) drop();
            super.onRemoved();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return item == fuel && items.get(fuel) < itemCapacity;
        }

        @Override
        public boolean configTapped(){
            configure(!online);
            Sounds.click.at(this);
            return false;
        }

        @Override
        public Boolean config(){
            return online;
        }

        @Override
        public void draw(){
            Draw.rect(block.region, x, y);
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.blend(Blending.additive);
            if(glowRegion.found()){
                Color c = overloaded() ? VPal.danger : fieldColor;
                float flicker = overloaded() ? Mathf.absin(Time.time, 2f, 0.6f) : Mathf.absin(Time.time + id * 11f, 14f, 0.2f);
                Draw.color(c, 0.1f + 0.9f * Math.max(warmup, strain) * (0.75f + flicker));
                Draw.rect(glowRegion, x, y);
            }
            Draw.blend();
            if(lampRegion.found()){
                //status lamp: resonance ready, bright while absorbing, red flicker overloaded, amber starved, dark off
                Draw.color(!online ? Pal.darkerMetal : overloaded() ? Tmp.c1.set(VPal.danger).a(0.5f + Mathf.absin(Time.time, 3f, 0.5f)) :
                    shielding() ? VPal.resonanceLight : ready() ? VPal.resonance : Tmp.c1.set(VPal.industrial).a(0.6f + Mathf.absin(Time.time, 6f, 0.4f)));
                Draw.rect(lampRegion, x, y + size * tilesize * 0.3f);
            }
            Draw.z(Layer.effect - 1f);
            if(warmup > 0.01f){
                //the field: a discreet ring at its reach, a bright wavering dome while it holds a pulse back
                Lines.stroke(1f, Tmp.c1.set(fieldColor).a(0.18f * warmup + 0.45f * strain));
                Lines.dashCircle(x, y, radius);
                if(strain > 0.01f && fieldRegion.found()){
                    Draw.blend(Blending.additive);
                    float r = radius * 2f * (0.97f + Mathf.absin(Time.time + id * 5f, 9f, 0.03f));
                    Draw.color(shielding() ? fieldColor : VPal.danger, 0.14f * strain);
                    Draw.rect(fieldRegion, x, y, r, r);
                    Draw.blend();
                }
            }
            Draw.color();
            Draw.z(z);
        }

        @Override
        public void drawSelect(){
            Drawf.dashCircle(x, y, radius, overloaded() ? VPal.danger : VPal.resonance);
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, (24f + 40f * strain) * warmup, fieldColor, 0.6f * Math.max(warmup, strain));
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(online);
            write.f(upkeep);
            write.i(absorbedPulse);
            write.i(overloadedPulse);
            write.i(coresSpent);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                online = read.bool();
                upkeep = read.f();
                absorbedPulse = read.i();
                overloadedPulse = read.i();
                coresSpent = read.i();
                held = absorbedPulse >= 0;
            }
        }
    }
}
