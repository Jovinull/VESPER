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
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import vesper.content.*;
import vesper.graphics.*;
import vesper.world.ProtocolController.*;

import static mindustry.Vars.*;

/**
 * A containment lock over one of the vents the planet answers through (a gate of the planetary response), placed
 * by a map on the player's team. It takes nothing from the player's grid: the {@link ProtocolController central
 * controller} drives it over the complex's own conduits with its containment bus, and pays its cores from its
 * own store. With its whole share it closes over the vent in {@link #sealTime} ticks; sealed, the vent is shut: no
 * patrol climbs out of it and no pulse aims through it, and a pulse already announced through it is contained when
 * it lands (for {@link #containCost} cores). Short of its share, or with no core to pay its time, it opens at once.
 * A player switches it off with a tap, leaving its share to the other locks or to recovery. The builders made it to
 * last: it takes no damage, cannot be dismantled, and nothing can be taken out of it.
 */
public class ContainmentLock extends Block{
    /** Power it takes from the containment bus (per tick), ticks to close, ticks of sealing one core pays, share of its power it needs to hold. */
    public float power = 1f, sealTime = 10f * 60f, upkeepTime = 20f * 60f, minPower = 0.9f;
    /** Cores to hold back a pulse announced through its vent before it closed. */
    public int containCost = 6;
    public TextureRegion shutterRegion, glowRegion, lampRegion;

    public ContainmentLock(String name){
        super(name);
        update = true;
        solid = true;
        sync = true;
        destructible = true;
        breakable = false;
        rebuildable = false;
        targetable = false;
        configurable = true;
        saveConfig = true;
        hasItems = false;
        unloadable = false;
        emitLight = true;
        envEnabled = Env.any;

        config(Boolean.class, (LockBuild build, Boolean online) -> build.online = online);
    }

    @Override
    public void load(){
        super.load();
        shutterRegion = Core.atlas.find(name + "-shutter");
        glowRegion = Core.atlas.find(name + "-glow");
        lampRegion = Core.atlas.find("vesper-activity-indicator");
    }

    @Override
    public boolean canBreak(Tile tile){
        return false;
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(VStats.protocol, Core.bundle.format("vesper.stat.lock", Strings.autoFixed(power * 60f, 1), Strings.autoFixed(sealTime / 60f, 1),
            Strings.autoFixed(upkeepTime / 60f, 1), containCost));
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("lock", (LockBuild b) -> new Bar(b::label, () -> b.sealed() ? VPal.resonanceLight : b.seal > 0f ? VPal.resonance
            : b.online && b.controller() != null && b.controller().known() ? VPal.dangerLight : Color.gray, () -> b.seal));
    }

    /** The lock over a vent (a gate tile): standing on it or within two tiles of it. */
    public static @Nullable LockBuild over(Tile gate){
        if(gate == null) return null;
        for(int dx = -2; dx <= 2; dx++){
            for(int dy = -2; dy <= 2; dy++){
                Tile t = world.tile(gate.x + dx, gate.y + dy);
                if(t != null && t.build instanceof LockBuild l) return l;
            }
        }
        return null;
    }

    /** Whether a vent is shut by a sealed lock. */
    public static boolean shuts(Tile gate){
        var l = over(gate);
        return l != null && l.sealed();
    }

    /** A pulse announced through this vent lands on its sealed lock: whether the lock pays to hold it back. */
    public static boolean hold(Tile gate){
        var l = over(gate);
        return l != null && l.contain();
    }

    public class LockBuild extends Building{
        /** Switched on by the player (saved). */
        public boolean online = true;
        /** Share of its power the controller delivered (0-1), and the update it did (-1: not since the world loaded). */
        public float supply;
        public long supplied = -1;
        /** Closing progress (0-1, sealed at 1), ticks of sealing already paid for. */
        public float seal, upkeep;
        /** Cores spent and pulses contained since the map began (saved). */
        public int coresSpent, contained;
        /** Smoothed visuals. */
        public float shutter, alarm;

        public @Nullable ControllerBuild controller(){
            return ProtocolController.find(team);
        }

        public boolean sealed(){
            return seal >= 1f;
        }

        /** The controller hands over this tick's share (called by the controller, on every side). */
        public void supply(float share){
            supply = share;
            supplied = state.updateId;
        }

