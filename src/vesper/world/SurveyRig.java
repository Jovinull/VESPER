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
import vesper.graphics.*;
import vesper.world.AncientSite.*;

import static mindustry.Vars.*;

/**
 * A field survey rig: built next to an ancient installation, it scans the nearest one still unexplored within its
 * range, spending power and one recovered circuit per sample (bring them by belt or unit). The survey work is kept
 * by the installation, so a rig that is destroyed or moved loses nothing; two rigs on one site work twice as fast and
 * cost twice as much. Only maps tagged {@link AncientSite#tagEnabled} have anything to survey.
 */
public class SurveyRig extends Block{
    /** Ticks of work one recovered circuit pays for; shared by every rig (see {@link #circuitsFor}). */
    public static float sampleTime = 4f * 60f;
    /** Reach from the rig's centre to the edge of an installation. */
    public float range = 7f * tilesize;
    public Color beamColor = VPal.resonanceLight;
    public TextureRegion dishRegion, lightRegion;

    public SurveyRig(String name){
        super(name);
        update = true;
        solid = true;
        sync = true;
        hasItems = true;
        hasPower = true;
        itemCapacity = 10;
        emitLight = true;
        envEnabled = Env.any;
    }

    /** Circuits a single rig spends on the given ticks of survey work. */
    public static int circuitsFor(float ticks){
        return Mathf.ceil(ticks / sampleTime);
    }

    @Override
    public void load(){
        super.load();
        dishRegion = Core.atlas.find(name + "-dish");
        lightRegion = Core.atlas.find(name + "-glow");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.range, range / tilesize, StatUnit.blocks);
        stats.add(Stat.productionTime, sampleTime / 60f, StatUnit.seconds);
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("survey", (SurveyRigBuild b) -> new Bar(
            () -> b.target() == null ? Core.bundle.get("vesper.rig.idle") : Core.bundle.format("vesper.rig.bar", (int)(b.target().investigation * 100)),
            () -> VPal.resonance, () -> b.target() == null ? 0f : b.target().investigation));
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);
        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range, VPal.resonance);
    }

    public class SurveyRigBuild extends Building{
        /** Survey work already paid for (one circuit buys a sample; it is paid when the sample starts). */
        public float sample;
        public float warmup, dish;
        int targetPos = -1;
        float retarget;
        private @Nullable SiteBuild found;
        private float foundDst;

        /** The installation being surveyed: the nearest unexplored one within range (rechecked every second). */
        public @Nullable SiteBuild target(){
            if(world.build(targetPos) instanceof SiteBuild site && valid(site)) return site;
            return null;
        }

        boolean valid(SiteBuild site){
            return site.team == team && site.siteState == AncientSite.SiteState.unexplored && site.active()
                && site.within(this, range + site.block.size * tilesize / 2f);
        }

        void findTarget(){
            //the team's building tree: only what stands within reach is looked at, however large the base
            found = null;
            foundDst = Float.MAX_VALUE;
            indexer.eachBlock(this, range, b -> b instanceof SiteBuild site && valid(site), b -> {
                float dst = b.dst2(this);
                if(dst < foundDst){
                    found = (SiteBuild)b;
                    foundDst = dst;
                }
            });
            targetPos = found == null ? -1 : found.pos();
            found = null;
        }

        @Override
        public boolean shouldConsume(){
            return target() != null;
        }

        @Override
        public void updateTile(){
            if((retarget += Time.delta) >= 60f || (targetPos != -1 && target() == null)){
                retarget = 0f;
                findTarget();
            }
            SiteBuild site = target();
            warmup = Mathf.approachDelta(warmup, site != null ? Mathf.clamp(efficiency) : 0f, 0.02f);
            dish += warmup * Time.delta * 2f;
            if(site == null || efficiency <= 0f) return;
            //pay first, so the work done is always paid for and the announced cost is exact
            if(sample <= 0f){
                consume();
                sample += sampleTime;
            }
            float work = Math.min(edelta(), sample);
            site.investigate(work / ((AncientSite)site.block).investigateTime);
            sample -= work;
        }

        @Override
        public void draw(){
            Draw.rect(block.region, x, y);
            if(dishRegion.found()) Draw.rect(dishRegion, x, y, dish);
            SiteBuild site = target();
            float z = Draw.z();
            if(lightRegion.found() && warmup > 0.01f){
                Draw.z(z + 0.01f);
                Draw.blend(Blending.additive);
                Draw.color(beamColor, warmup * (0.7f + Mathf.absin(Time.time, 5f, 0.3f)));
                Draw.rect(lightRegion, x, y);
                Draw.blend();
            }
            if(site != null && warmup > 0.05f){
                //the scan: a thin sweeping beam onto the installation
                Draw.z(Layer.effect);
                Draw.color(beamColor, warmup * 0.55f);
                Lines.stroke(1.2f * warmup);
                float sweep = Mathf.sin(Time.time / 20f + id) * site.block.size * tilesize * 0.4f;
                Lines.line(x, y, site.x + sweep, site.y - sweep * 0.5f);
                Fill.circle(site.x + sweep, site.y - sweep * 0.5f, 1.6f * warmup);
            }
            Draw.color();
            Draw.z(z);
        }

        @Override
        public void drawSelect(){
            Drawf.dashCircle(x, y, range, VPal.resonance);
            SiteBuild site = target();
            if(site != null) Drawf.square(site.x, site.y, site.block.size * tilesize / 2f + 2f, VPal.resonanceLight);
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, 30f * warmup, beamColor, 0.6f * warmup);
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(sample);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1) sample = read.f();
        }
    }
}
