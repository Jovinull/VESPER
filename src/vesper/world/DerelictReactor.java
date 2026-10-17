package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.struct.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.blocks.power.*;
import mindustry.world.meta.*;
import vesper.*;
import vesper.graphics.*;
import vesper.systems.*;

import static mindustry.Vars.*;

/**
 * An ancient reactor the player can bring back. It starts dormant and accepts the restoration materials from
 * belts or from a unit; once every material is in it is primed, and the player ignites it by hand (so the
 * awakening it causes happens when the defenses are ready). Running, it generates power scaled by planetary
 * activity and disturbs the basin. It is an indestructible ancient structure and enemies ignore it.
 */
public class DerelictReactor extends PowerGenerator{
    /** Materials needed to prime the reactor. */
    public ItemStack[] restoration = {};
    /** Planetary activity while dormant (scaled by restoration progress) and once running. */
    public float dormantActivity = 15f, runningActivity = 30f;
    /** Activity surge at ignition; large enough to wake the basin even with no industry around. */
    public float ignitionSurge = 50f;
    public Color glowColor = VPal.resonance;
    public Effect ignitionEffect = Fx.none;
    public TextureRegion glowRegion, activeRegion;

    public DerelictReactor(String name){
        super(name);
        solid = true;
        update = true;
        sync = true;
        hasItems = true;
        itemCapacity = 0;
        configurable = true;
        breakable = false;
        rebuildable = false;
        targetable = false;
        destructible = true;
        drawDisabled = false;
        emitLight = true;
        flags = EnumSet.of(BlockFlag.generator);
        envEnabled = Env.any;

        config(Boolean.class, (ReactorBuild build, Boolean ignite) -> {
            if(ignite) build.ignite();
        });
    }

    @Override
    public void load(){
        super.load();
        glowRegion = Core.atlas.find(name + "-glow");
        activeRegion = Core.atlas.find(name + "-active");
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.input, StatValues.items(false, restoration));
    }

    @Override
    public void setBars(){
        super.setBars();
        for(int i = 0; i < restoration.length; i++){
            int index = i;
            ItemStack stack = restoration[i];
            addBar("restore-" + stack.item.name, (ReactorBuild b) -> new Bar(
                () -> Core.bundle.format("vesper.reactor.bar", stack.item.localizedName, b.delivered[index], stack.amount),
                () -> b.delivered[index] >= stack.amount ? VPal.resonance : VPal.industrial,
                () -> b.delivered[index] / (float)stack.amount));
        }
        addBar("activity", (ReactorBuild b) -> new Bar(
            () -> Core.bundle.format("vesper.reactor.output", Strings.autoFixed(Vesper.activity.outputMultiplier(), 2)),
            () -> VPal.resonanceLight,
            () -> b.ignited ? Vesper.activity.fraction() : 0f));
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    public class ReactorBuild extends GeneratorBuild implements ActivitySource{
        public int[] delivered = new int[restoration.length];
        public boolean ignited;
        /** Smoothed visual intensity. */
        public float glow;

        public float progress(){
            int total = 0, have = 0;
            for(int i = 0; i < restoration.length; i++){
                total += restoration[i].amount;
                have += Math.min(delivered[i], restoration[i].amount);
            }
            return total == 0 ? 1f : have / (float)total;
        }

        public boolean primed(){
            return progress() >= 1f;
        }

        public int remaining(Item item){
            if(ignited) return 0;
            for(int i = 0; i < restoration.length; i++){
                if(restoration[i].item == item) return Math.max(restoration[i].amount - delivered[i], 0);
            }
            return 0;
        }

        void add(Item item, int amount){
            for(int i = 0; i < restoration.length; i++){
                if(restoration[i].item == item) delivered[i] = Math.min(delivered[i] + amount, restoration[i].amount);
            }
        }

        public void ignite(){
            if(ignited || !primed()) return;
            ignited = true;
            ignitionEffect.at(x, y, size * tilesize, glowColor);
            Sounds.explosionReactor.at(x, y, 0.6f);
            Effect.shake(4f, 60f, x, y);
            Vesper.activity.surge(ignitionSurge);
        }

        @Override
        public float activity(){
            return ignited ? runningActivity : dormantActivity * progress();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return remaining(item) > 0;
        }

        @Override
        public void handleItem(Building source, Item item){
            add(item, 1);
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            return source == null || source.team() == team ? Math.min(remaining(item), amount) : 0;
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            add(item, amount);
        }

        @Override
        public void updateTile(){
            productionEfficiency = ignited ? Vesper.activity.outputMultiplier() : 0f;
            float target = ignited ? 1f : primed() ? 0.75f : 0.12f + progress() * 0.5f;
            glow = Mathf.approachDelta(glow, target, 0.01f);
        }

        @Override
        public void damage(float damage){
            //an ancient structure: the player's war cannot scratch it
        }

        @Override
        public boolean shouldShowConfigure(Player player){
            return primed() && !ignited;
        }

        @Override
        public void buildConfiguration(Table table){
            table.button(Icon.power, Styles.cleari, () -> ui.showConfirm("@vesper.reactor.ignite.title", "@vesper.reactor.ignite.confirm", () -> configure(true)))
                .size(50f).tooltip("@vesper.reactor.ignite.title");
        }

        @Override
        public void draw(){
            Draw.rect(block.region, x, y);
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.blend(Blending.additive);
            if(glowRegion.found()){
                //dormant: a dying flicker; restoring: a slow breath that grows with progress
                float flicker = ignited ? 1f : primed() ? 0.85f + Mathf.absin(Time.time, 12f, 0.15f) :
                    0.7f + Mathf.absin(Time.time + id * 31f, 5f, 0.2f) * (Mathf.randomSeed((long)(Time.time / 8f) + id) > 0.15f ? 1f : 0.2f);
                Draw.color(glowColor, glow * flicker);
                Draw.rect(glowRegion, x, y);
            }
            if(ignited && activeRegion.found()){
                Draw.color(glowColor, 0.65f + Mathf.absin(Time.time, 18f, 0.35f) * Mathf.clamp(0.4f + Vesper.activity.fraction()));
                Draw.rect(activeRegion, x, y);
            }
            Draw.blend();
            Draw.color();
            Draw.z(z);
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, (ignited ? 90f : 30f + 30f * progress()) * size / 4f, glowColor, ignited ? 0.9f : 0.35f * glow);
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(ignited);
            write.b(delivered.length);
            for(int d : delivered) write.i(d);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                ignited = read.bool();
                int count = read.b();
                for(int i = 0; i < count; i++){
                    int d = read.i();
                    if(i < delivered.length) delivered[i] = d;
                }
            }
            glow = ignited ? 1f : glow;
        }
    }
}
