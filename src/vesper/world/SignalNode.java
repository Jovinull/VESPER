package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.core.*;
import mindustry.entities.*;
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
import vesper.world.SignalTerminal.*;

import static mindustry.Vars.*;

/**
 * A relay node of the deep network, placed by a map on the player's team. It stays dark until the map's control
 * terminal is surveyed; then, switched on, it needs power (from its own grid) and resonance cores (one every
 * {@link #upkeepTime} ticks, paid when each period starts) to be operational. While the terminal synchronizes, every
 * node must stay operational: a resonance pulse that reaches a node (unless a containment field that absorbed it
 * covers the node), enemy fire on it (it jams for {@link #jamTime} ticks), lost power or no cores interrupt it.
 * The builders made it to last: it takes no damage and cannot be dismantled, and nothing can be taken out of it.
 * Working, it disturbs the basin, twice as much while synchronizing. The server decides every state; clients get
 * the transitions at once.
 */
public class SignalNode extends Block{
    /** Ticks of operation one resonance core pays for; ticks a hit jams it; power share it needs to count as powered. */
    public float upkeepTime = 30f * 60f, jamTime = 4f * 60f, minPower = 0.9f;
    /** Activity while operational, and while operational during a synchronization. */
    public float operatingActivity = 4f, syncActivity = 8f;
    public Item fuel = VItems.resonanceCore;
    public Color signalColor = VPal.resonanceLight;
    public TextureRegion glowRegion, rotatorRegion, lampRegion;

    public enum NodeState{
        unknown, offline, unpowered, starved, jammed, interfered, operational, synced, interrupted;

        public String localized(){
            return Core.bundle.get("vesper.node.state." + name());
        }
    }

    public SignalNode(String name){
        super(name);
        update = true;
        solid = true;
        sync = true;
        destructible = true;
        breakable = false;
        rebuildable = false;
        targetable = true;
        configurable = true;
        saveConfig = true;
        hasItems = true;
        hasPower = true;
        itemCapacity = 10;
        unloadable = false;
        emitLight = true;
        envEnabled = Env.any;

        config(Boolean.class, (NodeBuild build, Boolean online) -> build.online = online);
    }

    @Override
    public void load(){
        super.load();
        glowRegion = Core.atlas.find(name + "-glow");
        rotatorRegion = Core.atlas.find(name + "-rotator");
        lampRegion = Core.atlas.find("vesper-activity-indicator");
    }

    @Override
    public boolean canBreak(Tile tile){
        //the network's relays: deconstructing one would refund what it never cost and end the scenario
        return false;
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(VStats.signal, Core.bundle.format("vesper.stat.signal", Strings.autoFixed(upkeepTime / 60f, 1), Strings.autoFixed(operatingActivity, 1),
            Strings.autoFixed(syncActivity, 1)));
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("items");
        addBar("node", (NodeBuild b) -> new Bar(b::label, () -> switch(b.state()){
            case operational, synced -> VPal.resonanceLight;
            case interrupted, jammed, interfered -> VPal.dangerLight;
            case unknown, offline -> Color.gray;
            default -> VPal.industrial;
        }, () -> b.operational() ? 1f : 0f));
        addBar("upkeep", (NodeBuild b) -> new Bar(() -> Core.bundle.format("vesper.node.fuel", b.items.get(fuel), (int)(b.upkeep / 60f)), () -> VPal.resonance,
            () -> b.items.get(fuel) / (float)itemCapacity));
    }

    public class NodeBuild extends Building implements ActivitySource{
        /** Switched on by the player (saved, and kept on copy). */
        public boolean online = true;
        /** Ticks of operation already paid for; ticks left of a jam; cores burned since the map began (saved). */
        public float upkeep, jammed;
        public int coresSpent;
        /** Smoothed visuals. */
        public float warmup, spin, alarm;

        /** The terminal of this map (null when there is none: a node placed in another map does nothing). */
        public @Nullable TerminalBuild terminal(){
            return SignalTerminal.find(team);
        }

        /** Revealed by the terminal's survey: before it, the node is a dark ruin and costs nothing. */
        public boolean known(){
            var t = terminal();
            return t != null && t.siteState != AncientSite.SiteState.unexplored;
        }

        public boolean powered(){
            return power != null && power.status >= minPower;
        }

        /** Every requirement met: known, on, powered, paid for, not jammed, no pulse interfering. */
        public boolean operational(){
            return known() && online && powered() && (upkeep > 0f || items.has(fuel)) && jammed <= 0f && !Vesper.activity.interferes(this);
        }

        public boolean syncing(){
            var t = terminal();
            return t != null && t.syncState == SignalTerminal.SyncState.syncing;
        }

        public NodeState state(){
            var t = terminal();
            if(!known()) return NodeState.unknown;
            if(t != null && t.syncState == SignalTerminal.SyncState.synced) return NodeState.synced;
            if(operational()) return syncing() ? NodeState.synced : NodeState.operational;
            //what stops it, in the order a player fixes it
            NodeState reason = !online ? NodeState.offline : !powered() ? NodeState.unpowered : !(upkeep > 0f || items.has(fuel)) ? NodeState.starved
                : jammed > 0f ? NodeState.jammed : NodeState.interfered;
            return syncing() ? NodeState.interrupted : reason;
        }

