package vesper.harness;

import arc.*;
import arc.struct.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.power.ConsumeGenerator.*;
import mindustry.world.blocks.production.Drill.*;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.GenericCrafter.*;
import vesper.content.*;
import vesper.world.*;
import vesper.world.ContainmentField.*;
import vesper.world.ResonanceDynamo.*;
import vesper.world.SignalNode.*;
import vesper.world.SurveyRig.*;

import static mindustry.Vars.*;

/**
 * The item accounts of a playthrough, for the economy audits: what the map started with, what construction took
 * (rebuilds included), what the machines made and burned, the lowest stock the core fell to and what is left.
 * Production is counted where it happens: a crafter finishing a craft, a drill handing over an ore, a generator
 * taking in fuel, a rig paying for a sample. Resonance cores are also balanced by conservation, as a check that no
 * sink was missed. Snapshots ({@link #mark}) record the core and the counts at named moments of the game.
 */
public class Ledger{
    public final ObjectIntMap<Item> start = new ObjectIntMap<>(), built = new ObjectIntMap<>(), made = new ObjectIntMap<>(), burned = new ObjectIntMap<>(),
        low = new ObjectIntMap<>();
    public final OrderedMap<String, String> marks = new OrderedMap<>();
    /** What each kind of machine made (crafts of crafters and of restored ancient sites). */
    public final ObjectIntMap<mindustry.world.Block> madeBy = new ObjectIntMap<>();
    final IntFloatMap last = new IntFloatMap();
    public boolean active;

    public Ledger(){
        Events.on(BlockBuildEndEvent.class, e -> {
            if(!active || e.breaking || e.team != state.rules.defaultTeam || e.tile.build == null) return;
            for(ItemStack s : e.tile.block().requirements) built.increment(s.item, Math.round(s.amount * state.rules.buildCostMultiplier));
        });
        Events.run(Trigger.update, this::update);
        Events.on(ResetEvent.class, e -> active = false);
    }

    /** Starts the accounts from the core's current stock. */
    public void begin(){
        start.clear();
        built.clear();
        made.clear();
        burned.clear();
        low.clear();
        marks.clear();
        madeBy.clear();
        last.clear();
        var core = state.rules.defaultTeam.core();
        if(core == null) return;
        for(Item item : content.items()){
            start.put(item, core.items.get(item));
            low.put(item, core.items.get(item));
        }
        active = true;
    }

    void update(){
        if(!active || !state.isPlaying()) return;
        var core = state.rules.defaultTeam.core();
        if(core == null) return;
        for(Item item : content.items()) if(core.items.get(item) < low.get(item, 0)) low.put(item, core.items.get(item));
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(b instanceof ResonanceDynamoBuild) continue;
            if(b instanceof GenericCrafterBuild g && g.block instanceof GenericCrafter c && c.outputItem != null){
                if(wrapped(b.id, g.progress)){
                    made.increment(c.outputItem.item, c.outputItem.amount);
                    madeBy.increment(b.block, c.outputItem.amount);
                }
            }else if(b instanceof AncientSite.SiteBuild site && site.block instanceof AncientSite a && a.output != null && site.siteState == AncientSite.SiteState.restored){
                //a restored ancient machine (the recoverer, the processing line) crafts like a crafter
                if(wrapped(b.id, site.progress)){
                    made.increment(a.output.item, a.output.amount);
                    madeBy.increment(b.block, a.output.amount);
                }
            }else if(b instanceof DrillBuild d && d.dominantItem != null){
                if(wrapped(b.id, d.progress)) made.increment(d.dominantItem);
            }else if(b instanceof ConsumeGeneratorBuild g){
                //a generator takes in its fuel by resetting its burn time to 1: the only flammable item on these maps is carbon
                if(rose(b.id, g.generateTime)) burned.increment(VItems.vitreousCarbon);
            }else if(b instanceof SurveyRigBuild r){
                if(rose(b.id, r.sample)) burned.increment(VItems.recoveredCircuit);
            }
        }
    }

    /** Progress that went down since the last frame: a craft or an ore handed over. */
    boolean wrapped(int id, float progress){
        float was = last.get(id, progress);
        last.put(id, progress);
        return progress < was - 0.0001f;
    }

    /** A timer that went up since the last frame: fuel taken in. */
    boolean rose(int id, float value){
        float was = last.get(id, value);
        last.put(id, value);
        return value > was + 0.0001f;
    }

    /** Resonance cores burned by the relays, the resonance dynamos and the containment fields. */
    public static int coresBurned(){
        int sum = 0;
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(b instanceof NodeBuild n) sum += n.coresSpent;
            else if(b instanceof ResonanceDynamoBuild d) sum += d.coresBurned;
            else if(b instanceof FieldBuild f) sum += f.coresSpent;
        }
        return sum;
    }

    /** Items of one kind held anywhere by the team: buildings (belts included) and units. */
    public static int held(Item item){
        int sum = 0;
        for(Building b : state.rules.defaultTeam.data().buildings) if(b.items != null) sum += b.items.get(item);
        for(Unit u : state.rules.defaultTeam.data().units) if(u.stack.item == item) sum += u.stack.amount;
        return sum;
    }

    /** Records the core's stock and the counts so far under a name. */
    public void mark(String name){
        var core = state.rules.defaultTeam.core();
        marks.put(name, "core " + (core == null ? "-" : stock(core.items)) + "; made cores " + made.get(VItems.resonanceCore, 0) + ", burned cores " + coresBurned()
            + ", held cores " + held(VItems.resonanceCore));
    }

    static String stock(mindustry.world.modules.ItemModule items){
        StringBuilder out = new StringBuilder();
        for(Item item : content.items()) if(items.get(item) > 0) out.append(out.length() == 0 ? "" : " ").append(label(item)).append(items.get(item));
        return out.toString();
    }

    static String label(Item item){
        return item.name.replace("vesper-", "");
    }

    /** The accounts, item by item: start, construction, made, burned, lowest core stock, now in the core; and the core balance. */
    public String report(){
        var core = state.rules.defaultTeam.core();
        StringBuilder out = new StringBuilder();
        for(Item item : content.items()){
            int s = start.get(item, 0), b = built.get(item, 0), m = made.get(item, 0), u = burned.get(item, 0);
            if(s == 0 && b == 0 && m == 0 && u == 0) continue;
            int now = core == null ? -1 : core.items.get(item);
            out.append(out.length() == 0 ? "" : "; ").append(label(item)).append(": start ").append(s).append(" built ").append(b).append(" made ").append(m);
            if(u > 0) out.append(" burned ").append(u);
            out.append(" low ").append(low.get(item, 0)).append(" now ").append(now);
        }
        //cores: what came in (start, made) against what went out (construction, burned) and what is held
        int in = start.get(VItems.resonanceCore, 0) + made.get(VItems.resonanceCore, 0), outflow = built.get(VItems.resonanceCore, 0) + coresBurned(),
            held = held(VItems.resonanceCore);
        out.append(" | cores in ").append(in).append(", out ").append(outflow).append(", held ").append(held).append(", unaccounted ").append(in - outflow - held);
        return out.toString();
    }
}
