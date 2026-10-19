package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import vesper.*;
import vesper.graphics.*;
import vesper.systems.*;

import static mindustry.Vars.*;

/**
 * The relay of an ancient power works, still wired to the works below. It draws a fixed amount of power from the
 * grid it is linked to and counts the energy it actually receives (its share of the grid's satisfaction, tick by
 * tick): an underpowered grid delivers part, a dead one nothing, and nothing is counted for power that never came.
 * Fed, the works answer: it disturbs the basin in proportion to what it receives. Placed by a scenario map, it is an
 * indestructible ancient structure that enemies ignore and the player cannot dismantle. The server counts; clients
 * get the count with the block's synced state.
 */
public class WorksRelay extends Block{
    /** Planetary activity while fully fed (scaled by what it receives, smoothed). */
    public float fedActivity = 40f;
    public Color glowColor = VPal.resonanceLight;
    public TextureRegion glowRegion, rotatorRegion;

    public WorksRelay(String name){
        super(name);
        solid = true;
        update = true;
        sync = true;
        hasPower = true;
        breakable = false;
        rebuildable = false;
        targetable = false;
        destructible = true;
        emitLight = true;
        envEnabled = Env.any;
    }

    @Override
    public void load(){
        super.load();
        glowRegion = Core.atlas.find(name + "-glow");
        rotatorRegion = Core.atlas.find(name + "-rotator");
    }

    @Override
    public boolean canBreak(Tile tile){
        //the scenario's relay: deconstructing it would refund what it never cost and end the scenario
        return false;
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("relay", (RelayBuild b) -> new Bar(
            () -> Core.bundle.format("vesper.relay.bar", Strings.autoFixed(b.rate, 0), (int)(b.satisfaction() * 100f)),
            () -> b.satisfaction() > 0.99f ? VPal.resonanceLight : b.satisfaction() > 0f ? VPal.industrial : VPal.dangerLight,
            b::satisfaction));
    }

    public class RelayBuild extends Building implements ActivitySource{
        /** Energy received since the map began (power units; saved, counted by the server). */
        public double delivered;
        /** Power received per second, smoothed over a few seconds (display). */
        public float rate;
        /** Smoothed share of its draw it gets, for the activity and the visuals. */
        public float warmup;
        float spin;

        /** Share of its draw the grid supplies right now. */
        public float satisfaction(){
            return Mathf.clamp(power.status);
        }

        /** Power it draws at full supply, per second. */
        public float demand(){
            return block.consPower == null ? 0f : block.consPower.usage * 60f;
        }

        @Override
        public float activity(){
            return fedActivity * warmup;
        }

        @Override
        public void updateTile(){
            float got = block.consPower == null ? 0f : satisfaction() * block.consPower.usage * Time.delta;
            if(!net.client()) delivered += got;
            rate = Mathf.lerpDelta(rate, got / Math.max(Time.delta, 0.0001f) * 60f, 0.02f);
            warmup = Mathf.approachDelta(warmup, satisfaction(), 1f / 180f);
            spin += warmup * Time.delta;
        }

        @Override
        public void damage(float damage){
            //an ancient structure: the player's war cannot scratch it
        }

        @Override
        public void draw(){
            Draw.rect(block.region, x, y);
            if(rotatorRegion.found()) Draw.rect(rotatorRegion, x, y, spin * 0.8f);
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.blend(Blending.additive);
            if(glowRegion.found()){
                Draw.color(glowColor, 0.12f + 0.88f * warmup * (0.8f + Mathf.absin(Time.time + id * 9f, 16f, 0.2f)));
                Draw.rect(glowRegion, x, y);
            }
            Draw.blend();
            Draw.color();
            Draw.z(z);
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, (40f + 50f * warmup) * size / 3f, glowColor, 0.3f + 0.5f * warmup);
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.d(delivered);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                delivered = read.d();
                warmup = read.f();
            }
        }
    }
}
