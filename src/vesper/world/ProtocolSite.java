package vesper.world;

import arc.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import vesper.*;
import vesper.graphics.*;
import vesper.world.ProtocolController.*;

import static mindustry.Vars.*;

/**
 * An installation of the Central Control complex that its controller brings back: the distribution junction, the
 * conduction array, the processing line. It is not surveyed on its own: surveying the {@link ProtocolController}
 * reveals it. A subsystem stays sealed off until the junction is restored (that restores access to the wings).
 * Restoring one takes two things, in order: its materials, delivered by belt or unit; then {@link #workTime} ticks of
 * the controller's recovery protocol at full share (more or less as the routing gives it, faster when the basin is
 * loud). Restored, a site works for the player: the array feeds its {@link #generation} into the grid it is linked
 * to (scaled by the basin's resonance like the old reactor), the processing line turns its recipe into resonance
 * cores with power from the grid. Both are resonance machines: a pulse aims at them and stops them while it lasts,
 * unless a containment field that absorbed it covers them. Running, they disturb the basin.
 */
public class ProtocolSite extends AncientSite{
    /** Ticks of recovery work at full share, after the materials. */
    public float workTime = 45f * 60f;
    /** Power fed into the grid once restored (the conduction array), times the basin's output multiplier. */
    public float generation = 0f;
    /** A wing's subsystem: sealed off until the junction is restored. The junction is not one. */
    public boolean subsystem = true;
    /** Ticks a restored machine may wait for its inputs and still count as running for the complex. */
    public float workGap = 10f * 60f;

    public ProtocolSite(String name){
        super(name);
        unloadable = false;
    }

    @Override
    public void init(){
        if(generation > 0f){
            hasPower = true;
            outputsPower = true;
            consumesPower = false;
        }
        super.init();
    }

    public class ProtocolSiteBuild extends SiteBuild{
        /** Recovery work done (ticks at full share). */
        public float work;
        /** The game tick its materials were complete (the controller restores in that order; saved); -1 while they are not. */
        public double readyAt = -1;
        /** Smoothed visual of the work arriving. */
        public float driven;
        float drivenTimer;
        /** The game tick the restored line last worked (a gap of a second or two between inputs is not a stop); -1: not since loading. */
        double lastWorked = -1;

        public @Nullable ControllerBuild controller(){
            return ProtocolController.find(team);
        }

        @Override
        public boolean surveyable(){
            return false;
        }

        /** Whether the controller can reach it: revealed, and for a subsystem the junction restored. */
        public boolean accessible(){
            var c = controller();
            return c != null && c.known() && (!subsystem || c.access());
        }

        /** Materials in, waiting for (or under) the recovery protocol. */
        public boolean ready(){
            return siteState == SiteState.restoring && restoration() >= 1f;
        }

        public float workFraction(){
            return Mathf.clamp(work / workTime);
        }

        /**
         * Restored and working for the complex: not stopped by a pulse; the array feeding, the line running (or having run
         * within the last {@link #workGap} ticks: waiting a moment for its next input is not a stop, running dry is).
         */
        public boolean operational(){
            if(siteState != SiteState.restored || Vesper.activity.interferes(this)) return false;
            return generation > 0f || efficiency > 0f || (lastWorked >= 0 && state.tick - lastWorked < workGap);
        }

        /** The controller's recovery protocol drives the restoration (ticks of work at full share). */
        public void work(float amount){
            if(!ready()) return;
            work = Math.min(workTime, work + amount);
            driven = 1f;
            drivenTimer = 0f;
            if(work >= workTime && !net.client()){
                siteState = SiteState.restored;
                if(restoreSurge > 0f) Vesper.activity.surge(restoreSurge);
                VFx.restorationComplete.at(x, y, size * tilesize, glowColor);
                Events.fire(new SiteEvent(this));
                sync();
            }
        }

        @Override
        public void updateTile(){
            //the controller reveals it, and opens it once it can reach it
            if(active() && !net.client()){
                if(siteState == SiteState.unexplored && controller() != null && controller().known()){
                    siteState = accessible() ? SiteState.restoring : SiteState.investigated;
                    Events.fire(new SiteEvent(this));
                    sync();
                }else if(siteState == SiteState.investigated && accessible()){
                    siteState = SiteState.restoring;
                    Fx.healWaveDynamic.at(x, y, size * tilesize, glowColor);
                    Events.fire(new SiteEvent(this));
                    sync();
                }
            }
            if(ready() && readyAt < 0) readyAt = state.tick;
            if(generation > 0f && operational()) rotation += Time.delta * 1.2f;
            if(siteState == SiteState.restored && efficiency > 0f) lastWorked = state.tick;
            if((drivenTimer += Time.delta) > 10f) driven = Mathf.approachDelta(driven, 0f, 1f / 30f);
            super.updateTile();
        }

