package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import vesper.*;
import vesper.content.*;
import vesper.graphics.*;
import vesper.world.ContainmentLock.*;
import vesper.world.ProtocolSite.*;

import static mindustry.Vars.*;

/**
 * The Central Controller of the Central Control complex: an ancient installation surveyed like the others, which
 * still runs two industrial protocols over the complex's own conduits. It is one consumer on the player's grid: its
 * demand is a standby load, the {@link ContainmentLock locks} switched on (the containment bus) and the recovery
 * protocol when it has work (the recovery bus). What the grid actually delivers is split between the two buses by
 * the routing mode the player picks:
 * <ul>
 * <li><b>containment</b>: the locks are served first, recovery gets what is left;</li>
 * <li><b>balanced</b>: both in proportion to their demand (the locks in order, so a short bus seals some of them);</li>
 * <li><b>recovery</b>: recovery first, overdriven to {@link #overdrivePower} times its demand (the extra share works
 * at {@link #overdriveSpeed}, less efficiently, and disturbs the basin more); the locks get what is left.</li>
 * </ul>
 * A new mode swings the routing over {@link #rampTime} ticks, and a lock takes its own time to close: changing the
 * routing is never instant. The recovery bus restores the complex's {@link ProtocolSite sites} whose materials are in
 * (the junction first, then the subsystems in the order their materials were complete), and finally runs the
 * reactivation sequence, which only advances while both subsystems are operational. The protocols work at the
 * controller's own pace: the basin's resonance speeds the machines they restore, not the work itself. Recovery pays one resonance core
 * per {@link #recoveryCoreTime} ticks of full share, each lock one per its own upkeep, from the controller's store
 * (cores arrive by belt or unit). The server decides the mode, the start and the end; clients predict the rest.
 */
public class ProtocolController extends AncientSite{
    /** Power: the standby load once surveyed, the recovery bus at full share, the overdrive factor and the speed of the overdriven share. */
    public float standbyPower = 0.5f, recoveryPower = 3f, overdrivePower = 1.5f, overdriveSpeed = 1.4f;
    /** Ticks of full-share recovery work one core pays for; ticks the routing takes to swing to a new mode. */
    public float recoveryCoreTime = 10f * 60f, rampTime = 5f * 60f;
    /** Ticks of full-share work of the reactivation sequence. */
    public float sequenceWork = 180f * 60f;
    /** Activity of the recovery protocol at full share, of each share beyond it (the overdrive is loud), and of the sequence on top. */
    public float recoveryActivity = 6f, overdriveActivity = 12f, sequenceActivity = 8f;
    public Item fuel = VItems.resonanceCore;
    /** The complex's site blocks the controller drives; filled by content. */
    public final Seq<Block> sites = new Seq<>();
    public TextureRegion rotatorRegion, topRegion;

    public enum Mode{
        containment, balanced, recovery;

        public static final Mode[] all = values();

        public String localized(){
            return Core.bundle.get("vesper.protocol.mode." + name());
        }
    }

    public enum Sequence{
        idle, running, complete
    }

    public ProtocolController(String name){
        super(name);
        hasPower = true;
        itemCapacity = 30;
        consumePowerDynamic((ControllerBuild b) -> b.demand());
        config(Integer.class, (ControllerBuild build, Integer mode) -> build.route(mode));
        config(Boolean.class, (ControllerBuild build, Boolean start) -> {
            if(start) build.start();
        });
    }

    @Override
    public void load(){
        super.load();
        rotatorRegion = Core.atlas.find(name + "-rotator");
        topRegion = Core.atlas.find(name + "-top");
    }

    /** The controller of a team in the current world, if any. */
    public static @Nullable ControllerBuild find(Team team){
        var list = team.data().getBuildings(VBlocks.centralController);
        return list.isEmpty() || !(list.first() instanceof ControllerBuild c) ? null : c;
    }

    /** Fired on the server (and in single player) once, when the reactivation sequence completes. */
    public static class ReactivationEvent{
        public final ControllerBuild controller;

        public ReactivationEvent(ControllerBuild controller){
            this.controller = controller;
        }
    }

