package vesper.harness.suites;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.world.*;
import mindustry.world.blocks.storage.Unloader.*;
import vesper.*;
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
 * The deep network's rules in a purpose-built world, as a custom game: a signal terminal surveyed by a real survey
 * rig, three relays each powered by a resonance dynamo beside it, a containment field at the relay nearest the vent.
 * Checks every relay state, the start (refused without every relay operational, or with one missing), the
 * synchronization's rate, a short interruption (free), a long one (grace, then a slow slip that stops at the last
 * quarter), fire on a relay (a jam, no damage), a pulse absorbed by the field and one that is not, save and load in
 * the middle of the synchronization and of an interruption, a relay switched off at the last moment, the completion
 * (once), and what happens after it. Every resonance core delivered to a relay is in it or burned by it.
 * <p>
 * Test fixtures: the cores and circuits the machines start with are added by the test (what belts would bring);
 * the activity is held up with a shock so the pulses come on time, and the patrols are held back.
 */
public class DeepSignalSuite implements VesperHarness.Suite{
    static final Team team = Team.sharded;
    static final int width = 60, height = 44;
    /** Bottom-left tiles of the relays, the terminal, the rig and its dynamo, the field. */
    static final int[][] relays = {{8, 20}, {48, 20}, {28, 32}};
    static final int terminalX = 28, terminalY = 10, rigX = 33, rigY = 11, fieldX = 25, fieldY = 30;
    static final int ventX = 28, ventY = 40;

