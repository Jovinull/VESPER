package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import vesper.*;
import vesper.content.*;
import vesper.graphics.*;
import vesper.world.SignalNode.*;

import static mindustry.Vars.*;

/**
 * The control terminal of the deep network: an ancient installation surveyed like the others (a {@link SurveyRig}
 * nearby, circuits per sample), which reveals the map's {@link SignalNode relay nodes}. Once every node is
 * operational, a player starts the synchronization here; it advances only while all of them stay operational at
 * once, and takes {@link #syncTime} ticks of that at x1, faster the more the basin resonates (the resonance multiplier
 * of the other resonance machines: a louder basin synchronizes sooner, and answers more). An interruption costs nothing for {@link #grace} ticks; past that
 * the progress slips back slowly ({@link #regress} per second) but never below the last quarter it reached, and it
 * resumes as soon as every node runs again. Completing it is the scenario's goal and happens once. The server
 * decides the start, the progress and the end; clients predict the progress and get every transition at once.
 */
public class SignalTerminal extends AncientSite{
    /** Ticks of synchronization with every node operational; grace before an interruption costs progress; progress lost per second after it. */
    public float syncTime = 150f * 60f, grace = 8f * 60f, regress = 0.01f;
    /** Relays the protocol addresses: with fewer on the map (one removed), it can neither start nor advance. */
    public int relays = 3;

    public enum SyncState{
        idle, syncing, synced;

        public String localized(){
            return Core.bundle.get("vesper.terminal.sync." + name());
        }
    }

    public SignalTerminal(String name){
        super(name);
        //the start of the synchronization; validated the same way on every side
        config(Boolean.class, (TerminalBuild build, Boolean start) -> {
            if(start) build.start();
        });
    }

    /** The control terminal of a team in the current world, if any. */
    public static @Nullable TerminalBuild find(Team team){
        var list = team.data().getBuildings(VBlocks.signalTerminal);
        return list.isEmpty() || !(list.first() instanceof TerminalBuild t) ? null : t;
    }

    /** Fired on the server (and in single player) when the network is synchronized, once. */
    public static class SyncEvent{
        public final TerminalBuild terminal;

        public SyncEvent(TerminalBuild terminal){
            this.terminal = terminal;
        }
    }

    public class TerminalBuild extends SiteBuild{
        public SyncState syncState = SyncState.idle;
        /** Synchronization progress (0-1), the last quarter reached, ticks of the current interruption, interruptions so far. */
        public float progress, checkpoint, interrupted;
        public int interruptions;
        /** Smoothed visuals. */
        public float syncGlow;

        /** Ticks an interruption costs nothing. */
        public float grace(){
            return grace;
        }

        /** How fast the synchronization advances: the basin's resonance (x1 up to activity 30, x2 at 100). */
        public float rate(){
            return Vesper.activity.resonanceMultiplier();
        }

        public Seq<NodeBuild> nodes(){
            Seq<NodeBuild> out = new Seq<>();
            for(Building b : team.data().getBuildings(VBlocks.signalNode)) if(b instanceof NodeBuild n) out.add(n);
            return out;
        }

        public int operational(){
            return nodes().count(NodeBuild::operational);
        }

        /** Whether every relay the protocol addresses is on the map and operational. */
        public boolean allOperational(){
            var nodes = nodes();
            return nodes.size >= relays && nodes.allMatch(NodeBuild::operational);
        }

        /**
         * Resonance cores a whole synchronization burns with no interruption: every relay's upkeep, and the energy of its
         * power as a resonance dynamo delivers it. What a player should have in stock (or on the belts) before starting.
         */
        public int coresPerSync(){
            float energy = VBlocks.resonanceDynamo instanceof ResonanceDynamo d ? d.powerProduction * d.itemDuration : 1500f;
            float time = syncSeconds() * 60f, sum = 0f;
            for(NodeBuild n : nodes()){
                SignalNode relay = (SignalNode)n.block;
                sum += time / relay.upkeepTime + (relay.consPower == null ? 0f : relay.consPower.usage * time / energy);
            }
            return Mathf.ceil(sum);
        }

        /** Seconds a whole synchronization takes at the basin's current resonance, with no interruption. */
        public float syncSeconds(){
            return syncTime / rate() / 60f;
        }

        /** Whether a synchronization can start now: surveyed, not started yet, and every relay operational. */
        public boolean canStart(){
            return siteState != SiteState.unexplored && syncState == SyncState.idle && allOperational();
        }

        void start(){
            if(!canStart()){
                sync();
                return;
            }
            syncState = SyncState.syncing;
            progress = checkpoint = interrupted = 0f;
            Events.fire(new SiteEvent(this));
            sync();
        }