    public class ControllerBuild extends SiteBuild{
        public Mode mode = Mode.balanced;
        public Sequence sequence = Sequence.idle;
        /** Reactivation progress (0-1). */
        public float progress;
        /** Power routed to containment this tick (smoothed), and to recovery; recovery work this tick (ticks at full share). */
        public float toContainment, toRecovery, worked;
        /** Recovery work the current core still pays for (ticks at full share); cores burned by each bus (saved). */
        public float paid;
        public int recoveryCores, lockCores, modeChanges;
        /** Seconds the sequence stood still (a subsystem stopped), and how often it did. */
        public float stalled;
        public int stalls;
        /** Every vent was shut at once at least one time (the objective before the sequence; saved). */
        public boolean containedOnce;
        /** Smoothed visuals. */
        public float spin, containmentGlow, recoveryGlow;
        boolean wasStalled;
        /** The locks and sites, gathered once per update (the power graph asks for the demand every tick). */
        final Seq<LockBuild> locks = new Seq<>();
        final Seq<ProtocolSiteBuild> siteList = new Seq<>();
        long gathered = -1;

        void gather(){
            if(gathered == state.updateId) return;
            gathered = state.updateId;
            locks.clear();
            for(Building b : team.data().getBuildings(VBlocks.containmentLock)) if(b instanceof LockBuild l) locks.add(l);
            //the order the bus serves them: as the map lists them, left to right, bottom to top
            locks.sort(l -> l.pos());
            siteList.clear();
            for(Block block : sites) for(Building b : team.data().getBuildings(block)) if(b instanceof ProtocolSiteBuild s) siteList.add(s);
        }

        /** Surveyed: the protocols are known and the routing works. */
        public boolean known(){
            return siteState != SiteState.unexplored && active();
        }

        public Seq<LockBuild> locks(){
            gather();
            return locks;
        }

        public Seq<ProtocolSiteBuild> sites(){
            gather();
            return siteList;
        }

        public Seq<ProtocolSiteBuild> subsystems(){
            return sites().select(s -> ((ProtocolSite)s.block).subsystem);
        }

        /** The junction is restored: the buses reach the wings. */
        public boolean access(){
            var junction = sites().find(s -> !((ProtocolSite)s.block).subsystem);
            return junction != null && junction.siteState == SiteState.restored;
        }

        /** Every subsystem restored. */
        public boolean restored(){
            var subs = subsystems();
            return subs.size > 0 && subs.allMatch(s -> s.siteState == SiteState.restored);
        }

        /** Every subsystem restored and running for the complex (what the sequence needs). */
        public boolean integrity(){
            var subs = subsystems();
            return subs.size > 0 && subs.allMatch(ProtocolSiteBuild::operational);
        }

        /** Every vent shut. */
        public boolean contained(){
            var locks = locks();
            return locks.size > 0 && locks.allMatch(LockBuild::sealed);
        }

        /** What the recovery bus works on now: the sequence once it runs, else the ready site first in line (the junction, then by when its materials were in). */
        public @Nullable ProtocolSiteBuild target(){
            if(sequence != Sequence.idle) return null;
            ProtocolSiteBuild best = null;
            for(ProtocolSiteBuild s : sites()){
                if(!s.ready() || !s.accessible()) continue;
                boolean junction = !((ProtocolSite)s.block).subsystem;
                if(best == null || junction || (((ProtocolSite)best.block).subsystem && s.readyAt < best.readyAt)) best = s;
                if(junction) break;
            }
            return best;
        }

        /** Whether the recovery bus has work: a site to restore, or the sequence running (and not finished). */
        public boolean recovering(){
            return known() && (sequence == Sequence.running || target() != null);
        }

        public float containmentDemand(){
            if(!known()) return 0f;
            float sum = 0f;
            for(LockBuild l : locks()) if(l.online) sum += ((ContainmentLock)l.block).power;
            return sum;
        }

        public float recoveryDemand(){
            return recovering() ? recoveryPower * (mode == Mode.recovery ? overdrivePower : 1f) : 0f;
        }

        /** Power asked of the grid per tick. */
        public float demand(){
            return known() ? standbyPower + containmentDemand() + recoveryDemand() : 0f;
        }

        /** Power the grid delivers to the buses this tick (the standby load comes off the top). */
        public float delivered(){
            return power == null ? 0f : (containmentDemand() + recoveryDemand()) * power.status;
        }

        /** Share of the recovery bus at full demand (up to the overdrive factor in recovery mode). */
        public float recoveryShare(){
            return toRecovery / recoveryPower;
        }

