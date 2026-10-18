package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.entities.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import vesper.*;
import vesper.graphics.*;
import vesper.systems.*;

import static mindustry.Vars.*;

/**
 * An ancient installation for industrial archaeology: placed by a scenario map on the player's team (derelict
 * buildings never update and any team may configure them), indestructible and ignored by enemies.
 * <p>
 * States: unexplored (a {@link SurveyRig} nearby investigates it; the progress is kept by the site, so a lost rig
 * loses nothing) → investigated (its purpose is known; the player chooses) → dismantling (a finite salvage comes
 * out into its own store, for belts and units, at the cost of power and time) or restoring (restoration materials
 * delivered by belt or unit) → dismantled (an empty husk, for good) or restored (the machine works again, with its
 * own recipe). A site without choices (a data archive) unlocks a block on this map when investigated.
 * <p>
 * The server decides every transition and the salvage; clients get them at once ({@link NetServer#syncBuilding}).
 * Only maps tagged {@link #tagEnabled} run it: elsewhere a site stays an inert ruin.
 */
public class AncientSite extends Block{
    public static final String tagEnabled = "vesper.archaeology";
    /** Choices a player can send (config); every other transition belongs to the server. */
    public static final int chooseRestore = 1, chooseDismantle = 2;
    /** Survey fraction at which the server reveals a site (clients predict up to 0.999 and wait for it). */
    static final float complete = 0.9999f;

    public enum SiteState{
        unexplored, investigated, dismantling, restoring, restored, dismantled;

        public String localized(){
            return Core.bundle.get("vesper.site.state." + name());
        }
    }

    /** Survey work to reveal it, in ticks of one rig at full efficiency, and the activity surge when it is revealed. */
    public float investigateTime = 30f * 60f, investigateSurge = 0f;
    /** The finite salvage dismantling releases, and the ticks it takes at full power. */
    public ItemStack[] salvage = {};
    public float dismantleTime = 40f * 60f;
    /** Materials that bring it back, and the surge when it wakes. */
    public ItemStack[] restoration = {};
    public float restoreSurge = 0f;
    /** The restored machine: inputs, output and craft time; the activity it adds while working. */
    public ItemStack[] recipe = {};
    public @Nullable ItemStack output;
    public float craftTime = 120f, runningActivity = 0f;
    /** What investigating it unlocks on this map (a data archive); null for machines. */
    public @Nullable Block unlocks;

    public Color glowColor = VPal.resonance;
    public TextureRegion glowRegion, restoredRegion, restoredGlowRegion, rotatorRegion, dismantledRegion, lampRegion;

    public AncientSite(String name){
        super(name);
        solid = true;
        update = true;
        sync = true;
        destructible = true;
        breakable = false;
        rebuildable = false;
        targetable = false;
        configurable = true;
        hasItems = true;
        itemCapacity = 20;
        unloadable = true;
        emitLight = true;
        envEnabled = Env.any;
        flags = EnumSet.of(BlockFlag.factory);

        config(Integer.class, (SiteBuild build, Integer choice) -> build.choose(choice));
    }

    @Override
    public void load(){
        super.load();
        glowRegion = Core.atlas.find(name + "-glow");
        restoredRegion = Core.atlas.find(name + "-restored", region);
        restoredGlowRegion = Core.atlas.find(name + "-restored-glow");
        rotatorRegion = Core.atlas.find(name + "-rotator");
        dismantledRegion = Core.atlas.find(name + "-dismantled", region);
        lampRegion = Core.atlas.find("vesper-activity-indicator");
    }

    @Override
    public boolean canBreak(Tile tile){
        //an installation is restored or dismantled, never deconstructed (that would refund what it never cost)
        return false;
    }

    public boolean hasChoices(){
        return salvage.length > 0 && restoration.length > 0;
    }

    public static boolean enabled(){
        return state.rules.tags.getBool(tagEnabled);
    }

    static int total(ItemStack[] stacks){
        int sum = 0;
        for(ItemStack s : stacks) sum += s.amount;
        return sum;
    }