        /** The state as a player reads it: an interruption says what interrupts it. */
        public String label(){
            NodeState s = state();
            return s == NodeState.interrupted ? Core.bundle.format("vesper.node.state.interrupted", reason().localized()) : s.localized();
        }

        /** Why it is not operational (the same as {@link #state} outside a synchronization). */
        public NodeState reason(){
            if(operational() || !known()) return state();
            return !online ? NodeState.offline : !powered() ? NodeState.unpowered : !(upkeep > 0f || items.has(fuel)) ? NodeState.starved
                : jammed > 0f ? NodeState.jammed : NodeState.interfered;
        }

        @Override
        public boolean shouldConsume(){
            return known() && online;
        }

        @Override
        public float activity(){
            return operational() ? syncing() ? syncActivity : operatingActivity : 0f;
        }

        @Override
        public void updateTile(){
            jammed = Math.max(0f, jammed - Time.delta);
            boolean was = alarm > 0.5f;
            if(known() && online && powered() && jammed <= 0f && !Vesper.activity.interferes(this)){
                //pay first, so every tick of operation is paid for; a terminal that finished its work needs nothing more
                var t = terminal();
                boolean done = t != null && t.syncState == SignalTerminal.SyncState.synced;
                if(!done){
                    if(upkeep <= 0f && items.has(fuel)){
                        items.remove(fuel, 1);
                        coresSpent++;
                        upkeep += upkeepTime;
                    }
                    if(upkeep > 0f) upkeep = Math.max(0f, upkeep - Time.delta);
                }
            }
            boolean operational = operational();
            warmup = Mathf.approachDelta(warmup, operational ? 1f : known() ? 0.15f : 0f, 1f / 60f);
            alarm = Mathf.approachDelta(alarm, syncing() && !operational ? 1f : 0f, 1f / 20f);
            spin += warmup * Time.delta * (syncing() ? 3f : 1f);
            if(!was && alarm > 0.5f && !headless) VFx.signalInterference.at(x, y, size * tilesize, VPal.danger);
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return item == fuel && known() && items.get(fuel) < itemCapacity;
        }

        @Override
        public void damage(float damage){
            //the builders' relay takes no damage, but fire on it jams it for a while
            if(!net.client()){
                boolean fresh = jammed <= 0f;
                jammed = jamTime;
                if(fresh) sync();
            }
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

        void sync(){
            if(net.server()) NetServer.syncBuilding(this);
        }

        @Override
        public void draw(){
            Draw.rect(block.region, x, y);
            if(rotatorRegion.found()) Drawf.spinSprite(rotatorRegion, x, y, spin * 0.6f);
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.blend(Blending.additive);
            if(glowRegion.found() && warmup > 0.01f){
                Color c = alarm > 0.01f ? Tmp.c1.set(signalColor).lerp(VPal.danger, alarm) : signalColor;
                float flicker = alarm > 0.01f ? Mathf.absin(Time.time, 2f, 0.5f) : Mathf.absin(Time.time + id * 9f, 14f, 0.2f);
                Draw.color(c, warmup * (0.7f + flicker));
                Draw.rect(glowRegion, x, y);
            }
            Draw.blend();
            if(lampRegion.found() && known()){
                NodeState s = state();
                Draw.color(s == NodeState.operational || s == NodeState.synced ? VPal.resonanceLight : s == NodeState.offline ? Pal.darkerMetal
                    : s == NodeState.interrupted || s == NodeState.jammed || s == NodeState.interfered ? Tmp.c2.set(VPal.danger).a(0.5f + Mathf.absin(Time.time, 3f, 0.5f))
                    : Tmp.c2.set(VPal.industrial).a(0.6f + Mathf.absin(Time.time, 6f, 0.4f)));
                Draw.rect(lampRegion, x - size * tilesize * 0.32f, y + size * tilesize * 0.32f);
            }
            //while the network synchronizes, a beam from every working node towards the terminal
            var t = terminal();
            if(t != null && syncing() && warmup > 0.3f && alarm < 0.5f){
                Draw.z(Layer.effect - 1f);
                Lines.stroke(1.4f, Tmp.c1.set(signalColor).a(0.35f * warmup));
                float a = Angles.angle(x, y, t.x, t.y), len = Math.min(dst(t), 6f * tilesize + Mathf.absin(Time.time + id, 8f, tilesize));
                Lines.lineAngle(x, y, a, len);
            }
            Draw.color();
            Draw.z(z);
        }

        @Override
        public void drawLight(){
            Drawf.light(x, y, (30f + 40f * warmup) * size / 3f, alarm > 0.5f ? VPal.danger : signalColor, 0.2f + 0.6f * warmup);
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
            write.f(jammed);
            write.i(coresSpent);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                online = read.bool();
                upkeep = read.f();
                jammed = read.f();
                coresSpent = read.i();
            }
        }
    }
}