        @Override
        public void deliver(Item item, int amount){
            //the materials only: the restoration itself is the controller's work
            for(int i = 0; i < restoration.length; i++){
                if(restoration[i].item == item) delivered[i] = Math.min(delivered[i] + amount, restoration[i].amount);
            }
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(siteState == SiteState.restoring && ready()) return false;
            return super.acceptItem(source, item);
        }

        @Override
        public boolean shouldConsume(){
            //the array runs (and counts as a working resonance machine) once restored
            if(generation > 0f) return siteState == SiteState.restored && active();
            return super.shouldConsume();
        }

        @Override
        public float efficiencyScale(){
            //a pulse stops the complex's machinery while it lasts; otherwise it feeds on the basin's resonance
            return siteState == SiteState.restored ? (Vesper.activity.interferes(this) ? 0f : Vesper.activity.resonanceMultiplier()) : 1f;
        }

        @Override
        public float getPowerProduction(){
            //the array: its output while restored and not stopped by a pulse, as the basin resonates
            return generation > 0f && siteState == SiteState.restored && !Vesper.activity.interferes(this) ? generation * Vesper.activity.outputMultiplier() : 0f;
        }

        @Override
        public float activity(){
            if(siteState != SiteState.restored || !active()) return 0f;
            return generation > 0f ? (Vesper.activity.interferes(this) ? 0f : runningActivity) : super.activity();
        }

        @Override
        public String infoText(){
            String key = "vesper.site." + block.name.replace("vesper-", "");
            return switch(siteState){
                case unexplored -> Core.bundle.get("vesper.protocol.site.unknown");
                case investigated -> Core.bundle.get(key + ".purpose") + "\n\n" + Core.bundle.get("vesper.protocol.site.sealed");
                case restoring -> Core.bundle.get(key + ".purpose") + "\n\n" + (ready()
                    ? Core.bundle.format("vesper.protocol.site.work", (int)(workFraction() * 100f), (int)(workTime / 60f))
                    : Core.bundle.format("vesper.site.restoring", (int)(restoration() * 100), missing()));
                case restored -> Core.bundle.get(key + ".purpose") + "\n\n" + (operational() ? Core.bundle.get("vesper.protocol.site.running")
                    : Vesper.activity.interferes(this) ? Core.bundle.get("vesper.protocol.site.pulse") : Core.bundle.get("vesper.protocol.site.idle"));
                default -> super.infoText();
            };
        }

        @Override
        public String barText(){
            return switch(siteState){
                case unexplored -> Core.bundle.get("vesper.protocol.site.bar.unknown");
                case investigated -> Core.bundle.get("vesper.protocol.site.bar.sealed");
                case restoring -> ready() ? Core.bundle.format("vesper.protocol.site.bar.work", (int)(workFraction() * 100f))
                    : Core.bundle.format("vesper.site.bar.restoration", (int)(restoration() * 100));
                case restored -> operational() ? Core.bundle.get("vesper.protocol.site.bar.running") : Core.bundle.get("vesper.protocol.site.bar.stopped");
                default -> super.barText();
            };
        }

        @Override
        float barFraction(){
            return siteState == SiteState.restoring && ready() ? workFraction() : siteState == SiteState.restored ? (operational() ? 1f : 0f) : super.barFraction();
        }

        @Override
        public void draw(){
            super.draw();
            //the recovery protocol at work: a frame of light closing in as the work advances
            if(siteState == SiteState.restoring && ready() && driven > 0.01f){
                float z = Draw.z();
                Draw.z(Layer.blockOver);
                Lines.stroke(1.5f, Tmp.c1.set(VPal.industrialLight).a(0.6f * driven));
                float r = size * tilesize / 2f + 3f;
                Lines.square(x, y, r * (1.6f - 0.6f * workFraction()));
                Draw.color();
                Draw.z(z);
            }
        }

        @Override
        public byte version(){
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(work);
            write.d(readyAt);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                work = read.f();
                readyAt = read.d();
            }
        }
    }
}
