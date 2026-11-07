package vesper.harness.suites;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import vesper.*;
import vesper.campaign.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.harness.maps.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.ContainmentLock.*;
import vesper.world.ProtocolController.*;
import vesper.world.ProtocolSite.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Central Control played start to finish on the shipped map by the scripted {@link Bot}, without cheats, on a common
 * base: drill-fed turrets in the bay and at the chamber's north passage; a power plant of six carbon drills and
 * engines in the conduction gallery, its power carried by a chain of links to the controller and on to the
 * processing hall; a survey rig beside the controller; a materials line in the bay (a forge and an assembler, alloy
 * and circuits into the core); the core line of the processing hall (as in the Underworks) with its cores belted to
 * the controller; an unloader and a trunk from the core to the controller; the processing line's scrap and crystal
 * beside it and its cores belted to the controller once it is restored. The unit carries the sites' materials from the
 * core and, once something was rebuilt, lays its links again every 10 s (a rebuilt link only takes back the links it still
 * had when it fell, so a chain lost piece by piece comes back cut: a player sees the controller starve and relinks it).
 * Then one of four ways to route the protocols:
 * <ul>
 * <li><b>conservative</b>: containment first all along; the three locks shut from the moment the basin stirs
 * (activity 40), and a stabilizer on its own engine;</li>
 * <li><b>aggressive</b>: recovery first (overdriven) all along; the locks only for the containment objective; two
 * drill-fed turrets in each wing from the start, for what climbs out of the open vents;</li>
 * <li><b>balanced</b>: in proportion, the three locks shut from the survey on; three more carbon drills and engines
 * and two power accumulators: the investment in power to run both at full;</li>
 * <li><b>opportunist</b> (diagnostic): recovery first, and containment with every lock on only while a pulse or a
 * patrol is announced or running, back to recovery once it is over: does switching beat committing? And
 * <b>opportunist-slow</b>, the same reacting 12 s late (8 s before what was announced lands): is the gain the attention?</li>
 * <li><b>economic</b> (diagnostic): the common base with the smallest core line (the resonator and its extractors on the
 * processing hall's two engines, its circuits carried by the unit: no forge or assembler in the hall), played to spare
 * cores: the line's materials first, balanced (never overdriven), the locks only for the objective and while something is
 * announced. Without any core line, 20 loadout cores paid the junction and the processing line, and the line the rest.</li>
 * </ul>
 * Each strategy must reach the native victory (a game over in a custom game, the sector's capture in the campaign);
 * the suite reports what each got and paid: the time, the protocols' cores, the locks' and the line's, the routing
 * changes, the sequence's stalls, the level, pulses (landed and held back) and patrols (and held back), losses and the
 * accounts. No time is imposed; the comparison is the result.
 */
public class ControlPlaySuite implements VesperHarness.Suite{
    final String mode, tag;
    final boolean campaign;
    final Bot bot = new Bot();
    final Ledger ledger = new Ledger();
    final ObjectIntMap<String> lost = new ObjectIntMap<>();
    boolean listening, routing, carrying;
    float ticks, sampleTimer, levelSum, maxLevel, surveyedAt, sequenceAt, sequenceEnd, wireTimer;
    int switches, relinked;
    /** The links laid so far, laid again by {@link #tendLinks()}. */
    final Seq<Runnable> wiring = new Seq<>();

    /** @param mode conservative, aggressive, balanced or opportunist */
    public ControlPlaySuite(String mode, boolean campaign){
        this.mode = mode;
        this.campaign = campaign;
        tag = "control-" + mode;
    }

    public ControlPlaySuite(String mode){
        this(mode, false);
    }

    boolean won(VesperHarness h){
        return campaign ? state.rules.sector != null && state.rules.sector.isCaptured() && Vesper.central.victory : h.lastWinner == state.rules.defaultTeam;
    }

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            Events.run(Trigger.update, this::supplyRig);
            Events.run(Trigger.update, this::route);
            Events.run(Trigger.update, this::supplyResonator);
            Events.run(Trigger.update, this::supplySites);
            Events.run(Trigger.update, this::tendLinks);
            Events.on(BlockDestroyEvent.class, e -> {
                if(bot.active && e.tile.team() == state.rules.defaultTeam) lost.increment(e.tile.block().name);
            });
            Events.run(Trigger.update, () -> {
                if(!bot.active || !state.isPlaying()) return;
                ticks += Time.delta;
                var a = Vesper.activity;
                levelSum += a.level * Time.delta;
                maxLevel = Math.max(maxLevel, a.level);
                if((sampleTimer += Time.delta) >= 30f * 60f){
                    sampleTimer = 0f;
                    var c = controller();
                    Log.info("[vesper-harness] @ t=@ wave @ activity @ (@) industry @ ancient @ stabilization @ | @ | enemies @ pulses @ (held @) patrols @ (held @) | core @",
                        tag, minutes(), state.wave, (int)a.level, a.stage, (int)a.industry, (int)a.ancient, Strings.fixed(a.stabilization, 1),
                        c == null ? "-" : ControlSuite.routingOf(c) + " | " + ControlSuite.siteStatesOf(c), state.enemies, a.pulses, a.pulsesContained, a.patrols,
                        a.patrolsContained, coreSummary());
                }
            });
        }
        var a = Vesper.activity;

        s.run(() -> {
            lost.clear();
            routing = carrying = false;
            switches = relinked = 0;
            wiring.clear();
            ticks = levelSum = maxLevel = sampleTimer = surveyedAt = sequenceAt = sequenceEnd = wireTimer = 0f;
            if(!campaign) Worlds.play(Worlds.bundled(ContentSuite.controlName), Gamemode.survival);
            bot.active = true;
            bot.rebuild = true;
        });
        s.wait(0.5f);
        s.check(tag + ": no cheat, no infinite resources", () -> !state.rules.infiniteResources && !state.rules.teams.get(state.rules.defaultTeam).cheat, () -> "");
        s.check(tag + ": the map loads as authored", () -> Worlds.foreignTiles() == 0 && state.rules.winWave == 0 && state.rules.waves && state.rules.lighting,
            () -> Worlds.foreignTiles() + " foreign tiles");
        s.check(tag + ": scenario, activity and planetary response active" + (campaign ? ", in the Central Control sector" : "; not a campaign sector"),
            () -> Vesper.scenario() == Vesper.central && a.active() && a.responses() && controller() != null && controller().locks().size == 3
            && (campaign ? state.rules.sector == VSectors.centralControl.sector : state.rules.sector == null), () -> "");
        s.check(tag + ": starting loadout", () -> core(ferroprism) == 1500 && core(resonanceCore) == 6 && core(recoveredCircuit) == 240 && core(structuralAlloy) == 400
            && core(resonanceCrystal) == 40, () -> coreSummary());
        s.run(ledger::begin);

        // --- defense: drill-fed turrets in the bay and at the chamber's north passage (where the waves come down)
        phase(h, s, "drill-fed turrets in the bay and at the chamber's north passage", () -> {
            bot.build(surveyDrill, 58, 6);
            bot.build(surveyDrill, 60, 6);
            bot.build(kineticTurret, 58, 8);
            bot.build(kineticTurret, 60, 8);
            bot.build(surveyDrill, 66, 66);
            bot.build(surveyDrill, 68, 66);
            bot.build(kineticTurret, 64, 66);
            bot.build(kineticTurret, 70, 66);
            bot.build(kineticTurret, 66, 68);
            bot.build(kineticTurret, 68, 68);
        }, 360f);

        //the aggressive base leaves the vents open: what climbs out of them meets turrets in both wings from the start (no crystal
        //reaches the core for pulse turrets: kinetic turrets fed by drills on the wings' ferroprism)
        if(mode.equals("aggressive")) phase(h, s, "two drill-fed turrets in each wing", () -> {
            bot.build(surveyDrill, 28, 32);
            bot.build(kineticTurret, 30, 32);
            bot.build(kineticTurret, 28, 34);
            bot.build(surveyDrill, 128, 40);
            bot.build(kineticTurret, 130, 40);
            bot.build(kineticTurret, 128, 42);
        }, 360f);

        // --- the power plant in the conduction gallery and the chain to the controller
        phase(h, s, "six carbon drills and engines in the conduction gallery", () -> {
            for(int x : new int[]{8, 10, 12}){
                bot.build(surveyDrill, x, 30);
                bot.build(combustionEngine, x, 28);
                bot.build(surveyDrill, x, 36);
                bot.build(combustionEngine, x, 38);
            }
        }, 480f);
        phase(h, s, "a chain of links from the plant to the controller and on to the processing hall", () -> {
            for(int[] l : chain) bot.build(resonanceLink, l[0], l[1]);
        }, 600f);
        s.run(() -> wire(this::linkChain));

        // --- the survey: a rig beside the controller, its circuits carried by the unit (see supplyRig)
        phase(h, s, "a survey rig beside the controller", () -> bot.build(surveyRig, 74, 49), 240f);
        s.run(() -> ledger.mark("rig up"));

        // --- the materials line in the bay: alloy and circuits into the core (the subsystems' materials)
        phase(h, s, "the bay's materials line (a forge, a router, an assembler and its scrap collectors) and ferroprism drills, belts into the core", () -> {
            bot.build(surveyDrill, 74, 6);
            bot.build(surveyDrill, 76, 4);
            bot.build(surveyDrill, 78, 6);
            bot.build(structuralForge, 76, 6);
            bot.build(transitBelt, 76, 8, 1);
            bot.build(cargoRouter, 76, 9);
            bot.build(componentAssembler, 75, 10);
            bot.build(scrapCollector, 78, 10);
            bot.build(scrapCollector, 78, 12);
            bot.line(transitBelt, 72, 9, 75, 9, 2);
            bot.build(transitBelt, 71, 9, 1);
            bot.build(transitBelt, 71, 10, 1);
            bot.build(surveyDrill, 58, 18);
            bot.build(combustionEngine, 58, 20);
            for(int[] l : bayLinks) bot.build(resonanceLink, l[0], l[1]);
            //ferroprism into the core: two drills on the bay's east pocket and a belt round to the core (the rest of the base eats what it mines)
            bot.build(surveyDrill, 81, 18);
            bot.build(surveyDrill, 83, 18);
            bot.line(transitBelt, 73, 17, 84, 17, 2);
            bot.line(transitBelt, 72, 14, 72, 17, 3);
            bot.build(transitBelt, 72, 13, 2);
        }, 600f);
        s.run(() -> wire(this::linkBay));

        // --- the processing hall's core line (as in the Underworks), on its own engines and the chain; the economic one keeps only the
        //engines, and the unit carries its resonator's circuits (see supplyResonator)
        if(mode.equals("economic")) phase(h, s, "two engines on the processing hall's carbon", () -> {
            bot.build(surveyDrill, 106, 38);
            bot.build(combustionEngine, 106, 40);
            bot.build(combustionEngine, 104, 38);
        }, 480f);
        else phase(h, s, "the processing hall's core line: forge, assembler, collectors, two engines on their carbon", () -> {
            bot.build(surveyDrill, 106, 38);
            bot.build(combustionEngine, 106, 40);
            bot.build(combustionEngine, 104, 38);
            bot.build(surveyDrill, 110, 32);
            bot.build(surveyDrill, 112, 30);
            bot.build(surveyDrill, 114, 32);
            bot.build(structuralForge, 112, 32);
            bot.build(componentAssembler, 112, 34);
            bot.build(scrapCollector, 110, 34);
            bot.build(scrapCollector, 110, 36);
        }, 480f);
        phase(h, s, "the resonator, its crystal extractors and belt, the core belt to the controller", () -> {
            bot.build(experimentalResonator, 115, 34);
            for(int x : new int[]{121, 123, 125}) bot.build(crystalExtractor, x, 32);
            bot.line(transitBelt, 118, 34, 126, 34, 2);
            bot.line(transitBelt, 116, 37, 116, 43, 1);
            bot.line(transitBelt, 75, 44, 116, 44, 2);
            bot.line(transitBelt, 74, 44, 74, 47, 1);
            bot.build(transitBelt, 74, 48, 2);
            bot.build(transitBelt, 73, 48, 2);
        }, 600f);
        s.run(() -> wire(this::linkHall));
        s.until(tag + ": the survey rig reveals the controller; the junction opens", () -> controller().known() && Vesper.central.completed >= 1, 300f,
            () -> "survey " + (int)(controller().investigation * 100) + "%");
        s.run(() -> {
            surveyedAt = ticks;
            ledger.mark("surveyed");
            //the protocols wake with every lock on: the routing hook decides which stay on
            routing = true;
        });

        // --- cores to the controller: the unloader and the trunk (the loadout's cores; the resonator's and the line's are belted to it)
        phase(h, s, "an unloader by the core and the trunk up the gallery into the controller", () -> {
            bot.build(cargoUnloader, 68, 12, 0, resonanceCore);
            bot.build(transitBelt, 67, 12, 2);
            bot.line(transitBelt, 66, 12, 66, 47, 1);
            bot.build(transitBelt, 66, 48, 0);
            bot.build(transitBelt, 67, 48, 0);
        }, 600f);
        if(mode.equals("balanced")){
            //the investment in power that needs no cores: three more carbon drills and engines, two accumulators by the controller
            phase(h, s, "three more drills and engines on the conduction gallery's north carbon, two accumulators by the controller", () -> {
                for(int x : new int[]{10, 12, 14}){
                    bot.build(surveyDrill, x, 57);
                    bot.build(combustionEngine, x, 59);
                }
                bot.build(resonanceLink, 18, 53);
                bot.build(powerAccumulator, 63, 47);
                bot.build(powerAccumulator, 63, 49);
            }, 600f);
            s.run(() -> wire(() -> {
                FaultSuite.link(18, 53, 14, 59, 2);
                FaultSuite.link(18, 53, CentralControlMap.arrayX, CentralControlMap.arrayY, 4);
            }));
        }
        s.until(tag + ": the controller is powered and holds cores", () -> Vesper.central.completed >= 2, 240f, () -> ControlSuite.routingOf(controller()));

        // --- the junction: its materials carried from the core, then the recovery protocol
        s.run(() -> carrying = true);
        s.until(tag + ": the junction restored (access to the wings)", () -> controller().access() && Vesper.central.completed >= 3, 600f,
            () -> ControlSuite.siteStatesOf(controller()) + " | " + ControlSuite.routingOf(controller()));
        s.run(() -> ledger.mark("junction restored"));

        // --- the processing line's supply and output, the array on the chain, each strategy's own blocks
        phase(h, s, "the processing line's scrap and crystal beside it, its cores belted to the controller, the array on the chain", () -> {
            bot.build(scrapCollector, 115, 46);
            bot.build(scrapCollector, 115, 48);
            bot.build(crystalExtractor, 121, 46);
            bot.build(crystalExtractor, 121, 48);
            bot.build(transitBelt, 118, 50, 1);
            bot.build(transitBelt, 118, 51, 1);
            bot.line(transitBelt, 73, 52, 118, 52, 2);
            bot.build(resonanceLink, 119, 44);
        }, 600f);
        s.run(() -> wire(this::linkSites));
        if(mode.equals("conservative")){
            phase(h, s, "a stabilizer on the plant's grid in the conduction gallery", () -> bot.build(stabilizerPylon, 16, 28), 360f);
            s.run(() -> wire(() -> FaultSuite.link(14, 33, 16, 28, 2)));
        }

        // --- the subsystems: their materials carried from the core as the industry makes them; the recovery protocol restores them
        s.until(tag + ": both subsystems restored", () -> controller().restored() && Vesper.central.completed >= 4, 1800f,
            () -> ControlSuite.siteStatesOf(controller()) + " | " + ControlSuite.routingOf(controller()) + " | core " + coreSummary());
        s.run(() -> ledger.mark("subsystems restored"));
        //the containment objective: every lock on until every vent is shut once (the routing hook goes back to the strategy after)
        s.until(tag + ": every vent shut at once", () -> controller().containedOnce && Vesper.central.completed >= 5, 300f,
            () -> ControlSuite.routingOf(controller()) + " | " + ControlSuite.lockStatesOf(controller()));
        s.run(() -> {
            sequenceAt = ticks;
            ledger.mark("sequence started");
            controller().configure(true);
            h.line("phase: reactivation sequence (" + tag + ", t=" + minutes() + ", activity " + (int)a.level + ", core: " + coreSummary() + ")");
        });
        s.until(tag + ": the reactivation sequence starts", () -> controller().sequence != ProtocolController.Sequence.idle, 30f, () -> ControlSuite.routingOf(controller()));
        h.shot(tag + "-01-chamber", 70, 50, 2f, true);

        s.step(d -> won(h) || h.lastWinner != null || ticks - sequenceAt > 40f * 60f * 60f);
        s.until(tag + (campaign ? ": VICTORY - the game captures the Central Control sector" : ": VICTORY - the game declares Central Control won"), () -> won(h), 1f,
            () -> ControlSuite.routingOf(controller()) + " | objectives " + Vesper.central.completed + ", core "
                + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + " hp"));
        s.check(tag + ": the complex is reactivated and the mission complete", () -> controller().sequence == ProtocolController.Sequence.complete && Vesper.central.victory
            && Vesper.central.completed == 6, () -> "");
        if(!campaign) s.check(tag + ": a custom game's victory leaves the campaign alone", () -> !Chapter.secured(VSectors.centralControl) && state.rules.sector == null
            && !Chapter.Record.partialReactivation.found(), () -> "");
        s.run(() -> {
            sequenceEnd = ticks;
            var c = controller();
            int lockCores = 0, contained = 0;
            for(LockBuild l : c.locks()){
                lockCores += l.coresSpent;
                contained += l.contained;
            }
            h.line("info: " + tag + (won(h) ? " won after " : " did NOT win; stopped after ") + minutes() + " of game time; surveyed at " + Strings.fixed(surveyedAt / 3600f, 1) + "min, sequence "
                + Strings.fixed((sequenceEnd - sequenceAt) / 3600f, 2) + " min (180 s at full share), stalls " + c.stalls + " (" + Strings.fixed(c.stalled, 1) + " s), routing changes "
                + switches + " (" + c.modeChanges + " mode changes)");
            h.line("info: " + tag + " cores: recovery " + c.recoveryCores + ", locks " + lockCores + " (held back " + contained + " pulses), dynamos " + DeepPlaySuite.burned(false)
                + ", fields " + DeepPlaySuite.fieldSpent() + "; line made " + lineMade() + "; mean activity " + Strings.fixed(levelSum / ticks, 1) + " (peak " + (int)maxLevel
                + "), pulses " + a.pulses + " (held back " + a.pulsesContained + "), patrols " + a.patrols + " (held back " + a.patrolsContained + "), awakened " + a.awakened
                + ", waves met " + (state.wave - 1) + ", core " + (state.rules.defaultTeam.core() == null ? "lost" : (int)state.rules.defaultTeam.core().health + "/"
                + (int)state.rules.defaultTeam.core().maxHealth));
            h.line("info: " + tag + " buildings lost " + lost + ", rebuilt " + bot.rebuilt + ", links laid again " + relinked);
            h.line("info: " + tag + " infrastructure: " + FaultSuite.infrastructure());
            ledger.mark("victory");
            h.line("info: " + tag + " ledger: " + ledger.report());
            for(var m : ledger.marks) h.line("info: " + tag + " at " + m.key + ": " + m.value);
            ledger.active = false;
        });
        h.shot(tag + "-02-victory", 70, 50, 2f, true);
        if(!campaign) s.run(() -> {
            bot.active = false;
            routing = carrying = false;
            var builds = state.rules.defaultTeam.data().buildings.copy();
            float[] frame = IndustryPerfSuite.time(3, 7, 10, () -> {
                for(int i = 0; i < builds.size; i++) builds.get(i).update();
            });
            var file = h.out.child(tag + "-base.msav");
            float[] save = IndustryPerfSuite.time(1, 5, 1, () -> mindustry.io.SaveIO.save(file));
            h.line("info: " + tag + " performance: the base's " + builds.size + " buildings update in " + IndustryPerfSuite.ms(frame[0]) + " a frame (min "
                + IndustryPerfSuite.ms(frame[1]) + "); saving it " + IndustryPerfSuite.ms(save[0]));
            h.check(tag + ": the whole base updates in under 4 ms a frame", frame[0] < 4f, IndustryPerfSuite.ms(frame[0]));
        });
        s.run(() -> {
            bot.active = false;
            routing = carrying = false;
            if(!headless && ui.restart.isShown()) ui.restart.hide();
        });
    }

    /** The strategy's routing, as a player sets it: the mode, and which locks are on. */
    void route(){
        if(!routing || !bot.active || !state.isPlaying()) return;
        var c = controller();
        if(c == null || !c.known() || c.sequence == ProtocolController.Sequence.complete) return;
        var a = Vesper.activity;
        boolean objective = c.restored() && !c.containedOnce;
        //the opportunists and the economic one only notice a warning 12 s after it shows (8 s before it lands) when slow
        float notice = mode.equals("opportunist-slow") ? 8f * 60f : 1e9f;
        boolean threat = (a.pulsePending > 0f && a.pulsePending <= notice) || a.pulseActive > 0f || (a.pending > 0f && a.pending <= notice);
        Mode want;
        boolean locks;
        switch(mode){
            case "conservative" -> {
                want = Mode.containment;
                locks = objective || a.level >= 40f || c.locks().contains(LockBuild::sealed) && a.level >= 30f;
            }
            case "aggressive" -> {
                want = Mode.recovery;
                locks = objective;
            }
            case "balanced" -> {
                want = Mode.balanced;
                locks = true;
            }
            case "economic" -> {
                //never overdriven (the overdrive pays 1.5 cores for 1.4 times the work); the locks only when they are needed
                want = Mode.balanced;
                locks = threat || objective;
            }
            default -> {
                //opportunist: containment while something is announced or running, recovery otherwise; is the advantage the
                //attention (the slow one), or the switch itself?
                want = threat || objective ? Mode.containment : Mode.recovery;
                locks = threat || objective;
            }
        }
        if(c.mode != want){
            c.configure(want.ordinal());
            switches++;
        }
        for(LockBuild l : c.locks()) if(l.online != locks) l.configure(locks);
    }

    /** Lays a set of links and keeps it for {@link #tendLinks()}. */
    void wire(Runnable links){
        wiring.add(links);
        links.run();
    }

    /** Every 10 s once something was rebuilt: the links of the plan laid again. */
    void tendLinks(){
        if(!bot.active || !state.isPlaying() || bot.rebuilt == 0 || (wireTimer += Time.delta) < 10f * 60f) return;
        wireTimer = 0f;
        int before = links();
        wiring.each(Runnable::run);
        relinked += links() - before;
    }

    /** The power links the team holds (each is kept at both of its ends). */
    static int links(){
        int n = 0;
        for(Building b : state.rules.defaultTeam.data().buildings) if(b.power != null) n += b.power.links.size;
        return n / 2;
    }

    /** The economic base has no assembler beside its resonator: the unit keeps it in circuits from the core. */
    void supplyResonator(){
        if(!mode.equals("economic") || !bot.active || !state.isPlaying() || bot.delivering() || !bot.idle()) return;
        if(!(Worlds.at(115, 34, 3) instanceof mindustry.world.blocks.production.GenericCrafter.GenericCrafterBuild res) || res.block != experimentalResonator) return;
        if(res.items.get(recoveredCircuit) >= 3 || core(recoveredCircuit) < 10) return;
        bot.deliver(res, recoveredCircuit, i -> 10 - res.items.get(recoveredCircuit));
    }

    /** Keeps the rig in circuits while the controller is unexplored. */
    void supplyRig(){
        if(!bot.active || !state.isPlaying()) return;
        var c = controller();
        if(!(Worlds.at(74, 49, 2) instanceof SurveyRig.SurveyRigBuild rig)) return;
        if(c == null || c.siteState != SiteState.unexplored || bot.delivering() || rig.items.get(recoveredCircuit) >= 3) return;
        bot.deliver(rig, recoveredCircuit, i -> controller() != null && controller().siteState == SiteState.unexplored ? 10 - rig.items.get(recoveredCircuit) : 0);
    }

    /** Carries each site's materials from the core, the junction first, then the array, then the line (what it still needs and the core has). */
    void supplySites(){
        if(!carrying || !bot.active || !state.isPlaying() || bot.delivering() || !bot.idle()) return;
        var c = controller();
        if(c == null) return;
        //the economic one brings the line's materials before the array's: the line makes the cores the rest needs
        for(ProtocolSiteBuild site : mode.equals("economic") ? c.sites().copy().sort(b -> b.block == processingLine ? 0 : b.block == distributionJunction ? -1 : 1) : c.sites()){
            if(site.siteState != SiteState.restoring || site.ready()) continue;
            for(ItemStack stack : ((ProtocolSite)site.block).restoration){
                int need = site.remaining(stack.item);
                //a trip only for what the core has (the unit loads what is still needed, up to a full stack); the line's crystal comes from its own extractors
                if(need > 0 && core(stack.item) >= Math.min(need, 30)){
                    bot.deliver(site, stack.item, item -> carrying ? site.remaining(item) : 0);
                    return;
                }
            }
        }
    }

    /** The links of the chain: from the conduction gallery's engines to the controller, and on from the controller to the processing hall. */
    static final int[][] chain = {{14, 33}, {20, 37}, {26, 41}, {32, 44}, {38, 47}, {44, 47}, {50, 47}, {56, 47}, {62, 47},
        {73, 49}, {79, 47}, {85, 47}, {91, 47}, {97, 47}, {103, 46}, {109, 45}, {109, 40}, {114, 40}, {121, 38}};
    static final int[][] bayLinks = {{63, 18}, {69, 16}, {75, 14}};

    void linkChain(){
        FaultSuite.link(14, 33, 12, 28, 2);
        FaultSuite.link(14, 33, 12, 38, 2);
        int[][] west = {{14, 33}, {20, 37}, {26, 41}, {32, 44}, {38, 47}, {44, 47}, {50, 47}, {56, 47}, {62, 47}};
        for(int i = 0; i + 1 < west.length; i++) FaultSuite.link(west[i][0], west[i][1], west[i + 1][0], west[i + 1][1], 1);
        FaultSuite.link(62, 47, CentralControlMap.controllerX, CentralControlMap.controllerY, 5);
        int[][] east = {{73, 49}, {79, 47}, {85, 47}, {91, 47}, {97, 47}, {103, 46}, {109, 45}, {109, 40}, {114, 40}, {121, 38}};
        for(int i = 0; i + 1 < east.length; i++) FaultSuite.link(east[i][0], east[i][1], east[i + 1][0], east[i + 1][1], 1);
    }

    void linkBay(){
        FaultSuite.link(63, 18, 58, 20, 2);
        FaultSuite.link(63, 18, 69, 16, 1);
        FaultSuite.link(69, 16, 75, 14, 1);
        FaultSuite.link(75, 14, 75, 10, 3);
    }

    void linkHall(){
        FaultSuite.link(109, 40, 106, 40, 2);
        FaultSuite.link(109, 40, 112, 34, 3);
        FaultSuite.link(114, 40, 115, 34, 3);
        for(int x : new int[]{121, 123, 125}) FaultSuite.link(121, 38, x, 32, 2);
    }

    void linkSites(){
        FaultSuite.link(26, 41, CentralControlMap.arrayX, CentralControlMap.arrayY, 4);
        FaultSuite.link(121, 38, 119, 44, 1);
        FaultSuite.link(119, 44, CentralControlMap.lineX, CentralControlMap.lineY, 4);
        FaultSuite.link(119, 44, 121, 46, 2);
        FaultSuite.link(119, 44, 121, 48, 2);
    }

    /** Cores the processing line has made (its crafts, counted by the ledger). */
    int lineMade(){
        return ledger.madeBy.get(processingLine, 0);
    }

    static @Nullable ControllerBuild controller(){
        return ProtocolController.find(state.rules.defaultTeam);
    }

    void phase(VesperHarness h, Script s, String name, Runnable queue, float timeout){
        s.run(() -> {
            queue.run();
            h.line("phase: " + name + " (" + tag + ", t=" + minutes() + ", wave " + state.wave + ", core: " + coreSummary() + ")");
        });
        s.until(tag + ": " + name, bot::idle, timeout, () -> bot.status + ", pending " + bot.pending() + ", t=" + minutes());
    }

    static int core(Item item){
        return state.rules.defaultTeam.core() == null ? -1 : state.rules.defaultTeam.core().items.get(item);
    }

    static String coreSummary(){
        return "F" + core(ferroprism) + " S" + core(conductiveScrap) + " A" + core(structuralAlloy) + " R" + core(recoveredCircuit) + " X" + core(resonanceCrystal)
            + " C" + core(resonanceCore);
    }

    String minutes(){
        return Strings.fixed(ticks / 3600f, 1) + "min";
    }
}