        /** Speed of the recovery work from a share: the overdriven part beyond 1 works at the overdrive's lower efficiency. */
        public float speed(float share){
            return Math.min(share, 1f) + Math.max(share - 1f, 0f) * (overdriveSpeed - 1f) / Math.max(overdrivePower - 1f, 0.001f);
        }

        /** Whether the sequence can start: every objective before it done (subsystems restored, the vents shut once). */
        public boolean canStart(){
            return known() && sequence == Sequence.idle && restored() && containedOnce;
        }

        /** The item both buses burn: resonance cores. */
        public Item fuel(){
            return fuel;
        }

        public boolean takeCore(){
            if(!items.has(fuel)) return false;
            items.remove(fuel, 1);
            lockCores++;
            return true;
        }

        void route(int index){
            if(!known() || index < 0 || index >= Mode.all.length){
                sync();
                return;
            }
            Mode next = Mode.all[index];
            if(next != mode){
                mode = next;
                modeChanges++;
            }
            sync();
        }

        void start(){
            if(!canStart()){
                sync();
                return;
            }
            sequence = Sequence.running;
            progress = 0f;
            Events.fire(new SiteEvent(this));
            sync();
        }

        @Override
        public void updateTile(){
            super.updateTile();
            worked = 0f;
            if(!known()){
                toContainment = toRecovery = 0f;
                return;
            }
            float dc = containmentDemand(), dr = recoveryDemand(), delivered = delivered();
            float target = switch(mode){
                case containment -> Math.min(delivered, dc);
                case recovery -> delivered - Math.min(delivered, dr);
                case balanced -> dc + dr <= 0f ? 0f : delivered * dc / (dc + dr);
            };
            //the routing swings to a new split over rampTime, never at once
            toContainment = Mathf.clamp(Mathf.approach(toContainment, target, Math.max(dc + dr, 0.01f) / rampTime * Time.delta), 0f, Math.min(delivered, dc));
            toRecovery = Math.min(delivered - toContainment, dr);

            //the containment bus: each lock switched on in order gets its whole share or what is left of the bus
            float left = toContainment;
            for(LockBuild l : locks()){
                float need = ((ContainmentLock)l.block).power;
                if(!l.online){
                    l.supply(0f);
                    continue;
                }
                float give = Math.min(left, need);
                l.supply(give / need);
                left -= give;
            }

            if(!containedOnce && contained() && !net.client()){
                containedOnce = true;
                Events.fire(new SiteEvent(this));
                sync();
            }

            //the recovery bus: work for the site in line or the sequence, paid in cores
            float share = recoveryShare();
            if(share > 0.001f){
                boolean stall = sequence == Sequence.running && !integrity();
                if(stall){
                    stalled += Time.delta / 60f;
                    if(!wasStalled && !net.client()) stalls++;
                }else{
                    if(paid <= 0f && items.has(fuel)){
                        items.remove(fuel, 1);
                        recoveryCores++;
                        paid += recoveryCoreTime;
                    }
                    if(paid > 0f){
                        paid = Math.max(0f, paid - share * Time.delta);
                        worked = speed(share) * Time.delta;
                        var site = target();
                        if(site != null){
                            site.work(worked);
                        }else if(sequence == Sequence.running){
                            progress = Math.min(progress + worked / sequenceWork, net.client() ? 0.999f : 1f);
                        }
                    }
                }
                wasStalled = stall;
            }
            if(sequence == Sequence.running && progress >= 1f && !net.client()){
                sequence = Sequence.complete;
                VFx.signalSynced.at(x, y, size * tilesize);
                Events.fire(new ReactivationEvent(this));
                Events.fire(new SiteEvent(this));
                sync();
            }

            spin += Time.delta * (0.3f + share * 2f);
            containmentGlow = Mathf.approachDelta(containmentGlow, dc > 0f ? toContainment / dc : 0f, 1f / 40f);
            recoveryGlow = Mathf.approachDelta(recoveryGlow, worked > 0f ? Math.min(share, 1.5f) / 1.5f : 0f, 1f / 40f);
        }