    final IntIntMap delivered = new IntIntMap();
    boolean listening;
    float holdShock = -1f, ticks, mark, expected;
    int syncEvents, wins, spentBefore, pulsesBefore;
    float progressBefore;
    String before = "", campaignBefore = "";

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            Events.on(SyncEvent.class, e -> syncEvents++);
            Events.on(GameOverEvent.class, e -> {
                if(e.winner == team) wins++;
            });
            Events.run(Trigger.update, () -> {
                if(!state.isPlaying()) return;
                ticks += Time.delta;
                //what the synchronization should have advanced: 1/150 per second of every relay running, times the basin's resonance
                var t = terminal();
                if(t != null && t.syncState == SyncState.syncing && t.allOperational()) expected += Time.delta * Vesper.activity.resonanceMultiplier() / (150f * 60f);
                if(holdShock >= 0f && Vesper.activity.active() && !net.client()) Vesper.activity.shock = holdShock;
            });
            Events.on(ResetEvent.class, e -> holdShock = -1f);
        }
        var a = Vesper.activity;

        s.run(() -> {
            delivered.clear();
            syncEvents = wins = 0;
            campaignBefore = CampaignSuite.chapterSettings();
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
            Worlds.generate(width, height, DeepSignalSuite::generate, r);
        });
        s.wait(0.5f);
        s.run(() -> {
            //no patrols and no pulse until the pulse checks ask for one
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
        });
        s.check("deep: the scenario binds to the world as a custom game, nothing done yet", () -> Vesper.scenario() == Vesper.deep && Vesper.deep.active()
            && Vesper.deep.completed == 0 && !Vesper.deep.victory && terminal() != null && terminal().siteState == SiteState.unexplored && terminal().nodes().size == 3
            && !state.isCampaign(), () -> "scenario " + Vesper.scenario());
        s.check("relays: dark before the survey: unknown, no cores taken, no power drawn, no activity", () -> nodes().allMatch(n -> n.state() == NodeState.unknown
            && !n.acceptItem(null, resonanceCore) && !n.shouldConsume() && n.activity() == 0f), () -> nodeStates());
        s.run(() -> terminal().configure(true));
        s.wait(0.5f);
        s.check("terminal: a start before the survey is refused", () -> terminal().syncState == SyncState.idle, () -> terminal().syncState.name());

        // --- the survey, by a real rig with power and circuits
        s.run(() -> {
            feed(world.build(rigX + 3, rigY + 1), resonanceCore, 10);
            feed(world.build(rigX, rigY), recoveredCircuit, 40);
        });
        s.until("survey: a powered survey rig with circuits reveals the terminal", () -> terminal().siteState == SiteState.investigated, 90f,
            () -> "survey " + (int)(terminal().investigation * 100) + "%");
        s.check("survey: the first objective is done and the relays are known: switched on, but no power yet", () -> Vesper.deep.completed >= 1
            && nodes().allMatch(n -> n.known() && n.online && n.state() == NodeState.unpowered), () -> nodeStates());
        s.check("relays: they take resonance cores and nothing else", () -> nodes().allMatch(n -> n.acceptItem(null, resonanceCore) && !n.acceptItem(null, ferroprism)
            && !n.acceptItem(null, recoveredCircuit)), () -> "");

        // --- power: each relay's own dynamo
        s.run(() -> {
            for(int[] r : relays) feed(world.build(r[0] - 2, r[1] + 1), resonanceCore, 40);
        });
        s.wait(2f);
        s.check("relays: powered but with no cores: starved", () -> nodes().allMatch(n -> n.powered() && n.state() == NodeState.starved), () -> nodeStates());

        // --- nothing can be taken back out of a relay
        s.run(() -> {
            node(0).handleStack(resonanceCore, 4, null);
            delivered.increment(node(0).pos(), 4);
            unloader().configure(resonanceCore);
        });
        s.wait(4f);
        s.check("relays: an unloader beside a relay takes nothing out of it", () -> world.build(7, 17).items.get(resonanceCore) == 0
            && node(0).items.get(resonanceCore) + node(0).coresSpent == 4, () -> "container " + world.build(7, 17).items.get(resonanceCore) + ", " + nodeStates());
        s.check("relays: with power and cores, operational; the first core paid at once", () -> node(0).state() == NodeState.operational && node(0).coresSpent == 1
            && node(0).upkeep > 0f && node(0).activity() == 4f, () -> nodeStates());

        // --- a relay missing from the map: the protocol cannot start
        s.run(() -> {
            feedNode(0, 6);
            feedNode(1, 10);
            world.tile(relays[2][0] + 1, relays[2][1] + 1).setNet(Blocks.air);
        });
        s.wait(1f);
        s.run(() -> terminal().configure(true));
        s.wait(0.5f);
        s.check("terminal: with a relay missing, the start is refused even though the others run (and it says so)", () -> terminal().syncState == SyncState.idle
            && terminal().nodes().size == 2 && terminal().nodes().allMatch(NodeBuild::operational) && !terminal().canStart()
            && terminal().infoText().contains(Core.bundle.format("vesper.terminal.missing", 1, 3)), () -> terminal().infoText());
        s.run(() -> world.tile(relays[2][0] + 1, relays[2][1] + 1).setNet(signalNode, team, 0));
        s.wait(1f);
        s.run(() -> terminal().configure(true));
        s.wait(0.5f);
        s.check("terminal: three relays again, one starved: a start without materials is refused", () -> terminal().syncState == SyncState.idle
            && terminal().nodes().size == 3 && node(2).state() == NodeState.starved && !terminal().canStart() && Vesper.deep.completed == 1, () -> nodeStates());
        s.run(() -> feedNode(2, 10));
        s.wait(1f);
        s.check("relays: all three operational: the second objective is done and the terminal is ready", () -> terminal().canStart() && Vesper.deep.completed >= 2
            && terminal().infoText().contains(Core.bundle.get("vesper.terminal.ready")), () -> terminal().infoText());

        // --- the synchronization
        s.run(() -> {
            terminal().configure(true);
            mark = ticks;
        });
        s.wait(0.5f);
        s.check("sync: started; the relays show it and stir the basin twice as much", () -> terminal().syncState == SyncState.syncing
            && nodes().allMatch(n -> n.state() == NodeState.synced && n.activity() == 8f), () -> nodeStates());
        s.run(() -> {
            mark = ticks;
            expected = 0f;
            progressBefore = terminal().progress;
        });
        s.wait(15f);
        s.check("sync: advances at 1/150 per second of all three relays running, times the basin's resonance", () -> near(terminal().progress - progressBefore, expected, 0.004f)
            && expected > (ticks - mark) / (150f * 60f) * 0.999f, () -> Strings.fixed((terminal().progress - progressBefore) * 100f, 2) + "% in " + Strings.fixed((ticks - mark) / 60f, 1)
            + " s, expected " + Strings.fixed(expected * 100f, 2) + "% at resonance x" + Strings.fixed(Vesper.activity.resonanceMultiplier(), 2));

        // --- a short interruption costs nothing
        s.run(() -> {
            progressBefore = terminal().progress;
            node(1).configure(false);
        });
        s.wait(3f);
        s.check("interruption: a relay switched off: the synchronization holds, the relay and the terminal say so", () -> terminal().progress == progressBefore
            && terminal().interrupted > 0f && terminal().interruptions == 1 && node(1).state() == NodeState.interrupted
            && node(1).label().equals(Core.bundle.format("vesper.node.state.interrupted", NodeState.offline.localized())), () -> node(1).label() + ", " + terminalState());
        s.run(() -> node(1).configure(true));
        s.wait(1f);
        s.check("interruption: 3 s, within the grace: nothing lost, it resumes", () -> terminal().interrupted == 0f && terminal().progress > progressBefore,
            () -> terminalState());

        // --- fire on a relay jams it, without damage
        s.run(() -> {
            progressBefore = terminal().progress;
            node(0).damage(120f);
        });
        s.wait(0.5f);
        s.check("jam: fire on a relay jams it for 4 s and does it no damage", () -> node(0).jammed > 0f && node(0).health == node(0).maxHealth
            && node(0).reason() == NodeState.jammed && terminal().interrupted > 0f, () -> nodeStates());
        s.wait(5f);
        s.check("jam: it clears by itself and the synchronization goes on, nothing lost", () -> node(0).operational() && terminal().interrupted == 0f
            && terminal().progress > progressBefore && terminal().interruptions == 2, () -> terminalState());

        // --- a long interruption: grace, then a slow slip that stops at the last quarter
        s.waitFor(() -> terminal().progress >= 0.40f, 120f);
        s.run(() -> {
            progressBefore = terminal().progress;
            node(2).configure(false);
        });
        s.wait(7f);
        s.check("long interruption: nothing lost during the 8 s grace", () -> terminal().progress == progressBefore && terminal().checkpoint == 0.25f, () -> terminalState());
        s.wait(5f);
        s.check("long interruption: past the grace, it slips back about 1% per second", () -> near(progressBefore - terminal().progress, 0.04f, 0.015f), () -> terminalState()
            + ", lost " + Strings.fixed((progressBefore - terminal().progress) * 100f, 1) + "%");

        // --- save and load in the middle of an interruption
        s.run(() -> {
            before = saved();
            Fi file = h.out.child("deep-interrupted.msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
            h.check("persistence: mid-interruption, the synchronization, every relay and the objectives survive save and load", saved().equals(before),
                "before " + before + ", after " + saved());
        });
        s.wait(30f);
        s.check("long interruption: after loading it goes on slipping, and stops at the last quarter reached (25%)", () -> terminal().progress == 0.25f
            && terminal().syncState == SyncState.syncing && Vesper.deep.active() && Vesper.deep.completed == 2, () -> terminalState());
        s.run(() -> node(2).configure(true));
        s.wait(1f);
        s.check("long interruption: the relay back on, it resumes from 25%", () -> terminal().interrupted == 0f && terminal().progress > 0.25f, () -> terminalState());

        // --- pulses: the relay nearest the vent takes them; the field beside it absorbs the first
        s.run(() -> {
            feed(field(), resonanceCore, 10);
            field().configure(true);
            pulsesBefore = a.pulses;
            holdShock = 30f;
            a.pulseCooldown = 0f;
        });
        s.until("pulse: announced at the relay nearest the vent", () -> a.pulsePending > 0f && Math.abs(a.pulseX - (relays[2][0] + 1)) <= 3
            && Math.abs(a.pulseY - (relays[2][1] + 1)) <= 3, 60f, () -> "level " + (int)a.level + ", epicentre " + a.pulseX + "," + a.pulseY);
        s.waitFor(() -> a.pulses > pulsesBefore && a.pulseActive > 0f, 30f);
        s.run(() -> {
            a.pulseCooldown = 1e9f;
            progressBefore = terminal().progress;
        });
        s.wait(3f);
        s.check("pulse: the field absorbed it: the relay in the pulse stays operational and the synchronization goes on", () -> field().shielding()
            && a.inPulse(node(2)) && node(2).operational() && terminal().progress > progressBefore, () -> nodeStates() + ", " + terminalState());
        s.waitFor(() -> a.pulseActive < 0f, 25f);
        s.run(() -> {
            field().configure(false);
            pulsesBefore = a.pulses;
            a.pulseCooldown = 0f;
        });
        s.waitFor(() -> a.pulses > pulsesBefore && a.pulseActive > 0f, 60f);
        s.run(() -> {
            a.pulseCooldown = 1e9f;
            progressBefore = terminal().progress;
        });
        s.wait(2f);
        s.check("pulse: with the field off, the pulse interrupts the relay (the terminal shows why)", () -> a.interferes(node(2)) && node(2).reason() == NodeState.interfered
            && terminal().interrupted > 0f && terminal().infoText().contains(NodeState.interfered.localized()), () -> nodeStates() + ", " + terminalState());
        s.waitFor(() -> a.pulseActive < 0f, 25f);
        s.wait(1f);
        s.check("pulse: once it ends the relay runs again; the 20 s cost at most 12 s of slip, never below the last quarter", () -> node(2).operational()
            && terminal().interrupted == 0f && terminal().progress >= Math.max(terminal().checkpoint, progressBefore - 0.13f), () -> terminalState());
        s.run(() -> holdShock = 0f);

        // --- save and load in the middle of the synchronization
        s.run(() -> {
            before = saved();
            Fi file = h.out.child("deep-syncing.msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
            h.check("persistence: mid-synchronization, everything survives save and load", saved().equals(before), "before " + before + ", after " + saved());
        });

        // --- a relay switched off at the last moment
        s.run(() -> {
            //cores for the rest of the run (what the belts would keep bringing)
            for(int i = 0; i < 3; i++) feedNode(i, 6);
        });
        s.waitFor(() -> terminal().progress >= 0.985f, 200f);
        s.run(() -> node(0).configure(false));
        s.wait(6f);
        s.check("last moment: a relay switched off at 98.5% stops the completion", () -> terminal().syncState == SyncState.syncing && terminal().progress < 1f
            && syncEvents == 0 && !Vesper.deep.victory, () -> terminalState());
        s.run(() -> node(0).configure(true));
        s.until("sync: completes once all three run again", () -> terminal().syncState == SyncState.synced, 15f, () -> terminalState());
        s.wait(2f);
        s.check("victory: the custom game is won, once; the objectives are all done", () -> Vesper.deep.victory && Vesper.deep.completed == 3 && syncEvents == 1
            && wins == 1 && state.gameOver, () -> "sync events " + syncEvents + ", wins " + wins);
        s.check("victory: a custom game touches no campaign progress", () -> CampaignSuite.chapterSettings().equals(campaignBefore), () -> CampaignSuite.chapterSettings());

        // --- after the completion
        s.run(() -> {
            spentBefore = nodes().sum(n -> n.coresSpent);
            terminal().configure(true);
            node(1).configure(false);
        });
        s.wait(40f);
        s.check("after: the network stays synchronized; a relay switched off or a new start changes nothing, and nothing completes twice",
            () -> terminal().syncState == SyncState.synced && terminal().progress == 1f && syncEvents == 1 && wins == 1, () -> terminalState());
        s.check("after: a synchronized network burns no more cores", () -> nodes().sum(n -> n.coresSpent) == spentBefore, () -> nodeStates());
        s.check("cores: every core delivered to a relay is still in it or was burned by it", () -> nodes().allMatch(n -> n.items.get(resonanceCore) + n.coresSpent
            == delivered.get(n.pos(), 0)), () -> nodeStates());
        s.run(() -> {
            before = saved();
            Fi file = h.out.child("deep-synced.msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
        });
        s.wait(3f);
        s.check("persistence: a won network loads won, and wins nothing again", () -> saved().equals(before) && Vesper.deep.victory && syncEvents == 1
            && wins == 1, () -> "before " + before + ", after " + saved());
        s.run(() -> {
            holdShock = -1f;
            logic.reset();
            state.set(State.menu);
        });
    }

    static void generate(Tiles tiles){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                tiles.set(x, y, new Tile(x, y, hullPlating, Blocks.air, edge ? bulkheadWall : Blocks.air));
            }
        }
        place(tiles, coreFoothold, 2, 2);
        place(tiles, signalTerminal, terminalX, terminalY);
        place(tiles, surveyRig, rigX, rigY);
        place(tiles, resonanceDynamo, rigX + 2, rigY - 1);
        for(int[] r : relays){
            place(tiles, signalNode, r[0], r[1]);
            place(tiles, resonanceDynamo, r[0] - 3, r[1]);
        }
        place(tiles, containmentField, fieldX, fieldY);
        //an unloader under the first relay's corner, a container below it
        place(tiles, cargoUnloader, 8, 19);
        place(tiles, containerBlock(), 7, 17);
    }

    /** A plain store for the unloader to fill (a vanilla container: the test fixture, not part of any map). */
    static mindustry.world.Block containerBlock(){
        return Blocks.container;
    }

    static void place(Tiles tiles, mindustry.world.Block block, int left, int bottom){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, 0);
    }

    static @Nullable TerminalBuild terminal(){
        return SignalTerminal.find(team);
    }

    static Seq<NodeBuild> nodes(){
        return terminal() == null ? new Seq<>() : terminal().nodes();
    }

    static NodeBuild node(int i){
        return (NodeBuild)world.build(relays[i][0] + 1, relays[i][1] + 1);
    }

    static FieldBuild field(){
        return (FieldBuild)world.build(fieldX, fieldY);
    }

    static UnloaderBuild unloader(){
        return (UnloaderBuild)world.build(8, 19);
    }

    static void feed(Building b, mindustry.type.Item item, int amount){
        b.handleStack(item, amount, null);
    }

    void feedNode(int i, int amount){
        node(i).handleStack(resonanceCore, amount, null);
        delivered.increment(node(i).pos(), amount);
    }

    static boolean near(float a, float b, float eps){
        return Math.abs(a - b) <= eps;
    }

    /** What must survive a save: the terminal's synchronization, every relay's state and store, the scenario's progress. */
    static String saved(){
        var t = terminal();
        if(t == null) return "no terminal";
        StringBuilder out = new StringBuilder().append(t.siteState).append(' ').append(t.syncState).append(' ').append(Strings.fixed(t.progress, 4)).append(' ')
            .append(t.checkpoint).append(' ').append((int)t.interrupted).append(' ').append(t.interruptions);
        for(NodeBuild n : nodes()) out.append(" | ").append(n.online).append(' ').append((int)n.upkeep).append(' ').append((int)n.jammed).append(' ')
            .append(n.coresSpent).append(' ').append(n.items.get(resonanceCore));
        return out.append(" | objectives ").append(Vesper.deep.completed).append(' ').append(Vesper.deep.victory).toString();
    }

    static String nodeStates(){
        StringBuilder out = new StringBuilder();
        for(NodeBuild n : nodes()) out.append(n.tileX()).append(',').append(n.tileY()).append(' ').append(n.state()).append('/').append(n.reason()).append(" power ")
            .append(Strings.fixed(n.power.status, 2)).append(" cores ").append(n.items.get(resonanceCore)).append(" spent ").append(n.coresSpent).append("; ");
        return out.toString();
    }

    static String terminalState(){
        var t = terminal();
        return t == null ? "no terminal" : t.syncState + " " + Strings.fixed(t.progress * 100f, 2) + "% (kept " + (int)(t.checkpoint * 100) + "%), interrupted "
            + Strings.fixed(t.interrupted / 60f, 1) + " s, interruptions " + t.interruptions;
    }
}
