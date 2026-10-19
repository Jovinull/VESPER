package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import vesper.*;
import vesper.content.*;
import vesper.graphics.*;
import vesper.systems.*;

import static mindustry.Vars.*;

/**
 * The builders' stabilizer pylon, working again. Powered and switched on, it holds the basin's resonance down:
 * the strongest stabilizer takes {@link PlanetaryActivity#stabilizerStrength} points off the activity target and
 * every further one a fixed fraction of the previous (see {@link PlanetaryActivity#stabilization}), so a base can
 * be made quieter but never silent. Tapping it switches it on or off; off, it draws no power. A resonance pulse
 * overloads the stabilizers it reaches, unless a containment field that absorbed it covers them. Pylons the
 * builders left in the maps (derelict) stay inert ruins and cannot be dismantled for materials.
 */
public class Stabilizer extends Block{
    public Color glowColor = VPal.resonance;
    public TextureRegion glowRegion, lampRegion, fieldRegion;

    public Stabilizer(String name){
        super(name);
        update = true;
        solid = true;
        destructible = true;
        configurable = true;
        saveConfig = true;
        sync = true;
        hasPower = true;
        emitLight = true;
        envEnabled = Env.any;

        config(Boolean.class, (StabilizerBuild build, Boolean online) -> build.online = online);
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
        stats.add(VStats.stabilization, Core.bundle.format("vesper.stat.stabilization",
            Strings.autoFixed(Vesper.activity.stabilizerStrength, 1), (int)(Vesper.activity.stabilizerFalloff * 100f),
            Strings.autoFixed(Vesper.activity.stabilizerStrength / (1f - Vesper.activity.stabilizerFalloff), 1)));
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("stabilization", (StabilizerBuild b) -> new Bar(
            () -> b.online ? Core.bundle.format("vesper.stabilizer.bar", Strings.autoFixed(b.contribution, 1)) :
                Core.bundle.get("vesper.stabilizer.off"),
            () -> b.overloaded() ? VPal.dangerLight : b.online ? VPal.resonance : Color.gray,
            () -> b.contribution / Vesper.activity.stabilizerStrength));
    }

    @Override
    public boolean canBreak(Tile tile){
        //the builders' pylons are ruins, not salvage (dismantling a derelict block refunds part of its cost)
        return tile.team() != Team.derelict;
    }

    public class StabilizerBuild extends Building implements ActivityStabilizer{
        /** Switched on by the player (saved, and kept when the block is rebuilt or copied). */
        public boolean online = true;
        /** Points this stabilizer takes off the activity right now, after diminishing returns (from the server). */
        public float contribution;
        /** Smoothed operation, for the visuals. */
        public float warmup;

        public boolean overloaded(){
            return Vesper.activity.interferes(this);
        }

        @Override
        public float stabilizerOperation(){
            return online && team != Team.derelict && !overloaded() ? Mathf.clamp(efficiency) : 0f;
        }

        @Override
        public void stabilizerContribution(float points){
            contribution = points;
        }

        @Override
        public boolean shouldConsume(){
            return online;
        }

        @Override
        public void updateTile(){
            warmup = Mathf.approachDelta(warmup, stabilizerOperation(), 1f / 90f);
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
            if(team == Team.derelict){
                //a ruin: the same slow breathing as the other ancient structures, stronger as the basin stirs
                float activity = Vesper.activity.fraction();
                Draw.color(glowColor, Math.min(1f, 0.8f * (1f + activity * 0.6f) * (0.65f + Mathf.absin(Time.time + id * 13f, 25f * (1f - activity * 0.5f), 0.35f))));
                Draw.rect(glowRegion, x, y);
            }else{
                Draw.color(glowColor, 0.15f + 0.85f * warmup * (0.8f + Mathf.absin(Time.time + id * 13f, 18f, 0.2f)));
                Draw.rect(glowRegion, x, y);
                //the field it holds: a faint ring breathing around the pylon
                if(warmup > 0.01f && fieldRegion.found()){
                    float r = size * tilesize * (1.6f + Mathf.absin(Time.time + id * 7f, 30f, 0.25f));
                    Draw.color(glowColor, 0.22f * warmup);
                    Draw.rect(fieldRegion, x, y, r, r);
                }
            }
            Draw.blend();
            if(team != Team.derelict && lampRegion.found()){
                //status lamp on the upper emitter: resonance when working, amber when on but starved or overloaded, dark when off
                Draw.color(!online ? Pal.darkerMetal : warmup > 0.5f ? VPal.resonanceLight : Tmp.c1.set(VPal.industrial).a(0.6f + Mathf.absin(Time.time, 6f, 0.4f)));
                Draw.rect(lampRegion, x, y + size * tilesize * 0.34f);
            }
            Draw.color();
            Draw.z(z);
        }

        @Override
        public void drawLight(){
            if(team != Team.derelict) Drawf.light(x, y, 36f * warmup, glowColor, 0.7f * warmup);
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(online);
            write.f(contribution);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            //revision 0: a pylon saved by 0.2, when it was a plain ruin
            if(revision >= 1){
                online = read.bool();
                contribution = read.f();
            }
        }
    }
}
