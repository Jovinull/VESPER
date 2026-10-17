package vesper.harness;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import vesper.world.DerelictReactor.*;

import static mindustry.Vars.*;

/**
 * A scripted player for playthrough tests. It plays through the same channels as a person: build plans go to
 * the player's core unit, which builds them with items from the core (construction stalls when the core runs
 * out), it flies at the unit's own speed, and it carries items from the core to the reactor with the unit
 * (request + deposit, 30 items per trip). No cheats, no free items.
 */
public class Bot{
    final Seq<BuildPlan> backlog = new Seq<>();
    /** Every queued plan until its block stands on the map; plans the unit loses are queued again. */
    final Seq<BuildPlan> wanted = new Seq<>();
    public boolean active;
    public String status = "idle";
    int unitId = -1;
    /** Plans the unit dropped without building (and that were queued again), for diagnosis. */
    public int requeued, unitChanges;

    //delivery to the reactor
    @Nullable ReactorBuild reactor;
    @Nullable Item deliveryItem;

    public Bot(){
        Events.run(Trigger.update, this::update);
        Events.on(ResetEvent.class, e -> {
            backlog.clear();
            wanted.clear();
            unitId = -1;
            reactor = null;
            deliveryItem = null;
            active = false;
        });
    }

    /** Queues a block by the bottom-left tile of its footprint. */
    public void build(Block block, int left, int bottom, int rotation){
        int off = (block.size - 1) / 2;
        BuildPlan plan = new BuildPlan(left + off, bottom + off, rotation, block);
        backlog.add(plan);
        wanted.add(plan);
    }

    public void build(Block block, int left, int bottom){
        build(block, left, bottom, 0);
    }

    /** Queues a straight line of 1x1 blocks (conveyors) with one rotation. */
    public void line(Block block, int x0, int y0, int x1, int y1, int rotation){
        for(int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++){
            for(int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++){
                build(block, x, y, rotation);
            }
        }
    }

    /** Carries items from the core into the reactor until it needs no more of that item. */
    public void deliver(ReactorBuild target, Item item){
        reactor = target;
        deliveryItem = item;
    }

    /** True when every queued plan stands on the map and no delivery is running. */
    public boolean idle(){
        return wanted.isEmpty() && deliveryItem == null;
    }

    static boolean built(BuildPlan plan){
        Tile t = world.tile(plan.x, plan.y);
        return t != null && t.block() == plan.block && t.build != null && t.build.tile == t && t.team() == player.team();
    }

    static boolean queued(Unit u, BuildPlan plan){
        for(BuildPlan p : u.plans){
            if(same(p, plan)) return true;
        }
        return false;
    }

    static boolean same(BuildPlan a, BuildPlan b){
        return a.x == b.x && a.y == b.y && a.block == b.block;
    }

    void update(){
        if(!active || !state.isPlaying()) return;
        wanted.removeAll(Bot::built);
        Unit u = player.unit();
        if(u == null || u.dead()){
            status = "waiting for the unit to respawn";
            return;
        }
        if(u.id != unitId){
            if(unitId != -1){
                unitChanges++;
                Log.info("[vesper-harness] bot: the player unit changed (#@ -> #@), @ plans still wanted", unitId, u.id, wanted.size);
            }
            unitId = u.id;
        }
        //anything wanted that is neither waiting nor in the unit's queue was lost: queue it again
        for(BuildPlan plan : wanted){
            if(!backlog.contains(p -> same(p, plan)) && !queued(u, plan)){
                requeued++;
                Log.info("[vesper-harness] bot: @ at @,@ left the unit's queue unbuilt; queued again", plan.block.name, plan.x, plan.y);
                backlog.add(new BuildPlan(plan.x, plan.y, plan.rotation, plan.block));
            }
        }
        while(!backlog.isEmpty() && u.plans.size < 8) u.addBuild(backlog.remove(0));

        if(deliveryItem != null){
            carry(u);
        }else if(u.plans.size > 0){
            BuildPlan plan = u.buildPlan();
            status = "building " + plan.block.name + " at " + plan.x + "," + plan.y;
            fly(u, plan.drawx(), plan.drawy() + tilesize * 2f);
        }else{
            status = "idle";
            Building core = u.closestCore();
            if(core != null) fly(u, core.x, core.y + tilesize * 3f);
        }
    }

    void carry(Unit u){
        Building core = u.closestCore();
        if(reactor == null || !reactor.isValid() || core == null || reactor.remaining(deliveryItem) <= 0){
            deliveryItem = null;
            return;
        }
        if(u.stack.amount > 0 && u.stack.item == deliveryItem){
            status = "carrying " + u.stack.amount + " " + deliveryItem.name + " to the reactor";
            if(fly(u, reactor.x, reactor.y)) Call.transferInventory(player, reactor);
        }else{
            if(fly(u, core.x, core.y)){
                if(u.stack.amount > 0) Call.transferInventory(player, core);
                int want = Math.min(reactor.remaining(deliveryItem), u.type.itemCapacity);
                if(core.items.get(deliveryItem) >= want){
                    Call.requestItem(player, core, deliveryItem, want);
                    status = "loading " + deliveryItem.name;
                }else{
                    status = "waiting for " + want + " " + deliveryItem.name + " in the core (" + core.items.get(deliveryItem) + ")";
                }
            }
        }
    }

    /** Moves at the unit's speed; returns true once within two tiles of the target. */
    boolean fly(Unit u, float x, float y){
        float dst = u.dst(x, y);
        if(dst <= tilesize * 2f) return true;
        float step = Math.min(dst, u.speed() * Time.delta);
        float angle = Angles.angle(u.x, u.y, x, y);
        u.set(u.x + Angles.trnsx(angle, step), u.y + Angles.trnsy(angle, step));
        u.rotation = Angles.moveToward(u.rotation, angle, u.type.rotateSpeed * Time.delta);
        u.vel.setZero();
        return false;
    }
}
