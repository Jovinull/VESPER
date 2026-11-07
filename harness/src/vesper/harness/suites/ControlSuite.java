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
import vesper.*;
import vesper.harness.*;
import vesper.mission.*;
import vesper.systems.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.ContainmentLock.*;
import vesper.world.ProtocolController.*;
import vesper.world.ProtocolSite.*;
import vesper.world.ResonanceDynamo.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Protocol routing on its own, in a purpose-built world, before any map: a central controller surveyed by a real
 * rig and powered by two resonance dynamos beside it, three containment locks over the world's three vents, the
 * distribution junction, the conduction array and the processing line. Checks the survey and what it reveals, the
 * standby load, each mode's split of a short supply (containment first, in proportion, recovery first and
 * overdriven), the ramp of a mode change, locks switched off, a lock without cores, the junction opening the wings,
 * the restoration order, the array feeding the grid, the line making cores, pulses aiming only through open vents,
 * an announced pulse and an announced patrol held back by a lock that closed in time, the sequence (refused until
 * every vent was shut once, standing still while a pulse stops a subsystem), save and load in the middle of a mode
 * change and of the sequence, the completion (once) and what follows. Every core put into the controller is still
 * there or was burned by a bus.
 * <p>
 * Test fixtures: the cores, materials and fuel the machines start with are added by the test (what belts would
 * bring), the activity is held up with a shock when a pulse is wanted, and patrols are held back unless one is
 * announced on purpose.
 */
public class ControlSuite implements VesperHarness.Suite{
    static final Team team = Team.sharded;
    static final int width = 66, height = 48;
    static final int controllerX = 30, controllerY = 18, rigX = 36, rigY = 19;
    /** Dynamos: the rig's (D0), and the controller's two (D1, D2) beside it. */
    static final int[][] dynamos = {{38, 18}, {27, 18}, {27, 21}};
    static final int lineX = 23, lineY = 21, arrayX = 30, arrayY = 23, junctionX = 44, junctionY = 24;
    /** Bottom-left tiles of the three locks; the vents are their centres. */
    static final int[][] locks = {{6, 40}, {32, 40}, {58, 40}};