        /** Why the recovery bus is not working, or null when it is. */
        public @Nullable String reason(){
            if(!known()) return null;
            if(sequence == Sequence.complete) return null;
            if(!recovering()) return Core.bundle.get(access() || sites().contains(s -> s.siteState == SiteState.restoring)
                ? "vesper.protocol.reason.materials" : "vesper.protocol.reason.nothing");
            if(toRecovery <= 0.001f) return Core.bundle.get(power.status < 0.05f ? "vesper.protocol.reason.power" : "vesper.protocol.reason.routing");
            if(paid <= 0f && !items.has(fuel)) return Core.bundle.get("vesper.protocol.reason.cores");
            if(sequence == Sequence.running && !integrity()){
                var stopped = subsystems().find(s -> !s.operational());
                return Core.bundle.format("vesper.protocol.reason.stalled", stopped == null ? "" : stopped.block.localizedName);
            }
            return null;
        }

        @Override
        public float activity(){
            if(!known() || sequence == Sequence.complete) return 0f;
            float share = Math.min(recoveryShare(), overdrivePower);
            return worked > 0f ? recoveryActivity * share + overdriveActivity * Math.max(share - 1f, 0f) + (sequence == Sequence.running ? sequenceActivity : 0f) : 0f;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return item == fuel && known() && items.get(fuel) < itemCapacity;
        }

        @Override
        public int acceptStack(Item item, int amount, Teamc source){
            if(item != fuel || !known() || (source != null && source.team() != team)) return 0;
            return Math.min(itemCapacity - items.get(fuel), amount);
        }

        @Override
        public void handleStack(Item item, int amount, Teamc source){
            items.add(item, amount);
        }

        @Override
        public void handleItem(Building source, Item item){
            items.add(item, 1);
        }

        @Override
        public String infoText(){
            if(!known()) return super.infoText();
            StringBuilder out = new StringBuilder(Core.bundle.get("vesper.site.central-controller.purpose")).append("\n\n");
            float dc = containmentDemand(), dr = recoveryDemand();
            out.append(Core.bundle.format("vesper.protocol.power", Strings.autoFixed(delivered() * 60f, 1), Strings.autoFixed((dc + dr) * 60f, 1),
                (int)(power.status * 100f))).append("\n");
            var locks = locks();
            out.append(Core.bundle.format("vesper.protocol.containment", locks.count(LockBuild::sealed), locks.size,
                Strings.autoFixed(toContainment * 60f, 1), Strings.autoFixed(lockCoresPerMinute(), 1))).append("\n");
            out.append(Core.bundle.format("vesper.protocol.recovery", (int)(recoveryShare() * 100f), Strings.autoFixed(toRecovery * 60f, 1),
                Strings.autoFixed(recoveryCoresPerMinute(), 1), Strings.autoFixed(activity(), 1))).append("\n");
            out.append(Core.bundle.format("vesper.protocol.store", items.get(fuel))).append("\n\n");
            for(ProtocolSiteBuild s : sites()) out.append(s.block.localizedName).append(": ").append(s.barText()).append("\n");
            if(sequence == Sequence.running) out.append("\n").append(Core.bundle.format("vesper.protocol.sequence", (int)(progress * 100f)));
            else if(sequence == Sequence.complete) out.append("\n").append(Core.bundle.get("vesper.protocol.sequence.complete"));
            String why = reason();
            if(why != null) out.append("\n").append(why);
            return out.toString();
        }

        /** Cores the locks burn per minute at the current seal. */
        public float lockCoresPerMinute(){
            float sum = 0f;
            for(LockBuild l : locks()) if(l.sealed()) sum += 3600f / ((ContainmentLock)l.block).upkeepTime;
            return sum;
        }

        /** Cores recovery burns per minute at the current share. */
        public float recoveryCoresPerMinute(){
            return worked > 0f ? recoveryShare() * 3600f / recoveryCoreTime : 0f;
        }

        @Override
        String barText(){
            return switch(sequence){
                case running -> Core.bundle.format("vesper.protocol.bar.sequence", (int)(progress * 100f));
                case complete -> Core.bundle.get("vesper.protocol.bar.complete");
                default -> known() ? Core.bundle.format("vesper.protocol.bar.mode", mode.localized()) : super.barText();
            };
        }

        @Override
        float barFraction(){
            return sequence == Sequence.idle ? super.barFraction() : progress;
        }