        /** Driven this tick: switched on, with (nearly) its whole share. */
        public boolean fed(){
            return online && supply >= ((ContainmentLock)block).minPower && state.updateId - supplied <= 1;
        }

        @Override
        public void updateTile(){
            var c = controller();
            //a world just loaded: wait for the controller's first word before opening a saved seal
            if(supplied < 0 && c != null){
                shutter = seal;
                return;
            }
            //a lock with nothing to pay its sealed time with does not start closing (it would only open again)
            boolean fed = fed() && c != null && c.known() && (upkeep > 0f || c.items.has(c.fuel()));
            if(fed && sealed() && upkeep <= 0f){
                //every tick of a closed vent is paid for; with no core to pay, it opens
                if(c.takeCore()){
                    coresSpent++;
                    upkeep += upkeepTime;
                }else{
                    fed = false;
                }
            }
            if(fed){
                seal = Math.min(1f, seal + Time.delta / sealTime);
                if(sealed()) upkeep = Math.max(0f, upkeep - Time.delta);
            }else if(seal > 0f){
                boolean was = sealed();
                seal = 0f;
                if(was && net.server()) NetServer.syncBuilding(this);
            }
            shutter = Mathf.approachDelta(shutter, seal, 1f / 20f);
            alarm = Mathf.approachDelta(alarm, !sealed() && vesper.Vesper.activity.pulsing() ? 1f : 0f, 1f / 30f);
        }

        /** A pulse announced through this vent landed while it was shut: the lock pays to hold it (false: it cannot). */
        public boolean contain(){
            var c = controller();
            if(c == null || !sealed()) return false;
            int paid = 0;
            while(paid < containCost && c.takeCore()) paid++;
            coresSpent += paid;
            //short of cores it cannot hold the pulse: what it paid is spent, and the pulse lands
            if(paid == containCost) contained++;
            if(net.server()) NetServer.syncBuilding(this);
            return paid == containCost;
        }

        /** The state as a player reads it. */
        public String label(){
            var c = controller();
            if(c == null || !c.known()) return Core.bundle.get("vesper.lock.unknown");
            if(!online) return Core.bundle.get("vesper.lock.offline");
            if(sealed()) return Core.bundle.get("vesper.lock.sealed");
            if(seal > 0f) return Core.bundle.format("vesper.lock.closing", (int)(seal * 100f));
            if(supply > 0f) return Core.bundle.format("vesper.lock.short", (int)(supply * 100f));
            return Core.bundle.get("vesper.lock.open");
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
        public void damage(float damage){
            //the builders' seal takes no damage
        }

        @Override
        public void draw(){
            Draw.rect(block.region, x, y);
            //the shutters slide over the vent as it closes
            if(shutterRegion.found() && shutter > 0.01f){
                float off = (1f - shutter) * size * tilesize * 0.5f;
                Draw.rect(shutterRegion, x - off, y, shutterRegion.width * Draw.scl * shutter, shutterRegion.height * Draw.scl);
                Draw.rect(shutterRegion, x + off, y, -shutterRegion.width * Draw.scl * shutter, shutterRegion.height * Draw.scl);
            }
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.blend(Blending.additive);
            if(glowRegion.found()){
                //an open vent breathes amber, red while the planet answers; a sealed one holds a steady cyan
                Color c = sealed() ? VPal.resonanceLight : Tmp.c1.set(VPal.industrial).lerp(VPal.danger, alarm);
                Draw.color(c, sealed() ? 0.55f : 0.35f + Mathf.absin(Time.time + id * 5f, 9f, 0.35f));
                Draw.rect(glowRegion, x, y);
            }
            Draw.blend();
            if(lampRegion.found()){
                Draw.color(!online ? Pal.darkerMetal : sealed() ? VPal.resonanceLight : seal > 0f ? VPal.resonance : VPal.dangerLight);
                Draw.rect(lampRegion, x - size * tilesize * 0.32f, y + size * tilesize * 0.32f);
            }
            Draw.color();
            Draw.z(z);
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, size * tilesize * 1.6f, sealed() ? VPal.resonanceLight : VPal.industrial, 0.5f);
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(online);
            write.f(seal);
            write.f(upkeep);
            write.i(coresSpent);
            write.i(contained);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                online = read.bool();
                seal = read.f();
                upkeep = read.f();
                coresSpent = read.i();
                contained = read.i();
            }
        }
    }
}
