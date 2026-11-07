package vesper.harness.suites;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;
import vesper.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.mission.*;
import vesper.systems.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.ContainmentField.*;
import vesper.world.SignalNode.*;
import vesper.world.SignalTerminal.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Failures injected by the harness into a running synchronization, to show what redundancy buys. Two bases that
 * differ only in redundancy are built in the same purpose-built world: <b>plain</b> (each relay fed by a router from
 * a branch of a trunk out of the core, powered by a resonance dynamo through a one-tile link) and <b>redundant</b>
 * (the same, plus a power accumulator beside each relay, on the relay's side of the link, and a containment field
 * beside it). Each scenario starts a fresh synchronization, injects one failure fifteen seconds in, and measures
 * the time the network was down, the interruptions, the progress that slipped and the time until it ran again:
 * <ul>
 * <li>a power line cut (the link between a relay and its dynamo destroyed);</li>
 * <li>a belt cut (three belts of a branch destroyed);</li>
 * <li>a fuel shortage (the core's cores held back for 60 s, the dynamo and the branch emptied);</li>
 * <li>a relay switched off for 20 s;</li>
 * <li>a pulse on the relay: the redundant base's field covers it, the plain base has none;</li>
 * <li>an attack: enemy units dropped beside a relay group; after the response delay the defense deals with them (the
 * harness removes them: there is no turret in this world).</li>
 * </ul>
 * The player answers a destruction after a fixed {@link #response} (the bot rebuilds from the rebuild plans, with
 * items from the core), the same for both bases. These are mechanism tests, not balance: what happens in a whole
 * game is measured by the playthroughs.
 */
public class DeepResilienceSuite implements VesperHarness.Suite{
    static final Team team = Team.sharded;
    static final int width = 66, height = 40;
    /** Bottom-left of each relay, its link (1x1), its dynamo; west, east, north. */
    static final int[][] relays = {{10, 15}, {54, 15}, {33, 30}}, links = {{9, 16}, {57, 16}, {36, 31}}, dynamos = {{6, 15}, {58, 15}, {37, 30}};
    /** The redundant base: an accumulator beside each relay, a field above the accumulator. */
    static final int[][] accumulators = {{13, 15}, {52, 15}, {31, 30}}, fields = {{13, 17}, {52, 17}, {31, 32}};
    static final int terminalX = 41, terminalY = 20, ventX = 3, ventY = 24;
    /** Seconds before the player answers a destruction. */
    static final float response = 15f;

    final Bot bot = new Bot();
    boolean listening, measuring;
    float down, lastDown, injectedAt, ticks, slipped, lastProgress;
    int interruptionsBefore;
    final ObjectIntMap<String> lost = new ObjectIntMap<>();
    /** Each scenario's result per base, for the comparison checks. */
    final ObjectMap<String, float[]> results = new ObjectMap<>();
    float holdShock = -1f;

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            Events.run(Trigger.update, () -> {
                if(!state.isPlaying()) return;
                ticks += Time.delta;
                if(holdShock >= 0f && Vesper.activity.active()) Vesper.activity.shock = holdShock;
                var t = terminal();
                if(!measuring || t == null || t.syncState != SyncState.syncing) return;
                if(!t.allOperational()){
                    down += Time.delta;
                    lastDown = ticks;
                }
                if(t.progress < lastProgress) slipped += lastProgress - t.progress;
                lastProgress = t.progress;
            });
            Events.on(BlockDestroyEvent.class, e -> {
                if(measuring && e.tile.team() == team) lost.increment(e.tile.block().name.replace("vesper-", ""));
            });
            Events.on(ResetEvent.class, e -> holdShock = -1f);
        }

        String[] scenarios = {"power line cut", "belt cut", "fuel shortage", "relay switched off", "pulse", "attack"};
        for(String scenario : scenarios){
            for(boolean redundant : new boolean[]{false, true}){
                scenario(h, s, scenario, redundant);
            }
        }

        // --- what the comparisons show (the mechanism, injected; not the balance of a whole game)
        s.check("resilience: a cut power line downs the plain base's relay until the line is rebuilt; the redundant base's accumulator keeps it running",
            () -> get("power line cut", false)[0] > 3f && get("power line cut", true)[0] < get("power line cut", false)[0] && get("power line cut", true)[0] <= 1f,
            () -> summary("power line cut"));
        s.check("resilience: a belt cut is ridden out by what the relays and dynamos hold, with or without redundancy", () -> get("belt cut", false)[0] <= 1f
            && get("belt cut", true)[0] <= 1f, () -> summary("belt cut"));
        s.check("resilience: in a fuel shortage the accumulators keep the redundant network up longer", () -> get("fuel shortage", true)[0] < get("fuel shortage", false)[0],
            () -> summary("fuel shortage"));
        s.check("resilience: a relay switched off costs both bases the same (redundancy cannot replace a relay)", () -> Math.abs(get("relay switched off", true)[0]
            - get("relay switched off", false)[0]) <= 2f && get("relay switched off", false)[2] <= 0.13f, () -> summary("relay switched off"));
        s.check("resilience: a pulse on the relay interrupts the plain base; the field keeps the redundant base running", () -> get("pulse", false)[0] >= 15f
            && get("pulse", true)[0] <= 1f && get("pulse", false)[2] <= 0.13f, () -> summary("pulse"));
        s.check("resilience: every scenario ends with the network running again", () -> {
            for(var e : results) if(e.value[3] < 0f) return false;
            return true;
        }, () -> results.keys().toSeq().toString(", ", k -> k + " " + (results.get(k)[3] < 0f ? "not recovered" : "ok")));
        s.run(() -> {
            h.line("info: resilience table (seconds down / interruptions / progress slipped / seconds to recover / buildings lost)");
            for(String scenario : scenarios) h.line("info: resilience " + summary(scenario));
            bot.active = false;
            logic.reset();
            state.set(State.menu);
        });
    }

    float[] get(String scenario, boolean redundant){
        return results.get(scenario + (redundant ? " redundant" : " plain"), new float[]{-1f, -1f, -1f, -1f, -1f});
    }

    String summary(String scenario){
        StringBuilder out = new StringBuilder(scenario).append(':');
        for(boolean r : new boolean[]{false, true}){
            float[] v = get(scenario, r);
            out.append(r ? " | redundant " : " plain ").append(Strings.fixed(v[0], 1)).append(" s / ").append((int)v[1]).append(" / ")
                .append(Strings.fixed(v[2] * 100f, 1)).append("% / ").append(v[3] < 0f ? "no" : Strings.fixed(v[3], 1) + " s").append(" / ").append((int)v[4]);
        }
        return out.toString();
    }

    void scenario(VesperHarness h, Script s, String scenario, boolean redundant){
        String key = scenario + (redundant ? " redundant" : " plain");
        var a = Vesper.activity;
        s.run(() -> {
            bot.active = false;
            lost.clear();
            measuring = false;
            holdShock = -1f;
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.loadout.clear();
            r.tags.put(FirstAwakening.tagScenario, DeepSignal.scenarioId);
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagResponse, "true");
            r.tags.put(AncientSite.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagGates, ventX + "," + ventY);
            Worlds.generate(width, height, tiles -> generate(tiles, redundant), r);
        });
        s.wait(0.5f);
        s.run(() -> {
            //no patrols, and no pulse unless the scenario asks for one
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
            var core = team.core();
            core.items.add(resonanceCore, 400);
            core.items.add(ferroprism, 1000);
            core.items.add(structuralAlloy, 800);
            core.items.add(recoveredCircuit, 400);
            core.items.add(conductiveScrap, 400);
            //the survey is the deep suite's business: the terminal starts surveyed (its relays known, switched on)
            terminal().siteState = SiteState.investigated;
            world.build(34, 5).configure(resonanceCore);
            if(redundant) for(int[] f : fields) ((FieldBuild)Worlds.at(f[0], f[1], 2)).handleStack(resonanceCore, 6, null);
            bot.active = true;
            bot.rebuild = true;
        });
        //the trunk fills the relays and the dynamos
        s.waitFor(() -> terminal() != null && terminal().allOperational() && relaysFull(), 60f);
        s.check("resilience " + key + ": the network runs on the trunk before the start", () -> terminal().allOperational(), () -> DeepSignalSuite.nodeStates());
        s.run(() -> terminal().configure(true));
        s.waitFor(() -> terminal().syncState == SyncState.syncing, 5f);
        s.wait(15f);
        s.run(() -> {
            measuring = true;
            down = slipped = 0f;
            lastDown = -1f;
            lastProgress = terminal().progress;
            injectedAt = ticks;
            interruptionsBefore = terminal().interruptions;
            inject(scenario);
            h.line("phase: resilience " + key + ": injected (" + DeepSignalSuite.terminalState() + ")");
        });
        if(scenario.equals("power line cut") || scenario.equals("belt cut") || scenario.equals("attack")){
            //the player notices and starts rebuilding after a fixed delay; in an attack the defense deals with the attackers then
            s.run(() -> bot.rebuild = false);
            s.wait(response);
            s.run(() -> {
                if(scenario.equals("attack")) Groups.unit.each(u -> u.team == state.rules.waveTeam, Unit::kill);
                bot.rebuild = true;
            });
        }else if(scenario.equals("fuel shortage")){
            s.wait(60f);
            s.run(() -> team.core().items.add(resonanceCore, 400));
        }else if(scenario.equals("relay switched off")){
            s.wait(20f);
            s.run(() -> node(0).configure(true));
        }else if(scenario.equals("pulse")){
            //held up until the pulse has landed and ended
            s.waitFor(() -> a.pulses > 0 && a.pulseActive < 0f, 90f);
            s.run(() -> holdShock = -1f);
        }
        //then until the network has run again for 10 s (or the synchronization completed), at most 3 minutes
        s.waitFor(() -> terminal().syncState == SyncState.synced || (terminal().allOperational() && (lastDown < 0f || ticks - lastDown > 600f)), 180f);
        s.run(() -> {
            measuring = false;
            boolean recovered = terminal().syncState == SyncState.synced || terminal().allOperational();
            int lostCount = 0;
            for(var e : lost) lostCount += e.value;
            results.put(key, new float[]{down / 60f, terminal().interruptions - interruptionsBefore, slipped,
                !recovered ? -1f : lastDown < 0f ? 0f : (lastDown - injectedAt) / 60f, lostCount});
            h.line("info: resilience " + key + ": down " + Strings.fixed(down / 60f, 1) + " s, interruptions " + (terminal().interruptions - interruptionsBefore)
                + ", slipped " + Strings.fixed(slipped * 100f, 1) + "%, recovered " + (recovered ? lastDown < 0f ? "never down" : "after "
                + Strings.fixed((lastDown - injectedAt) / 60f, 1) + " s" : "no") + ", lost " + lost + ", " + DeepSignalSuite.terminalState());
        });
    }

    void inject(String scenario){
        var a = Vesper.activity;
        switch(scenario){
            case "power line cut" -> world.build(links[0][0], links[0][1]).kill();
            case "belt cut" -> {
                for(int x = 20; x <= 22; x++) world.build(x, 14).kill();
            }
            case "fuel shortage" -> {
                team.core().items.remove(resonanceCore, team.core().items.get(resonanceCore));
                //and what was already on its way: the trunk, the hub, the west branch, its routers and its dynamo
                Worlds.at(dynamos[0][0], dynamos[0][1], 3).items.clear();
                for(int y = 6; y <= 14; y++) empty(world.build(34, y));
                for(int x = 8; x <= 33; x++) empty(world.build(x, 14));
            }
            case "relay switched off" -> node(0).configure(false);
            case "pulse" -> {
                //the basin held above the pulse level until the pulse lands on the west group (the resonance machine nearest the vent)
                holdShock = 60f;
                a.pulseCooldown = 0f;
            }
            case "attack" -> {
                for(int i = 0; i < 6; i++) VUnits.rustCrawler.spawn(state.rules.waveTeam, (4 + i % 3) * tilesize, (22 + i / 3) * tilesize);
                for(int i = 0; i < 2; i++) VUnits.ancientSentinel.spawn(state.rules.waveTeam, (5 + i * 3) * tilesize, 26 * tilesize);
            }
        }
    }

    /** Empties a belt or a router (a belt keeps its items in its own arrays as well). */
    static void empty(@Nullable Building b){
        if(b == null || b.items == null) return;
        b.items.clear();
        if(b instanceof mindustry.world.blocks.distribution.Conveyor.ConveyorBuild c){
            c.len = 0;
            c.mid = 0;
            c.minitem = 1f;
        }
    }

    /** Every relay and its dynamo hold some cores. */
    static boolean relaysFull(){
        for(int i = 0; i < 3; i++){
            if(node(i).items.get(resonanceCore) < 3 || Worlds.at(dynamos[i][0], dynamos[i][1], 3).items.get(resonanceCore) < 3) return false;
        }
        return true;
    }

    static void generate(Tiles tiles, boolean redundant){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                tiles.set(x, y, new Tile(x, y, hullPlating, Blocks.air, edge ? bulkheadWall : Blocks.air));
            }
        }
        place(tiles, coreFoothold, 33, 2, 0);
        place(tiles, signalTerminal, terminalX, terminalY, 0);
        for(int i = 0; i < 3; i++){
            place(tiles, signalNode, relays[i][0], relays[i][1], 0);
            place(tiles, resonanceLink, links[i][0], links[i][1], 0);
            place(tiles, resonanceDynamo, dynamos[i][0], dynamos[i][1], 0);
            if(redundant){
                place(tiles, powerAccumulator, accumulators[i][0], accumulators[i][1], 0);
                place(tiles, containmentField, fields[i][0], fields[i][1], 0);
            }
        }
        //the trunk: an unloader over the core, up to a hub router, three branches
        tiles.getn(34, 5).setBlock(cargoUnloader, team, 0);
        line(tiles, 34, 6, 34, 13, 1);
        tiles.getn(34, 14).setBlock(cargoRouter, team, 0);
        //west: to the router under the relay, a belt across the link's column, the router under the dynamo
        line(tiles, 11, 14, 33, 14, 2);
        tiles.getn(10, 14).setBlock(cargoRouter, team, 0);
        tiles.getn(9, 14).setBlock(transitBelt, team, 2);
        tiles.getn(8, 14).setBlock(cargoRouter, team, 0);
        //east
        line(tiles, 35, 14, 53, 14, 0);
        tiles.getn(54, 14).setBlock(cargoRouter, team, 0);
        line(tiles, 55, 14, 57, 14, 0);
        tiles.getn(58, 14).setBlock(cargoRouter, team, 0);
        //north
        line(tiles, 34, 15, 34, 28, 1);
        tiles.getn(34, 29).setBlock(cargoRouter, team, 0);
        line(tiles, 35, 29, 36, 29, 0);
        tiles.getn(37, 29).setBlock(cargoRouter, team, 0);
    }

    static void line(Tiles tiles, int x0, int y0, int x1, int y1, int rotation){
        for(int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++){
            for(int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) tiles.getn(x, y).setBlock(transitBelt, team, rotation);
        }
    }

    static void place(Tiles tiles, Block block, int left, int bottom, int rotation){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, rotation);
    }

    static @Nullable TerminalBuild terminal(){
        return SignalTerminal.find(team);
    }

    static NodeBuild node(int i){
        return (NodeBuild)world.build(relays[i][0] + 1, relays[i][1] + 1);
    }
}