    final IntIntMap given = new IntIntMap();
    boolean listening, fuel, starveSecond, keepLine;
    int lineCrafts;
    float lineProgress;
    float holdShock = -1f;
    /** The rules of this suite's world (the update hook does nothing elsewhere). */
    @Nullable Rules fixture;
    int reactivations, wins, coresBefore, pulsesBefore, containedBefore;
    float progressBefore, shareBefore;
    String before = "", campaignBefore = "", sealedBefore = "";

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            Events.on(ReactivationEvent.class, e -> reactivations++);
            Events.on(GameOverEvent.class, e -> {
                if(e.winner == team) wins++;
            });
            Events.run(Trigger.update, () -> {
                if(holdShock >= 0f && Vesper.activity.active() && !net.client()) Vesper.activity.shock = holdShock;
                //only in this suite's own world (other suites and maps have controllers of their own)
                if(!state.isPlaying() || controller() == null || state.rules != fixture || !(Worlds.at(lineX, lineY, 4) instanceof ProtocolSiteBuild)) return;
                //what belts would bring: cores to the dynamos (the second one held empty for the short-supply checks), scrap and crystal to the line
                if(fuel){
                    for(int i = 0; i < 3; i++){
                        var d = dynamo(i);
                        if(i == 2 && starveSecond) d.items.clear();
                        else if(d.items.get(resonanceCore) < 5) d.items.add(resonanceCore, 5);
                    }
                }
                var line = line();
                if(keepLine && line.siteState == SiteState.restored){
                    if(line.items.get(conductiveScrap) < 6) line.items.add(conductiveScrap, 12);
                    if(line.items.get(resonanceCrystal) < 2) line.items.add(resonanceCrystal, 4);
                }
                if(line.progress < lineProgress) lineCrafts++;
                lineProgress = line.progress;
            });
            Events.on(ResetEvent.class, e -> holdShock = -1f);
        }
        var a = Vesper.activity;

        s.run(() -> {
            given.clear();
            reactivations = wins = lineCrafts = 0;
            fuel = starveSecond = keepLine = false;
            campaignBefore = CampaignSuite.chapterSettings();
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.loadout.clear();
            r.tags.put(FirstAwakening.tagScenario, CentralControl.scenarioId);
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagResponse, "true");
            r.tags.put(AncientSite.tagEnabled, "true");
            StringBuilder g = new StringBuilder();
            for(int[] l : locks) g.append(g.length() == 0 ? "" : ";").append(l[0] + 1).append(',').append(l[1] + 1);
            r.tags.put(PlanetaryActivity.tagGates, g.toString());
            Worlds.generate(width, height, ControlSuite::generate, r);
            fixture = state.rules;
        });
        s.wait(0.5f);
        s.run(() -> {
            //no patrols, no pulse unless one is asked for, and no awakening response (the response suite's business)
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
            a.awakened = true;
        });
        s.check("control: the scenario binds to the world as a custom game, nothing done yet", () -> Vesper.scenario() == Vesper.central && Vesper.central.active()
            && Vesper.central.completed == 0 && controller() != null && !controller().known() && !state.isCampaign(), () -> "scenario " + Vesper.scenario());
        s.check("control: before the survey the controller draws nothing, takes no core, and the locks and sites are dark", () -> controller().demand() == 0f
            && !controller().acceptItem(null, resonanceCore) && locksOf().allMatch(l -> l.seal == 0f) && sitesOf().allMatch(x -> x.siteState == SiteState.unexplored),
            () -> "demand " + controller().demand());
        s.run(() -> controller().configure(Mode.recovery.ordinal()));
        s.wait(0.5f);
        s.check("control: a mode change before the survey is refused", () -> controller().mode == Mode.balanced, () -> controller().mode.name());

        // --- the survey, by a real rig with power and circuits
        s.run(() -> {
            feed(Worlds.at(dynamos[0][0], dynamos[0][1], 3), resonanceCore, 10);
            feed(world.build(rigX, rigY), recoveredCircuit, 40);
        });
        s.until("survey: a powered survey rig with circuits reveals the controller", () -> controller().known(), 90f,
            () -> "survey " + (int)(controller().investigation * 100) + "%");
        s.wait(0.5f);
        s.check("survey: the first objective is done; the junction opens for its materials, the wings stay sealed off until it is restored", () -> Vesper.central.completed >= 1
            && junction().siteState == SiteState.restoring && array().siteState == SiteState.investigated && line().siteState == SiteState.investigated
            && !array().acceptItem(null, structuralAlloy), () -> siteStates());
        s.check("survey: no rig can survey the sites themselves", () -> sitesOf().allMatch(x -> !x.surveyable()), () -> "");

        // --- power and cores: the standby load
        s.run(() -> {
            fuel = true;
            give(20);
            //the locks off for now: only the standby load
            locksOf().each(l -> l.configure(false));
        });
        s.wait(3f);
        s.check("power: once surveyed it draws a standby load, takes cores, and the second objective is done", () -> near(controller().demand(), 0.5f, 0.01f)
            && controller().power.status >= 0.99f && Vesper.central.completed >= 2, () -> "demand " + controller().demand() + ", status " + controller().power.status);

        // --- the junction: materials first, then the recovery protocol
        s.run(() -> {
            junction().handleStack(structuralAlloy, 80, null);
            junction().handleStack(recoveredCircuit, 30, null);
        });
        s.wait(1f);
        s.check("junction: materials short: not ready, the recovery bus has no work and says why", () -> !junction().ready() && controller().recoveryDemand() == 0f
            && controller().reason() != null, () -> controller().reason());
        s.run(() -> {
            junction().handleStack(recoveredCircuit, 10, null);
            coresBefore = recoveryCores();
        });
        s.wait(1f);
        s.check("junction: materials in: the recovery bus takes it (3/tick at full share) and pays a core", () -> junction().ready() && controller().target() == junction()
            && near(controller().recoveryDemand(), 3f, 0.01f) && recoveryCores() == coresBefore + 1 && controller().recoveryShare() >= 0.99f,
            () -> "demand " + controller().recoveryDemand() + ", share " + controller().recoveryShare() + ", cores " + recoveryCores());
        s.run(() -> progressBefore = junction().work);
        s.wait(5f);
        s.check("junction: work advances at the share, at the controller's own pace (5 s of full share; the basin's resonance does not speed it)",
            () -> near(junction().work - progressBefore, 5f * 60f, 30f), () -> "work " + Strings.fixed((junction().work - progressBefore) / 60f, 2) + " s at resonance x"
            + Strings.fixed(a.resonanceMultiplier(), 2));
        s.until("junction: restored after its 30 s of work; the wings open for their materials", () -> junction().siteState == SiteState.restored
            && array().siteState == SiteState.restoring && line().siteState == SiteState.restoring && controller().access() && Vesper.central.completed >= 3, 40f, () -> siteStates());
        s.check("junction: three cores paid its 30 s of full share (one per 10 s)", () -> recoveryCores() - coresBefore >= 3 && recoveryCores() - coresBefore <= 4,
            () -> (recoveryCores() - coresBefore) + " cores");

        // --- the containment bus: three locks, in order, with enough power
        s.run(() -> {
            locksOf().each(l -> l.configure(true));
            give(20);
        });
        s.until("locks: with the whole bus, every lock closes in its 10 s and the vents are shut", () -> locksOf().allMatch(LockBuild::sealed) && controller().contained(), 20f,
            () -> lockStates());
        s.check("locks: every vent shut once: the containment objective is reachable; a lock is paid one core per 20 s", () -> controller().containedOnce
            && controller().lockCores >= 3, () -> "lock cores " + controller().lockCores);
        s.check("vents: with every vent shut, no gate is open for a patrol or a pulse", () -> a.openGates().isEmpty(), () -> a.openGates().size + " open");

        // --- a short supply: one dynamo off, then each mode's split
        s.run(() -> {
            //the line's materials in, so recovery has work again; the second dynamo without fuel: one dynamo for a demand of 6.5
            for(var stack : ((ProtocolSite)line().block).restoration) line().handleStack(stack.item, stack.amount, null);
            starveSecond = true;
            controller().configure(Mode.containment.ordinal());
            give(25);
        });
        s.wait(8f);
        s.check("routing, containment first, short supply: the locks keep their whole share; recovery gets what is left", () -> controller().contained()
            && near(controller().toContainment, 3f, 0.05f) && controller().recoveryShare() < 0.9f && controller().recoveryShare() > 0.1f,
            () -> routing());
        s.run(() -> controller().configure(Mode.balanced.ordinal()));
        s.wait(10f);
        s.check("routing, balanced, short supply: both in proportion: some locks short (open), recovery between", () -> locksOf().count(LockBuild::sealed) >= 1
            && locksOf().count(LockBuild::sealed) < 3 && controller().recoveryShare() > 0.5f && controller().recoveryShare() < 1f, () -> routing() + " | " + lockStates());
        s.run(() -> {
            controller().configure(Mode.recovery.ordinal());
            shareBefore = controller().recoveryShare();
        });
        s.wait(10f);
        s.check("routing, recovery first: overdriven to 1.5 times its demand, it takes the whole short supply; every vent opens", () -> controller().recoveryShare() > 1.2f
            && locksOf().count(LockBuild::sealed) == 0 && near(controller().recoveryDemand(), 4.5f, 0.01f), () -> routing() + " | " + lockStates());
        s.check("routing, recovery first: the overdriven share works at the lower efficiency (speed below the share) and is loud", () -> controller().speed(1.5f) < 1.5f
            && controller().speed(1.5f) > 1.3f && controller().speed(1f) == 1f && near(controller().activity(), 6f * 1.5f + 12f * 0.5f, 0.3f),
            () -> "speed at 1.5: " + controller().speed(1.5f) + ", activity " + controller().activity());
        // --- the ramp: a change of mode is never instant
        s.run(() -> controller().configure(Mode.containment.ordinal()));
        s.wait(1f);
        s.check("ramp: one second after switching to containment the bus has not swung yet, no vent is shut", () -> controller().toContainment < 2.5f
            && locksOf().count(LockBuild::sealed) == 0, () -> routing());
        s.until("ramp: the bus swings (5 s at most), then the locks take their 10 s: shut again", () -> locksOf().allMatch(LockBuild::sealed), 25f, () -> routing());

        // --- a lock switched off: its share goes to recovery; one without cores opens
        s.run(() -> {
            shareBefore = controller().recoveryShare();
            lock(0).configure(false);
        });
        s.wait(6f);
        s.check("locks: one switched off (a tap) opens its vent; its share goes to recovery", () -> !lock(0).sealed() && !lock(0).online
            && controller().recoveryShare() > shareBefore + 0.2f && a.openGates().size == 1, () -> routing() + " | " + lockStates());
        s.run(() -> lock(0).configure(true));
        s.waitFor(() -> lock(0).sealed(), 20f);
        s.run(() -> {
            //the controller's store emptied (the cores taken back by hand, counted out)
            int n = controller().items.get(resonanceCore);
            controller().items.remove(resonanceCore, n);
            given.increment(0, -n);
        });
        s.waitFor(() -> locksOf().count(LockBuild::sealed) == 0, 25f);
        s.check("locks: with no core to pay its time, a sealed lock opens when its paid time runs out", () -> locksOf().count(LockBuild::sealed) == 0, () -> lockStates());
        s.wait(6f);
        s.check("locks: and with no core it does not start closing again (powered, but nothing to pay with)", () -> locksOf().allMatch(l -> l.seal == 0f && l.fed()),
            () -> lockStates());

        // --- back to full power; the line goes on first (its materials were in first), then the array
        s.run(() -> {
            starveSecond = false;
            give(30);
            //the array's materials, whatever they are (what belts would bring)
            for(var stack : ((ProtocolSite)array().block).restoration) array().handleStack(stack.item, stack.amount, null);
            controller().configure(Mode.balanced.ordinal());
        });
        s.wait(1f);
        s.check("order: both wings ready, the recovery bus stays on the one whose materials were in first (the line)", () -> line().siteState == SiteState.restored
            || (controller().target() == line() && array().ready()), () -> "target " + (controller().target() == null ? "-" : controller().target().block.name) + ", " + siteStates());
        s.check("objectives: every vent shut once is not enough before the subsystems: the objectives go in order", () -> Vesper.central.completed == 3, () -> "" + Vesper.central.completed);
        s.until("line: restored first", () -> line().siteState == SiteState.restored, 120f, () -> siteStates());
        s.run(() -> {
            give(30);
            keepLine = true;
        });
        s.until("line: with power, scrap and crystal it makes resonance cores (into the dynamo beside it)", () -> lineCrafts >= 2, 30f, () -> siteStates() + ", line " + line().items);
        s.run(() -> give(30));
        s.until("array: restored next by the recovery protocol; its power feeds the controller's grid", () -> array().siteState == SiteState.restored
            && array().getPowerProduction() > 2f && array().power.graph == controller().power.graph, 120f, () -> siteStates());
        s.check("array: a running resonance machine for the planet (a pulse can aim at it) and a source of activity", () -> array().activity() > 0f
            && PlanetaryActivity.resonant.contains(conductionArray) && array().operational(), () -> "activity " + array().activity());
        s.check("subsystems: both restored: the fourth objective is done; the fifth was already met (every vent shut once)", () -> Vesper.central.completed >= 5,
            () -> "" + Vesper.central.completed);

        // --- pulses aim only through open vents
        s.run(() -> {
            keepLine = false;
            line().items.clear();
            locksOf().each(l -> l.configure(false));
            holdShock = 60f;
            a.pulseCooldown = 0f;
        });
        s.until("pulse: with every vent open, announced at the resonance machine nearest a vent (the array, under the middle vent)", () -> a.pulsePending > 0f, 20f,
            () -> "level " + (int)a.level);
        s.check("pulse: it aims through the middle vent, at the array", () -> a.pulseGateX == locks[1][0] + 1 && Worlds.at(arrayX, arrayY, 4).within(a.pulseX * tilesize,
            a.pulseY * tilesize, 3f * tilesize), () -> "vent " + a.pulseGateX + "," + a.pulseGateY + ", epicentre " + a.pulseX + "," + a.pulseY);
        // --- the middle lock closes before it lands: contained
        s.run(() -> {
            pulsesBefore = a.pulses;
            containedBefore = a.pulsesContained;
            coresBefore = lock(1).coresSpent;
            give(20);
            lock(1).configure(true);
            controller().configure(Mode.containment.ordinal());
        });
        s.waitFor(() -> a.pulsePending < 0f, 30f);
        s.check("pulse: the lock closed in time: the pulse is held back at the vent (paid 6 cores), nothing lands", () -> a.pulsesContained == containedBefore + 1
            && a.pulses == pulsesBefore && a.pulseActive < 0f && lock(1).coresSpent >= coresBefore + 6 && lock(1).contained == 1,
            () -> "contained " + a.pulsesContained + ", pulses " + a.pulses + ", lock cores " + (lock(1).coresSpent - coresBefore));
        // --- the next pulse aims through another open vent: the line (running again), under the west one
        s.run(() -> {
            keepLine = true;
            a.pulseCooldown = 0f;
        });
        s.until("pulse: with the middle vent shut, the next aims through the west vent at the line", () -> a.pulsePending > 0f && a.pulseGateX == locks[0][0] + 1
            && line().within(a.pulseX * tilesize, a.pulseY * tilesize, 3f * tilesize), 20f, () -> "vent " + a.pulseGateX + "," + a.pulseGateY + ", epicentre "
            + a.pulseX + "," + a.pulseY);
        s.run(() -> {
            //the sequence can start now (every vent was shut once, both subsystems restored): start it before the pulse lands
            controller().configure(true);
            give(20);
        });
        s.waitFor(() -> controller().sequence == ProtocolController.Sequence.running, 5f);
        s.check("sequence: starts once the subsystems are restored and every vent was shut once", () -> controller().sequence == ProtocolController.Sequence.running,
            () -> controller().sequence.name());
        s.until("pulse: it lands on the line: the line stops while it lasts", () -> a.pulseActive > 0f && !line().operational(), 30f, () -> siteStates());
        s.run(() -> {
            progressBefore = controller().progress;
            coresBefore = recoveryCores();
        });
        s.wait(5f);
        s.check("sequence: a subsystem stopped: the sequence stands still (no progress, no core paid) and says why", () -> controller().progress == progressBefore
            && recoveryCores() == coresBefore && controller().stalls >= 1 && controller().reason() != null && !controller().integrity(),
            () -> "progress " + controller().progress + ", reason " + controller().reason());
        s.run(() -> holdShock = -1f);
        s.waitFor(() -> a.pulseActive < 0f, 30f);
        s.until("sequence: the pulse over and the line running, it advances again", () -> controller().integrity() && controller().progress > progressBefore + 0.01f, 20f,
            () -> "progress " + controller().progress + ", " + siteStates());

        // --- an announced patrol with every vent shut
        s.run(() -> {
            give(20);
            locksOf().each(l -> l.configure(true));
        });
        s.waitFor(() -> locksOf().allMatch(LockBuild::sealed), 25f);
        s.run(() -> {
            containedBefore = a.patrolsContained;
            pulsesBefore = state.enemies;
            a.pendingCrawlers = 2;
            a.pendingSentinels = 1;
            a.pending = 60f;
        });
        s.wait(3f);
        s.check("patrol: announced with every vent shut, it never climbs out", () -> a.patrolsContained == containedBefore + 1 && state.enemies == pulsesBefore,
            () -> "contained " + a.patrolsContained + ", enemies " + state.enemies + " (before " + pulsesBefore + ")");

        // --- save and load in the middle of a mode change and of the sequence
        s.run(() -> {
            controller().configure(Mode.recovery.ordinal());
            give(15);
        });
        s.wait(1.5f);
        s.run(() -> {
            before = saved();
            sealedBefore = locksOf().select(LockBuild::sealed).map(l -> l.pos()).toString();
            Fi file = h.out.child("control-mid.msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            fixture = state.rules;
            state.set(State.playing);
            h.check("persistence: mid-sequence and mid-swing, the routing, the locks, the sites and the objectives survive save and load", saved().equals(before),
                "before " + before + "\nafter  " + saved());
        });
        s.wait(1f);
        s.check("persistence: after loading the routing goes on in the saved mode; the locks shut before the save are still shut", () -> controller().mode == Mode.recovery
            && locksOf().select(LockBuild::sealed).map(l -> l.pos()).toString().equals(sealedBefore), () -> routing() + " | " + lockStates() + " | before " + sealedBefore);

        // --- the completion
        s.run(() -> {
            controller().configure(Mode.balanced.ordinal());
            give(30);
            //no more pulses for the rest of the run (the pulse checks are done)
            a.pulseCooldown = 1e9f;
        });
        s.step(d -> {
            //keep the controller in cores, as a belt would
            if(controller().items.get(resonanceCore) < 10) give(10);
            return controller().sequence == ProtocolController.Sequence.complete || state.gameOver;
        });
        s.wait(2f);
        s.check("victory: the sequence completes once; the custom game is won, once; every objective done", () -> reactivations == 1 && wins == 1 && Vesper.central.victory
            && Vesper.central.completed == 6, () -> "reactivations " + reactivations + ", wins " + wins);
        s.check("victory: a custom game touches no campaign progress", () -> CampaignSuite.chapterSettings().equals(campaignBefore), () -> CampaignSuite.chapterSettings());
        s.run(() -> {
            coresBefore = recoveryCores();
            controller().configure(true);
        });
        s.wait(10f);
        s.check("after: the recovery bus is done (no more cores, no work); a new start changes nothing", () -> recoveryCores() == coresBefore
            && controller().recoveryDemand() == 0f && controller().sequence == ProtocolController.Sequence.complete && reactivations == 1, () -> routing());
        s.check("cores: every core put into the controller is still in it or was burned by a bus", () -> controller().items.get(resonanceCore) + controller().recoveryCores
            + controller().lockCores == given.get(0, 0), () -> "held " + controller().items.get(resonanceCore) + " + recovery " + controller().recoveryCores + " + locks "
            + controller().lockCores + " vs given " + given.get(0, 0));
        s.run(() -> {
            before = saved();
            Fi file = h.out.child("control-done.msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            fixture = state.rules;
            state.set(State.playing);
        });
        s.wait(3f);
        s.check("persistence: a reactivated complex loads reactivated, and wins nothing again", () -> controller().sequence == ProtocolController.Sequence.complete
            && Vesper.central.victory && reactivations == 1 && wins == 1, () -> saved());
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
        place(tiles, centralController, controllerX, controllerY);
        place(tiles, surveyRig, rigX, rigY);
        for(int[] d : dynamos) place(tiles, resonanceDynamo, d[0], d[1]);
        place(tiles, processingLine, lineX, lineY);
        place(tiles, conductionArray, arrayX, arrayY);
        place(tiles, distributionJunction, junctionX, junctionY);
        for(int[] l : locks) place(tiles, containmentLock, l[0], l[1]);
    }

    static void place(Tiles tiles, Block block, int left, int bottom){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, 0);
    }

    static @Nullable ControllerBuild controller(){
        return ProtocolController.find(team);
    }

    static ProtocolSiteBuild junction(){
        return (ProtocolSiteBuild)Worlds.at(junctionX, junctionY, 3);
    }

    static ProtocolSiteBuild array(){
        return (ProtocolSiteBuild)Worlds.at(arrayX, arrayY, 4);
    }

    static ProtocolSiteBuild line(){
        return (ProtocolSiteBuild)Worlds.at(lineX, lineY, 4);
    }

    static Seq<ProtocolSiteBuild> sitesOf(){
        return Seq.with(junction(), array(), line());
    }

    static LockBuild lock(int i){
        return (LockBuild)Worlds.at(locks[i][0], locks[i][1], 3);
    }

    static Seq<LockBuild> locksOf(){
        return Seq.with(lock(0), lock(1), lock(2));
    }

    static ResonanceDynamoBuild dynamo(int i){
        return (ResonanceDynamoBuild)Worlds.at(dynamos[i][0], dynamos[i][1], 3);
    }

    static void feed(Building b, mindustry.type.Item item, int amount){
        b.handleStack(item, amount, null);
    }

    /** Cores put into the controller by hand (what a belt would bring), counted for the conservation check. */
    void give(int amount){
        int room = Math.min(amount, controller().block.itemCapacity - controller().items.get(resonanceCore));
        if(room <= 0) return;
        controller().handleStack(resonanceCore, room, null);
        given.increment(0, room);
    }

    static int recoveryCores(){
        return controller().recoveryCores;
    }

    static boolean near(float a, float b, float eps){
        return Math.abs(a - b) <= eps;
    }

    static String routing(){
        return routingOf(controller());
    }

    /** A controller's routing at a glance: the mode, the power, the split. */
    public static String routingOf(@Nullable ControllerBuild c){
        if(c == null) return "no controller";
        return "mode " + c.mode + ", status " + Strings.fixed(c.power.status, 2) + ", delivered " + Strings.fixed(c.delivered(), 2) + " (demand "
            + Strings.fixed(c.containmentDemand(), 2) + " + " + Strings.fixed(c.recoveryDemand(), 2) + "), to containment " + Strings.fixed(c.toContainment, 2)
            + ", recovery share " + Strings.fixed(c.recoveryShare(), 2);
    }

    static String lockStates(){
        return lockStatesOf(controller());
    }

    public static String lockStatesOf(@Nullable ControllerBuild c){
        if(c == null) return "";
        StringBuilder out = new StringBuilder();
        for(LockBuild l : c.locks()) out.append(l.tileX()).append(' ').append(l.online ? "on" : "off").append(" supply ").append(Strings.fixed(l.supply, 2))
            .append(" seal ").append(Strings.fixed(l.seal, 2)).append("; ");
        return out.toString();
    }

    static String siteStates(){
        return siteStatesOf(controller());
    }

    public static String siteStatesOf(@Nullable ControllerBuild c){
        if(c == null) return "";
        StringBuilder out = new StringBuilder();
        for(ProtocolSiteBuild x : c.sites()) out.append(x.block.name.replace("vesper-", "")).append(' ').append(x.siteState).append(" mat ")
            .append((int)(x.restoration() * 100)).append("% work ").append((int)(x.workFraction() * 100)).append("%; ");
        return out.toString();
    }

    /** What must survive a save: the routing, the sequence, every lock and site, the scenario's progress. */
    static String saved(){
        var c = controller();
        if(c == null) return "no controller";
        StringBuilder out = new StringBuilder().append(c.siteState).append(' ').append(c.mode).append(' ').append(c.sequence).append(' ')
            .append(Strings.fixed(c.progress, 4)).append(' ').append(Strings.fixed(c.toContainment, 2)).append(' ').append(c.recoveryCores).append(' ')
            .append(c.lockCores).append(' ').append(c.containedOnce).append(' ').append(c.items.get(resonanceCore));
        for(LockBuild l : locksOf()) out.append(" | ").append(l.online).append(' ').append(Strings.fixed(l.seal, 2)).append(' ').append(l.coresSpent);
        for(ProtocolSiteBuild x : sitesOf()) out.append(" | ").append(x.siteState).append(' ').append((int)x.work).append(' ').append(x.restoration());
        return out.append(" | objectives ").append(Vesper.central.completed).append(' ').append(Vesper.central.victory).toString();
    }
}