        @Override
        public void updateTile(){
            super.updateTile();
            if(syncState == SyncState.syncing){
                if(allOperational()){
                    progress = Math.min(progress + Time.delta * rate() / syncTime, net.client() ? 0.999f : 1f);
                    checkpoint = Math.max(checkpoint, Mathf.floor(progress * 4f) / 4f);
                    interrupted = 0f;
                }else{
                    if(interrupted <= 0f && !net.client()) interruptions++;
                    interrupted += Time.delta;
                    if(interrupted > grace) progress = Math.max(checkpoint, progress - regress * Time.delta / 60f);
                }
                if(progress >= 1f && !net.client()){
                    syncState = SyncState.synced;
                    progress = 1f;
                    VFx.signalSynced.at(x, y, size * tilesize);
                    Events.fire(new SyncEvent(this));
                    Events.fire(new SiteEvent(this));
                    sync();
                }
            }
            syncGlow = Mathf.approachDelta(syncGlow, syncState == SyncState.synced ? 1f : syncState == SyncState.syncing ? 0.4f + progress * 0.6f : 0f, 1f / 60f);
        }

        /** What a player needs: the purpose, every node's state, and what the synchronization still needs. */
        @Override
        public String infoText(){
            if(siteState == SiteState.unexplored) return super.infoText();
            StringBuilder out = new StringBuilder(Core.bundle.get("vesper.site.signal-terminal.purpose"));
            out.append("\n");
            int i = 1;
            for(NodeBuild n : nodes()){
                out.append("\n").append(Core.bundle.format("vesper.terminal.node", i++, n.label(), (int)(n.power.status * 100f), n.items.get(VItems.resonanceCore)));
            }
            int missing = relays - nodes().size;
            if(missing > 0) out.append("\n").append(Core.bundle.format("vesper.terminal.missing", missing, relays));
            out.append("\n\n").append(switch(syncState){
                case idle -> (canStart() ? Core.bundle.get("vesper.terminal.ready") : Core.bundle.get("vesper.terminal.waiting")) + "\n"
                    + Core.bundle.format("vesper.terminal.cost", (int)syncSeconds(), coresPerSync()) + "\n"
                    + Core.bundle.format("vesper.terminal.resonance", Strings.fixed(rate(), 2));
                case syncing -> Core.bundle.format("vesper.terminal.syncing", (int)(progress * 100f), (int)(checkpoint * 100f), interrupted > 0f
                    ? Core.bundle.format("vesper.terminal.interrupted", Strings.autoFixed(Math.max(0f, grace - interrupted) / 60f, 0)) : "") + "\n"
                    + Core.bundle.format("vesper.terminal.resonance", Strings.fixed(rate(), 2));
                case synced -> Core.bundle.get("vesper.terminal.done");
            });
            return out.toString();
        }

        @Override
        String barText(){
            return switch(syncState){
                case syncing -> Core.bundle.format("vesper.terminal.bar", (int)(progress * 100f));
                case synced -> syncState.localized();
                default -> super.barText();
            };
        }

        @Override
        float barFraction(){
            return syncState == SyncState.idle ? super.barFraction() : progress;
        }

        @Override
        public void buildConfiguration(Table table){
            SyncState built = syncState;
            table.background(Styles.black6);
            table.margin(8f);
            table.table(content -> {
                content.defaults().left().width(380f);
                content.label(this::infoText).wrap();
                if(siteState != SiteState.unexplored && syncState == SyncState.idle){
                    content.row();
                    content.button(Core.bundle.get("vesper.terminal.start"), Icon.play, Styles.flatt, () -> configure(true)).height(46f).disabled(b -> !canStart())
                        .name("vesper-terminal-start");
                }
            }).update(content -> {
                if(syncState != built && control.input.config.getSelected() == this){
                    Core.app.post(() -> {
                        if(control.input.config.getSelected() == this) control.input.config.showConfig(this);
                    });
                }
            });
        }

        @Override
        public void draw(){
            super.draw();
            if(syncGlow > 0.01f){
                float z = Draw.z();
                Draw.z(Layer.effect - 1f);
                float r = size * tilesize * 0.75f;
                //the progress as an arc around the terminal, full and breathing once the network answers
                Lines.stroke(2f, Tmp.c1.set(VPal.resonanceLight).a(0.25f + 0.5f * syncGlow));
                Lines.arc(x, y, r, syncState == SyncState.synced ? 1f : progress, 90f);
                if(syncState == SyncState.synced){
                    Lines.stroke(1f, Tmp.c1.set(VPal.resonanceLight).a(0.3f * Mathf.absin(Time.time, 10f, 1f)));
                    Lines.circle(x, y, r + 4f + Mathf.absin(Time.time, 10f, 3f));
                }else if(interrupted > 0f){
                    Lines.stroke(1.5f, Tmp.c1.set(VPal.danger).a(0.6f));
                    Lines.arc(x, y, r + 3f, Math.max(0f, 1f - interrupted / grace), 90f);
                }
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
            write.b(syncState.ordinal());
            write.f(progress);
            write.f(checkpoint);
            write.f(interrupted);
            write.i(interruptions);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            //revision 1 is the site's own state
            if(revision >= 2){
                syncState = SyncState.values()[Mathf.clamp(read.b(), 0, SyncState.values().length - 1)];
                progress = read.f();
                checkpoint = read.f();
                interrupted = read.f();
                interruptions = read.i();
            }
        }
    }
}