        @Override
        public void buildConfiguration(Table table){
            Mode builtMode = mode;
            Sequence builtSequence = sequence;
            boolean builtKnown = known();
            table.background(Styles.black6);
            table.margin(8f);
            table.table(content -> {
                content.defaults().left().width(400f);
                content.label(this::infoText).wrap();
                if(known() && sequence != Sequence.complete){
                    content.row();
                    content.table(modes -> {
                        modes.name = "vesper-protocol-modes";
                        modes.defaults().height(42f).growX().pad(2f);
                        for(Mode m : Mode.all){
                            modes.button(m.localized(), Styles.togglet, () -> configure(m.ordinal())).checked(b -> mode == m).name("vesper-mode-" + m.name());
                        }
                    }).growX();
                    content.row();
                    content.label(() -> Core.bundle.get("vesper.protocol.mode." + mode.name() + ".effect")).wrap().color(Color.lightGray);
                }
                if(known() && sequence == Sequence.idle){
                    content.row();
                    content.button(Core.bundle.get("vesper.protocol.start"), Icon.play, Styles.flatt, () -> configure(true)).height(46f).disabled(b -> !canStart())
                        .name("vesper-protocol-start");
                }
            }).update(content -> {
                if((mode != builtMode || sequence != builtSequence || known() != builtKnown) && control.input.config.getSelected() == this){
                    Core.app.post(() -> {
                        if(control.input.config.getSelected() == this) control.input.config.showConfig(this);
                    });
                }
            });
        }

        @Override
        public void draw(){
            super.draw();
            if(rotatorRegion.found()) Drawf.spinSprite(rotatorRegion, x, y, spin * 0.5f);
            if(topRegion.found()) Draw.rect(topRegion, x, y);
            if(!known()) return;
            float z = Draw.z();
            Draw.z(Layer.effect - 1f);
            float r = size * tilesize * 0.62f;
            //the two buses as arcs: cyan containment on the left half, amber recovery on the right, as bright as their share
            Lines.stroke(2f, Tmp.c1.set(VPal.resonanceLight).a(0.15f + 0.6f * containmentGlow));
            Lines.arc(x, y, r, 0.5f * Math.max(containmentGlow, 0.05f), 90f);
            Lines.stroke(2f, Tmp.c1.set(VPal.industrialLight).a(0.15f + 0.6f * recoveryGlow));
            Lines.arc(x, y, r, 0.5f * Math.max(recoveryGlow, 0.05f), 270f);
            //the selected mode: a lamp at the top, its colour the protocol it favours
            Draw.z(z + 0.02f);
            Draw.color(mode == Mode.containment ? VPal.resonanceLight : mode == Mode.recovery ? VPal.industrialLight : Color.white);
            Fill.circle(x, y + size * tilesize * 0.38f, 1.6f);
            if(sequence == Sequence.running){
                Draw.z(Layer.effect - 1f);
                Lines.stroke(1.5f, Tmp.c1.set(integrity() ? VPal.resonanceLight : VPal.danger).a(0.7f));
                Lines.arc(x, y, r + 4f, progress, 90f);
            }else if(sequence == Sequence.complete){
                Draw.z(Layer.effect - 1f);
                Lines.stroke(1f, Tmp.c1.set(VPal.resonanceLight).a(0.3f * Mathf.absin(Time.time, 10f, 1f)));
                Lines.circle(x, y, r + 4f + Mathf.absin(Time.time, 10f, 3f));
            }
            Draw.color();
            Draw.z(z);
        }

        void sync(){
            if(net.server()) NetServer.syncBuilding(this);
        }

        @Override
        public Integer config(){
            return mode.ordinal();
        }

        @Override
        public byte version(){
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.b(mode.ordinal());
            write.b(sequence.ordinal());
            write.f(progress);
            write.f(toContainment);
            write.f(paid);
            write.i(recoveryCores);
            write.i(lockCores);
            write.i(modeChanges);
            write.f(stalled);
            write.i(stalls);
            write.bool(containedOnce);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                mode = Mode.all[Mathf.clamp(read.b(), 0, Mode.all.length - 1)];
                sequence = Sequence.values()[Mathf.clamp(read.b(), 0, Sequence.values().length - 1)];
                progress = read.f();
                toContainment = read.f();
                paid = read.f();
                recoveryCores = read.i();
                lockCores = read.i();
                modeChanges = read.i();
                stalled = read.f();
                stalls = read.i();
                containedOnce = read.bool();
            }
        }
    }
}