    @Override
    public void setStats(){
        super.setStats();
        if(restoration.length > 0) stats.add(Stat.input, StatValues.items(false, restoration));
        if(salvage.length > 0) stats.add(Stat.output, StatValues.items(false, salvage));
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("items");
        addBar("site", (SiteBuild b) -> new Bar(b::barText, () -> b.siteState == SiteState.dismantled ? Color.gray : b.siteState == SiteState.dismantling ? VPal.industrial : VPal.resonance,
            b::barFraction));
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    public class SiteBuild extends Building implements ActivitySource{
        public SiteState siteState = SiteState.unexplored;
        /** Investigation progress 0-1, kept by the site so an interrupted survey resumes. */
        public float investigation;
        /** Dismantling progress 0-1 and the salvage still in the machine, per stack. */
        public float dismantle;
        public int[] salvageLeft = new int[salvage.length];
        /** Restoration materials delivered, per stack. */
        public int[] delivered = new int[restoration.length];
        /** Crafting progress of the restored machine. */
        public float progress;
        /** Smoothed visuals. */
        public float glow, rotation, working;

        {
            for(int i = 0; i < salvage.length; i++) salvageLeft[i] = salvage[i].amount;
        }

        public boolean active(){
            return enabled() && team != Team.derelict;
        }

        /** A survey rig adds investigation work (both sides predict it; only the server reveals the site). */
        public void investigate(float amount){
            if(siteState != SiteState.unexplored || !active()) return;
            investigation = Math.min(investigation + amount, net.client() ? 0.999f : 1f);
            //the work arrives frame by frame and its float sum can fall a hair short of 1: that hair is not another sample
            if(investigation >= complete && !net.client()){
                investigation = 1f;
                siteState = SiteState.investigated;
                if(investigateSurge > 0f) Vesper.activity.surge(investigateSurge);
                if(unlocks != null) unlock(unlocks);
                Fx.healWaveDynamic.at(x, y, size * tilesize * 1.5f, glowColor);
                Events.fire(new SiteEvent(this));
                sync();
            }
        }

        /**
         * A player's choice, validated the same way on every side; anything else is ignored. A client applies its
         * own choice at once, so the server re-sends the real state after every choice it receives, accepted or
         * not: of two players choosing at the same moment, the one whose choice lost sees the winner's at once.
         */
        public void choose(int choice){
            if(siteState != SiteState.investigated || !hasChoices() || !active() || (choice != chooseRestore && choice != chooseDismantle)){
                sync();
                return;
            }
            siteState = choice == chooseRestore ? SiteState.restoring : SiteState.dismantling;
            Events.fire(new SiteEvent(this));
            sync();
        }

        void sync(){
            if(net.server()) NetServer.syncBuilding(this);
        }

        public float restoration(){
            int total = total(restoration), have = 0;
            for(int i = 0; i < restoration.length; i++) have += Math.min(delivered[i], restoration[i].amount);
            return total == 0 ? 1f : have / (float)total;
        }

        public int salvageRemaining(){
            int sum = 0;
            for(int s : salvageLeft) sum += s;
            return sum;
        }

        /** Restoration items this site still needs (0 outside the restoring state). */
        public int remaining(Item item){
            if(siteState != SiteState.restoring) return 0;
            for(int i = 0; i < restoration.length; i++){
                if(restoration[i].item == item) return Math.max(restoration[i].amount - delivered[i], 0);
            }
            return 0;
        }

        boolean restores(Item item){
            for(ItemStack s : restoration) if(s.item == item) return true;
            return false;
        }

        boolean consumes(Item item){
            for(ItemStack s : recipe) if(s.item == item) return true;
            return false;
        }

        @Override
        public boolean shouldConsume(){
            if(!active()) return false;
            if(siteState == SiteState.dismantling) return items.total() < itemCapacity;
            if(siteState == SiteState.restored) return output != null && items.get(output.item) < itemCapacity && hasInputs();
            return false;
        }

        boolean hasInputs(){
            for(ItemStack s : recipe) if(items.get(s.item) < s.amount) return false;
            return true;
        }

        @Override
        public float efficiencyScale(){
            //the restored machine feeds on the basin's resonance like the other resonance machines
            return siteState == SiteState.restored ? Vesper.activity.resonance(this) : 1f;
        }

        @Override
        public float activity(){
            return siteState == SiteState.restored && active() ? runningActivity * Mathf.clamp(efficiency) : 0f;
        }

        @Override
        public void updateTile(){
            if(!active()) return;
            if(siteState == SiteState.dismantling){
                dismantle = Math.min(1f, dismantle + edelta() / dismantleTime);
                //release the salvage in step with the work, only on the server (clients get the items with the snapshots)
                if(!net.client()) release();
                if(Mathf.chanceDelta(0.06 * efficiency)) Fx.hitBulletSmall.at(x + Mathf.range(size * 3f), y + Mathf.range(size * 3f));
                dumpAll();
                if(!net.client() && salvageRemaining() == 0 && items.total() == 0){
                    siteState = SiteState.dismantled;
                    Fx.breakBlock.at(x, y, size);
                    Events.fire(new SiteEvent(this));
                    sync();
                }
            }else if(siteState == SiteState.restored){
                if(efficiency > 0f){
                    progress += getProgressIncrease(craftTime);
                    rotation += edelta() * 1.6f;
                }
                if(progress >= 1f){
                    progress %= 1f;
                    for(ItemStack s : recipe) items.remove(s.item, s.amount);
                    if(output != null) items.add(output.item, output.amount);
                    VFx.recovererCraft.at(x, y, 0f, glowColor);
                }
                if(output != null) dump(output.item);
            }
            working = Mathf.approachDelta(working, siteState == SiteState.restored ? Mathf.clamp(efficiency) : 0f, 0.02f);
            float target = switch(siteState){
                case unexplored -> 0.25f + investigation * 0.5f;
                case investigated -> 0.8f;
                case restoring -> 0.4f + restoration() * 0.6f;
                case restored -> 0.5f + working * 0.5f;
                case dismantling -> 0.3f * (1f - dismantle);
                case dismantled -> 0f;
            };
            glow = Mathf.approachDelta(glow, target, 0.01f);
        }

        /** Moves the salvage owed by the current progress into the store, while there is room; never more than the pool. */
        void release(){
            int total = total(salvage);
            int owed = (int)(dismantle * total) - (total - salvageRemaining());
            while(owed > 0 && items.total() < itemCapacity && salvageRemaining() > 0){
                //the stack with the most left first, so all kinds come out together
                int best = 0;
                for(int i = 1; i < salvageLeft.length; i++) if(salvageLeft[i] > salvageLeft[best]) best = i;
                salvageLeft[best]--;
                items.add(salvage[best].item, 1);
                owed--;
            }
        }

        void dumpAll(){
            for(ItemStack s : salvage) if(items.has(s.item)) dump(s.item);
        }

        void unlock(Block block){
            //the map's whitelist decides what can be built: allowing the block changes this map only (saved with it, synced by the rules)
            var rules = mindustry.Vars.state.rules;
            if(rules.blockWhitelist) rules.bannedBlocks.add(block);
            else rules.bannedBlocks.remove(block);
            if(net.server()) Call.setRules(rules);
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(!active()) return false;
            //like the reactor: a mixed belt keeps flowing; the surplus burns
            if(siteState == SiteState.restoring) return restores(item);
            if(siteState == SiteState.restored) return consumes(item) && items.get(item) < itemCapacity;
            return false;
        }

        @Override
        public void handleItem(Building source, Item item){
            if(siteState == SiteState.restoring){
                if(remaining(item) > 0) deliver(item, 1);
                else if(Mathf.chance(0.3)) Fx.fuelburn.at(x + Mathf.range(size * 3f), y + Mathf.range(size * 3f));
                return;
            }
            super.handleItem(source, item);
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            if(!active() || (source != null && source.team() != team)) return 0;
            if(siteState == SiteState.restoring) return Math.min(remaining(item), amount);
            if(siteState == SiteState.restored && consumes(item)) return Math.min(itemCapacity - items.get(item), amount);
            return 0;
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            if(siteState == SiteState.restoring) deliver(item, amount);
            else super.handleStack(item, amount, source);
        }

        void deliver(Item item, int amount){
            for(int i = 0; i < restoration.length; i++){
                if(restoration[i].item == item) delivered[i] = Math.min(delivered[i] + amount, restoration[i].amount);
            }
            if(restoration() >= 1f && !net.client()){
                siteState = SiteState.restored;
                if(restoreSurge > 0f) Vesper.activity.surge(restoreSurge);
                VFx.restorationComplete.at(x, y, size * tilesize, glowColor);
                Events.fire(new SiteEvent(this));
                sync();
            }
        }

        @Override
        public boolean canPickup(){
            return false;
        }

        @Override
        public void damage(float damage){
            //an ancient structure: the war cannot scratch it
        }

        @Override
        public boolean shouldShowConfigure(Player player){
            return active();
        }

        @Override
        public void buildConfiguration(Table table){
            SiteState built = siteState;
            table.background(Styles.black6);
            table.margin(8f);
            table.table(content -> {
                content.defaults().left().width(360f);
                content.label(this::infoText).wrap();
                if(siteState == SiteState.investigated && hasChoices()){
                    content.row();
                    content.table(t -> {
                        t.name = "vesper-site-choices";
                        t.defaults().size(176f, 46f).pad(2f);
                        t.button(Core.bundle.get("vesper.site.restore"), Icon.wrench, Styles.flatt, () -> ui.showConfirm("@vesper.site.restore",
                            Core.bundle.format("vesper.site.restore.confirm", requirements(restoration), effect()), () -> configure(chooseRestore)));
                        t.button(Core.bundle.get("vesper.site.dismantle"), Icon.hammer, Styles.flatt, () -> ui.showConfirm("@vesper.site.dismantle",
                            Core.bundle.format("vesper.site.dismantle.confirm", requirements(salvage)), () -> configure(chooseDismantle)));
                    });
                }
            }).update(content -> {
                //a choice (this player's or another's) or the end of a phase changes what the panel offers: rebuild it
                if(siteState != built && control.input.config.getSelected() == this){
                    Core.app.post(() -> {
                        if(control.input.config.getSelected() == this) control.input.config.showConfig(this);
                    });
                }
            });
        }

        @Override
        public void updateTableAlign(Table table){
            super.updateTableAlign(table);
            //the panel opens under the machine, and the choice text is long: keep all of it on screen
            table.setPosition(Mathf.clamp(table.x, 0f, Math.max(Core.scene.getWidth() - table.getWidth(), 0f)),
                Mathf.clamp(table.y, 0f, Math.max(Core.scene.getHeight() - table.getHeight(), 0f)));
        }

        /** What the player knows: before the survey only what it takes to find out; after it, both options with their numbers. */
        public String infoText(){
            String key = "vesper.site." + block.name.replace("vesper-", "");
            return switch(siteState){
                case unexplored -> Core.bundle.format("vesper.site.unexplored", (int)(investigation * 100), circuitsToReveal(),
                    Strings.autoFixed(investigateSurge, 1));
                case investigated -> Core.bundle.get(key + ".purpose") + (hasChoices() ? "\n\n" + Core.bundle.format("vesper.site.choice",
                    requirements(restoration), effect(), requirements(salvage)) : "");
                case restoring -> Core.bundle.format("vesper.site.restoring", (int)(restoration() * 100), missing());
                case dismantling -> Core.bundle.format("vesper.site.dismantling", (int)(dismantle * 100));
                case restored -> Core.bundle.get(key + ".purpose");
                case dismantled -> Core.bundle.get("vesper.site.dismantled");
            };
        }

        /** Circuits a single survey rig spends to reveal what is left of the work. */
        public int circuitsToReveal(){
            return SurveyRig.circuitsFor(investigateTime * Math.max(complete - investigation, 0f));
        }

        String effect(){
            return output == null ? "" : Core.bundle.format("vesper.site.effect", requirements(recipe), output.amount + " " + output.item.localizedName,
                Strings.autoFixed(craftTime / 60f, 1), Strings.autoFixed(runningActivity, 1), Strings.autoFixed(restoreSurge, 1));
        }

        /** What the restoration still needs, item by item (what a player carrying it over wants to know). */
        String missing(){
            StringBuilder out = new StringBuilder();
            for(ItemStack s : restoration){
                int left = remaining(s.item);
                if(left > 0) out.append(out.length() == 0 ? "" : ", ").append(left).append(' ').append(s.item.localizedName);
            }
            return out.toString();
        }

        String requirements(ItemStack[] stacks){
            StringBuilder out = new StringBuilder();
            for(ItemStack s : stacks) out.append(out.length() == 0 ? "" : ", ").append(s.amount).append(' ').append(s.item.localizedName);
            return out.toString();
        }

        String barText(){
            return switch(siteState){
                case unexplored -> Core.bundle.format("vesper.site.bar.investigation", (int)(investigation * 100));
                case restoring -> Core.bundle.format("vesper.site.bar.restoration", (int)(restoration() * 100));
                case dismantling -> Core.bundle.format("vesper.site.bar.dismantle", (int)(dismantle * 100), salvageRemaining());
                default -> siteState.localized();
            };
        }

        float barFraction(){
            return switch(siteState){
                case unexplored -> investigation;
                case restoring -> restoration();
                case dismantling -> dismantle;
                case restored -> progress;
                case dismantled -> 0f;
                default -> 1f;
            };
        }

        @Override
        public void draw(){
            boolean restored = siteState == SiteState.restored;
            TextureRegion main = siteState == SiteState.dismantled ? dismantledRegion : restored ? restoredRegion : region;
            Draw.rect(main, x, y);
            if(siteState == SiteState.dismantling){
                //the shell comes apart: the husk shows through as the work goes on
                Draw.alpha(dismantle);
                Draw.rect(dismantledRegion, x, y);
                Draw.color();
            }else if(siteState == SiteState.restoring){
                //the machine fills back in as the materials arrive
                Draw.alpha(restoration() * 0.85f);
                Draw.rect(restoredRegion, x, y);
                Draw.color();
            }
            if(restored && rotatorRegion.found()) Drawf.spinSprite(rotatorRegion, x, y, rotation);
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.blend(Blending.additive);
            TextureRegion light = restored && restoredGlowRegion.found() ? restoredGlowRegion : glowRegion;
            if(light.found() && glow > 0.01f){
                float breath = restored ? 0.8f + Mathf.absin(Time.time, 10f, 0.2f) * working : 0.75f + Mathf.absin(Time.time + id * 11f, 22f, 0.25f);
                Draw.color(siteState == SiteState.unexplored ? VPal.industrial : glowColor, glow * breath);
                Draw.rect(light, x, y);
            }
            Draw.blend();
            //what can be surveyed or decided stands out from the builders' decorative ruins: a dashed outline
            if(active() && (siteState == SiteState.unexplored || siteState == SiteState.investigated)){
                Draw.z(Layer.blockOver);
                Drawf.dashSquare(siteState == SiteState.unexplored ? VPal.industrial : VPal.resonanceLight, x, y,
                    size * tilesize + 5f + Mathf.absin(Time.time + id * 7f, 12f, 1.5f));
                Draw.z(z + 0.01f);
            }
            //status lamp: amber unknown, resonance known or restored, dark husk
            if(lampRegion.found() && siteState != SiteState.dismantled){
                Draw.color(siteState == SiteState.unexplored ? VPal.industrial : siteState == SiteState.dismantling ? VPal.dangerLight : VPal.resonanceLight);
                Draw.rect(lampRegion, x - size * tilesize * 0.32f, y + size * tilesize * 0.32f);
            }
            Draw.color();
            Draw.z(z);
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, size * tilesize * (0.8f + glow), glowColor, 0.6f * glow);
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.b(siteState.ordinal());
            write.f(investigation);
            write.f(dismantle);
            write.f(progress);
            write.b(salvageLeft.length);
            for(int s : salvageLeft) write.i(s);
            write.b(delivered.length);
            for(int d : delivered) write.i(d);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision < 1) return;
            siteState = SiteState.values()[Mathf.clamp(read.b(), 0, SiteState.values().length - 1)];
            investigation = read.f();
            dismantle = read.f();
            progress = read.f();
            int count = read.b();
            for(int i = 0; i < count; i++){
                int s = read.i();
                if(i < salvageLeft.length) salvageLeft[i] = s;
            }
            count = read.b();
            for(int i = 0; i < count; i++){
                int d = read.i();
                if(i < delivered.length) delivered[i] = d;
            }
        }
    }

    /** Fired on the server (and locally) when a site changes state, for missions and the HUD. */
    public static class SiteEvent{
        public final SiteBuild site;

        public SiteEvent(SiteBuild site){
            this.site = site;
        }
    }
}
