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
    /** Without a local player (dedicated server), the bot flies a core unit of its own. */
    @Nullable Unit own;
    float respawn;
    /** Plans the unit dropped without building (and that were queued again), for diagnosis. */
    public int requeued, unitChanges;
    /** Rebuild what the team lost from its rebuild plans, as a player does with the rebuild button. */
    public boolean rebuild;
    public int rebuilt;
    float rebuildTimer;

    //delivery to the reactor
    @Nullable ReactorBuild reactor;
    @Nullable Item deliveryItem;

    public Bot(){
        Events.run(Trigger.update, this::update);
        Events.on(ResetEvent.class, e -> {
            backlog.clear();
            wanted.clear();
            unitId = -1;
            own = null;
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

    /** Queues a block with a configuration, applied when it is finished (as a rebuild plan does). */
    public void build(Block block, int left, int bottom, int rotation, Object config){
        build(block, left, bottom, rotation);
        backlog.peek().config = config;
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
        return t != null && t.block() == plan.block && t.build != null && t.build.tile == t && t.team() == state.rules.defaultTeam;
    }

    /** The unit that builds and carries: the player's, or on a dedicated server one the bot spawns at the core. */
    @Nullable Unit unit(){
        if(player != null) return player.unit();
        if(own != null && own.isValid()) return own;
        Building core = state.rules.defaultTeam.core();
        //respawn delay, like the core's
        if(core == null || (respawn += Time.delta) < 60f) return null;
        respawn = 0f;
        own = ((mindustry.world.blocks.storage.CoreBlock)core.block).unitType.spawn(state.rules.defaultTeam, core.x, core.y);
        own.controller(new mindustry.entities.units.AIController(){
            @Override
            public void updateUnit(){
                //the bot moves it; it must not wander or shoot on its own
            }
        });
        own.updateBuilding = true;
        return own;
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
        Unit u = unit();
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
        if(rebuild && (rebuildTimer += Time.delta) >= 60f){
            rebuildTimer = 0f;
            for(var lost : state.rules.defaultTeam.data().plans){
                if(wanted.contains(w -> w.x == lost.x && w.y == lost.y)) continue;
                BuildPlan plan = new BuildPlan(lost.x, lost.y, lost.rotation, lost.block, lost.config);
                backlog.add(plan);
                wanted.add(plan);
                rebuilt++;
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
            if(fly(u, reactor.x, reactor.y)) deposit(u, reactor);
        }else{
            if(fly(u, core.x, core.y)){
                if(u.stack.amount > 0) deposit(u, core);
                int want = Math.min(reactor.remaining(deliveryItem), u.type.itemCapacity);
                if(core.items.get(deliveryItem) >= want){
                    if(player != null) Call.requestItem(player, core, deliveryItem, want);
                    else Call.takeItems(core, deliveryItem, Math.min(u.maxAccepted(deliveryItem), want), u);
                    status = "loading " + deliveryItem.name;
                }else{
                    status = "waiting for " + want + " " + deliveryItem.name + " in the core (" + core.items.get(deliveryItem) + ")";
                }
            }
        }
    }

    /** Drops the unit's stack into a building: the player's deposit request, or on a server the transfer it performs. */
    void deposit(Unit u, Building build){
        if(player != null){
            Call.transferInventory(player, build);
        }else if(u.stack.amount > 0){
            Call.transferItemTo(u, u.item(), build.acceptStack(u.item(), u.stack.amount, u), u.x, u.y, build);
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
