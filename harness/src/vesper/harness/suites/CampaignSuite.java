package vesper.harness.suites;

import arc.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import vesper.*;
import vesper.campaign.*;
import vesper.campaign.Chapter.*;
import vesper.content.*;
import vesper.harness.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;

/**
 * Chapter One played as a campaign on the VESPER planet, in a profile of its own (run/campaign, see the
 * runCampaign task), through the game's own menus: the planet map, the sector, the launch button. Each phase is a
 * separate start of the client on the same profile, so what one phase leaves is what the player finds after
 * closing and reopening the game. The sectors are played by the bots of the custom-game suites, with the same
 * economy and win conditions; the campaign only changes how a sector is reached and what its capture means.
 * <ul>
 * <li>campaign-1: a clean profile; Resonance Fault locked; First Awakening played to its native capture, which
 * makes Resonance Fault and the resonance branch available.</li>
 * <li>campaign-2: after a restart, the progress is still there; Resonance Fault launched from the planet map (the
 * launch costs nothing) and played to its capture, which makes The Buried Foundry and the Survey Rig available.</li>
 * <li>campaign-3: two upgrades researched in the native tech tree with what the secured sectors kept; The Buried
 * Foundry played to its capture (a machine restored, the archive read): the Ancient Platform is recovered and the
 * chapter ends.</li>
 * <li>campaign-4: after a restart, the finished chapter: the deep region locked, the archive, a secured sector
 * reloaded, a duplicate capture event, a sector abandoned, replayed and lost, a custom game and old saves; none of
 * it changes the chapter's progress.</li>
 * </ul>
 */
public class CampaignSuite implements VesperHarness.Suite{
    final String phase;
    int captures;
    boolean listening, asked;
    String itemsBefore = "", settingsBefore = "";
    ItemSeq stockBefore = new ItemSeq();

    public CampaignSuite(String phase){
        this.phase = phase;
    }

    @Override
    public void define(VesperHarness h, Script s){
        if(!listening){
            listening = true;
            Events.on(SectorCaptureEvent.class, e -> {
                captures++;
                h.line("event: sector captured: " + e.sector + (e.initialCapture ? " (first capture)" : " (again)"));
            });
            Events.on(RecordEvent.class, e -> h.line("event: record found: " + e.record.id));
        }
        switch(phase){
            case "campaign-1" -> firstSector(h, s);
            case "campaign-2" -> secondSector(h, s);
            case "campaign-3" -> thirdSector(h, s);
            case "campaign-4" -> afterChapter(h, s);
            case "campaign-look" -> look(h, s);
            case "campaign-ui-a" -> uiAfterFirst(h, s);
            case "campaign-ui-b" -> uiAfterChapter(h, s);
            case "campaign-mp-host" -> mpHost(h, s);
            case "campaign-mp-client" -> mpGuest(h, s);
            default -> h.fail("campaign: unknown phase", phase);
        }
    }

    // --- campaign-1: a clean profile and First Awakening

    void firstSector(VesperHarness h, Script s){
        SectorPreset fa = VSectors.firstAwakening, rf = VSectors.resonanceFault;
        s.check("clean profile: nothing of the campaign yet", () -> !fa.sector.hasSave() && !fa.sector.info.wasCaptured && !Chapter.secured(fa)
            && !Chapter.complete() && VPlanets.vesper.sectors.count(Sector::hasBase) == 0, () -> "saves " + VPlanets.vesper.sectors.count(Sector::hasSave));
        s.check("clean profile: the planet is listed and First Awakening is its start sector", () -> VPlanets.vesper.alwaysUnlocked && VPlanets.vesper.isLandable()
            && VPlanets.vesper.getStartSector() == fa.sector && fa.alwaysUnlocked, () -> "start sector " + VPlanets.vesper.getStartSector());
        s.check("clean profile: Resonance Fault is locked", () -> rf.locked() && !rf.sector.hasSave(), () -> "");
        s.check("clean profile: industrial foundations researched, resonance not yet", () -> surveyDrill.unlocked() && structuralForge.unlocked()
            && componentAssembler.unlocked() && kineticTurret.unlocked() && transitBelt.unlocked() && crystalExtractor.locked() && stabilizerPylon.locked()
            && reinforcedDrill.locked(), () -> "");

        // --- the way a player starts: Play > Campaign opens the planet map
        s.run(() -> ui.planet.show());
        s.wait(1.5f);
        h.shot("campaign-01-first-open", 0, 0, 1f, true);
        //a profile that never played a campaign is asked to pick Serpulo or Erekir first; VESPER is picked from the map's planet list after that
        s.run(() -> {
            asked = Core.scene.getDialog() != ui.planet;
            if(asked){
                h.line("info: first campaign start: the game asks for a campaign first (" + UiSuite.dialogText().trim().replace('\n', ' ') + ")");
                click(h, Core.scene.getDialog(), Planets.serpulo.localizedName);
            }
        });
        //its OK button is enabled on the frame after the choice
        s.wait(0.5f);
        s.run(() -> {
            if(asked) click(h, Core.scene.getDialog(), Core.bundle.get("ok"));
        });
        s.wait(2f);
        h.shot("campaign-01b-vesper-intro", 0, 0, 1f, true);
        s.check("planet map: VESPER points itself out once, on the planet map", () -> Core.scene.getDialog() != null && UiSuite.dialogText().contains(Core.bundle.get("vesper.intro.go")),
            () -> UiSuite.dialogText());
        s.run(() -> click(h, Core.scene.getDialog(), Core.bundle.get("vesper.intro.go")));
        s.wait(1.5f);
        s.check("planet map: no other dialog left over it", () -> Core.scene.getDialog() == ui.planet, () -> UiSuite.dialogText());
        s.check("planet map: VESPER is in the planet list", () -> UiSuite.find(ui.planet, VPlanets.vesper.localizedName) != null, () -> "");
        s.check("planet map: VESPER shown, First Awakening selected", () -> ui.planet.state.planet == VPlanets.vesper && ui.planet.selected == fa.sector,
            () -> "showing " + ui.planet.state.planet + ", selected " + ui.planet.selected);
        h.shot("campaign-02-vesper", 0, 0, 1f, true);

        //a locked sector: the button says so and does nothing
        s.run(() -> ui.planet.selectSector(rf.sector));
        s.wait(1f);
        h.shot("campaign-02b-resonance-fault-locked", 0, 0, 1f, true);
        //before any base, the native panel lists what is missing and offers no button at all
        s.check("planet map: Resonance Fault shows as locked, waiting on First Awakening", () -> button(ui.planet, Core.bundle.get("sectors.launch")) == null
            && UiSuite.dialogText().contains(Core.bundle.format("requirement.capture", fa.localizedName)), () -> UiSuite.dialogText());
        s.check("planet map: a locked sector cannot be launched to", () -> state.isMenu() && ui.planet.isShown() && !rf.sector.hasSave() && rf.locked(), () -> "");

        s.run(() -> ui.planet.selectSector(fa.sector));
        s.wait(1f);
        h.shot("campaign-03-first-awakening-selected", 0, 0, 1f, true);
        s.check("planet map: First Awakening can be launched to", () -> button(ui.planet, Core.bundle.get("sectors.launch")) != null, () -> UiSuite.dialogText());
        s.run(() -> click(h, ui.planet, Core.bundle.get("sectors.launch")));
        landing(h, s, fa, "first-awakening");
        h.shot("campaign-04-landed", 26, 26, 2f, true);

        new MissionSuite(true).define(h, s);

        captured(h, s, fa);
        s.check("capture: Resonance Fault and the resonance branch become available", () -> rf.unlocked() && crystalExtractor.unlocked() && stabilizerPylon.unlocked(),
            () -> "Resonance Fault " + rf.unlocked() + ", extractor " + crystalExtractor.unlocked() + ", stabilizer " + stabilizerPylon.unlocked());
        s.check("capture: the pulse turret waits for the first crystal", () -> pulseTurret.locked() && !VItems.resonanceCrystal.unlocked(), () -> "");
        s.check("capture: the chapter is not complete yet", () -> !Chapter.complete(), () -> "");
        securedDialog(h, s, fa, "campaign-05-first-awakening-secured");
        //a secured sector stays quiet: no patrol, pulse or incursion after the capture
        s.wait(60f);
        s.check("capture: the basin stops answering after the capture", () -> state.enemies == 0 && Vesper.activity.pending < 0f && !state.rules.waves,
            () -> "enemies " + state.enemies + ", pending " + Vesper.activity.pending);
        h.shot("campaign-06-first-awakening-quiet", 26, 26, 2f, true);
        saveAndQuit(h, s);
    }

    // --- campaign-2: Resonance Fault

    void secondSector(VesperHarness h, Script s){
        SectorPreset fa = VSectors.firstAwakening, rf = VSectors.resonanceFault;
        s.check("restart: First Awakening is still captured and secured", () -> fa.sector.hasSave() && fa.sector.isCaptured() && fa.sector.info.wasCaptured
            && Chapter.secured(fa) && Chapter.Record.reactorSeals.found(), () -> "save " + fa.sector.hasSave() + ", captured " + fa.sector.isCaptured());
        s.check("restart: Resonance Fault and the resonance branch are still available", () -> rf.unlocked() && crystalExtractor.unlocked() && stabilizerPylon.unlocked()
            && !rf.sector.hasSave(), () -> "");
        s.check("restart: the chapter is not complete", () -> !Chapter.complete() && !Chapter.secured(rf), () -> "");

        s.run(() -> ui.planet.show());
        s.wait(1f);
        s.check("planet map: no campaign question the second time", () -> Core.scene.getDialog() == ui.planet, () -> UiSuite.dialogText());
        s.run(() -> click(h, ui.planet, VPlanets.vesper.localizedName));
        //the planet map shows a newly available sector by itself
        s.wait(6f);
        h.shot("campaign-07-resonance-fault-available", 0, 0, 1f, true);
        s.run(() -> ui.planet.selectSector(rf.sector));
        s.wait(1f);
        h.shot("campaign-08-resonance-fault-selected", 0, 0, 1f, true);
        s.check("planet map: Resonance Fault can be launched to", () -> button(ui.planet, Core.bundle.get("sectors.launch")) != null
            && !button(ui.planet, Core.bundle.get("sectors.launch")).isDisabled(), () -> UiSuite.dialogText());
        s.run(() -> {
            itemsBefore = fa.sector.items().toString();
            click(h, ui.planet, Core.bundle.get("sectors.launch"));
        });
        s.wait(1f);
        //the native launch dialog: from the secured sector, with the destination's description; nothing to pay
        s.check("launch: the native launch dialog, from First Awakening", () -> Core.scene.getDialog() != null && UiSuite.dialogText().contains(fa.localizedName)
            && UiSuite.dialogText().contains(Core.bundle.get("launch.text")), () -> UiSuite.dialogText());
        h.shot("campaign-09-launch-dialog", 0, 0, 1f, true);
        s.run(() -> click(h, Core.scene.getDialog(), Core.bundle.get("launch.text")));
        landing(h, s, rf, "resonance-fault");
        s.check("launch: it cost First Awakening nothing", () -> fa.sector.items().toString().equals(itemsBefore), () -> "before " + itemsBefore + ", after " + fa.sector.items());
        h.shot("campaign-10-resonance-fault-landed", 28, 30, 2f, true);

        new FaultSuite(true, false, "fault-loud", true).define(h, s);

        captured(h, s, rf);
        s.check("capture: the sector's records were found", () -> Chapter.Record.resonanceNetwork.found() && Chapter.Record.containment.found(), () -> "");
        s.check("capture: The Buried Foundry and the Survey Rig become available", () -> VSectors.buriedFoundry.unlocked() && surveyRig.unlocked(), () -> "");
        s.check("capture: the archive's platform and the deep region stay locked; the chapter goes on", () -> ancientPlatform.locked() && VSectors.deepSignal.locked()
            && !Chapter.complete(), () -> "");
        s.check("capture: the resonance industry waits for The Buried Foundry", () -> resonanceDynamo.locked() && containmentField.locked(), () -> "");
        securedDialog(h, s, rf, "campaign-11-resonance-fault-secured");
        saveAndQuit(h, s);
    }

    // --- campaign-3: research, then The Buried Foundry and the end of the chapter

    void thirdSector(VesperHarness h, Script s){
        SectorPreset fa = VSectors.firstAwakening, rf = VSectors.resonanceFault, bf = VSectors.buriedFoundry;
        s.check("restart: both sectors still captured and secured, The Buried Foundry available", () -> fa.sector.isCaptured() && rf.sector.isCaptured()
            && Chapter.secured(fa) && Chapter.secured(rf) && bf.unlocked() && surveyRig.unlocked() && !Chapter.complete(), () -> "");
        s.run(() -> ui.planet.show());
        s.wait(1f);
        s.run(() -> click(h, ui.planet, VPlanets.vesper.localizedName));
        s.wait(6f);
        h.shot("campaign-12-buried-foundry-available", 0, 0, 1f, true);

        //two upgrades the Foundry's defense is built with, researched with what the secured sectors kept
        s.check("research: heavy walls and accumulators still need research", () -> heavyWall.locked() && powerAccumulator.locked(), () -> "");
        s.run(() -> {
            stockBefore = stock();
            click(h, ui.planet, Core.bundle.get("techtree"));
        });
        s.wait(1.5f);
        h.shot("campaign-13-tech-tree", 0, 0, 1f, true);
        s.check("research: the tech tree opens on VESPER's own tree", () -> ui.research.isShown() && ui.research.lastNode == VTechTree.root, () -> "tree " + ui.research.lastNode.name);
        s.run(() -> research(h, heavyWall));
        s.wait(0.5f);
        s.run(() -> research(h, powerAccumulator));
        s.wait(0.5f);
        h.shot("campaign-14-tech-tree-researched", 0, 0, 1f, true);
        s.check("research: both researched, paid from the secured sectors", () -> heavyWall.unlocked() && powerAccumulator.unlocked()
            && stockBefore.get(VItems.structuralAlloy) - stock().get(VItems.structuralAlloy) == 80
            && stockBefore.get(VItems.ferroprism) - stock().get(VItems.ferroprism) == 100
            && stockBefore.get(VItems.conductiveScrap) - stock().get(VItems.conductiveScrap) == 150,
            () -> "before " + stockBefore + ", after " + stock());
        s.run(() -> ui.research.hide());
        s.wait(0.5f);

        s.run(() -> ui.planet.selectSector(bf.sector));
        s.wait(1f);
        s.run(() -> click(h, ui.planet, Core.bundle.get("sectors.launch")));
        s.wait(1f);
        h.shot("campaign-15-buried-foundry-launch", 0, 0, 1f, true);
        s.run(() -> click(h, Core.scene.getDialog(), Core.bundle.get("launch.text")));
        landing(h, s, bf, "buried-foundry");

        new FoundrySuite("restore", true).define(h, s);

        captured(h, s, bf);
        s.check("capture: the Foundry's records were found (the restored machine's, the archive's, the signal)", () -> Chapter.Record.restoredMachine.found()
            && Chapter.Record.archiveIndex.found() && Chapter.Record.deepSignal.found() && !Chapter.Record.dismantledMachine.found(), () -> "");
        s.check("capture: the Ancient Platform is recovered technology now", () -> Chapter.archiveRead() && ancientPlatform.unlocked(), () -> "");
        s.check("capture: Chapter One is complete; the deep region stays locked", () -> Chapter.complete() && VSectors.deepSignal.locked(), () -> "");
        s.check("capture: the resonance industry (dynamo, containment field) becomes available, with no research to pay", () -> resonanceDynamo.unlocked()
            && containmentField.unlocked(), () -> "dynamo " + resonanceDynamo.unlocked() + ", field " + containmentField.unlocked());
        s.waitFor(() -> UiSuite.dialogText().contains(Core.bundle.get("vesper.chapter.title")), 40f);
        s.check("capture: the chapter's ending is shown", () -> UiSuite.dialogText().contains(Core.bundle.get("vesper.chapter.title"))
            && UiSuite.dialogText().contains(Chapter.Record.deepSignal.title()), () -> UiSuite.dialogText());
        h.shot("campaign-16-chapter-complete", 30, 28, 2f, true);
        s.run(() -> click(h, Core.scene.getDialog(), Core.bundle.get("continue")));
        s.wait(0.5f);
        saveAndQuit(h, s);
    }

    // --- campaign-4: what the finished chapter keeps, and what cannot break it

    void afterChapter(VesperHarness h, Script s){
        SectorPreset fa = VSectors.firstAwakening, rf = VSectors.resonanceFault, bf = VSectors.buriedFoundry, deep = VSectors.deepSignal;
        s.check("restart: the chapter is still complete, all three sectors secured and captured", () -> Chapter.complete() && Chapter.sectors.allMatch(Chapter::secured)
            && Chapter.sectors.allMatch(p -> p.sector.isCaptured()), () -> "");
        s.check("restart: six records kept (the dismantled machine's was not found on this run)", () -> Chapter.Record.all.length - 1 == countRecords()
            && !Chapter.Record.dismantledMachine.found(), () -> countRecords() + " records");
        s.check("restart: the recovered technology and the research are kept", () -> ancientPlatform.unlocked() && heavyWall.unlocked() && powerAccumulator.unlocked()
            && resonanceDynamo.unlocked() && containmentField.unlocked(), () -> "");
        s.run(() -> settingsBefore = chapterSettings());

        s.run(() -> ui.planet.show());
        s.wait(1f);
        s.run(() -> click(h, ui.planet, VPlanets.vesper.localizedName));
        s.wait(2f);
        h.shot("campaign-17-chapter-planet", 0, 0, 1f, true);
        s.run(() -> {
            ui.planet.lookAt(deep.sector);
            ui.planet.selectSector(deep.sector);
        });
        s.wait(1.5f);
        h.shot("campaign-18-deep-signal", 0, 0, 1f, true);
        s.check("deep region: shown as not available in this version, and locked", () -> UiSuite.dialogText().contains(Core.bundle.get("vesper.requirement.unavailable"))
            && button(ui.planet, Core.bundle.get("locked")) != null && button(ui.planet, Core.bundle.get("locked")).isDisabled() && !deep.sector.hasSave(),
            () -> UiSuite.dialogText());
        s.run(() -> button(ui.planet, Core.bundle.get("locked")).fireClick());
        s.wait(1f);
        s.check("deep region: the locked button launches nothing", () -> state.isMenu() && !deep.sector.hasSave(), () -> "");
        s.run(() -> click(h, ui.planet, Core.bundle.get("vesper.archive")));
        s.wait(1f);
        h.shot("campaign-19-archive", 0, 0, 1f, true);
        s.check("archive: every record found is readable, the one missed is unknown", () -> UiSuite.dialogText().contains(Chapter.Record.reactorSeals.title())
            && UiSuite.dialogText().contains(Chapter.Record.deepSignal.text()) && UiSuite.dialogText().contains(Core.bundle.get("vesper.archive.unknown")), () -> UiSuite.dialogText());
        s.run(() -> Core.scene.getDialog().hide());
        s.wait(0.5f);

        //a secured sector reloads as it was left: quiet, won, nothing fired twice
        s.run(() -> {
            captures = 0;
            ui.planet.selectSector(fa.sector);
        });
        s.wait(1f);
        s.check("re-entry: a secured sector offers to go back", () -> button(ui.planet, Core.bundle.get("sectors.go")) != null, () -> UiSuite.dialogText());
        s.run(() -> click(h, ui.planet, Core.bundle.get("sectors.go")));
        s.waitFor(() -> state.isGame() && state.rules.sector == fa.sector, 60f);
        s.run(h::freeCamera);
        s.wait(20f);
        s.check("re-entry: First Awakening reloads won and quiet", () -> Vesper.mission.active() && Vesper.mission.victory && !state.rules.waves && state.enemies == 0
            && state.rules.defaultTeam.data().buildings.size > 50 && captures == 0 && !state.gameOver,
            () -> "victory " + Vesper.mission.victory + ", waves " + state.rules.waves + ", buildings " + state.rules.defaultTeam.data().buildings.size + ", captures " + captures);
        h.shot("campaign-20-first-awakening-again", 26, 26, 2f, true);
        //the same capture event again, as if it fired twice: nothing new is recorded or unlocked
        s.run(() -> Events.fire(new SectorCaptureEvent(fa.sector, false)));
        //the chapter's dialog may show again (it is information, not a reward); close it
        s.wait(6f);
        s.check("duplicate event: a second capture of a secured sector changes nothing", () -> chapterSettings().equals(settingsBefore), () -> chapterSettings());
        s.run(() -> {
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        });
        saveAndQuit(h, s);

        //replaying a sector: abandon it the native way, land again on a fresh map
        s.run(() -> ui.planet.show());
        s.wait(1f);
        s.run(() -> {
            click(h, ui.planet, VPlanets.vesper.localizedName);
            ui.planet.selectSector(rf.sector);
            ui.planet.abandonSectorConfirm(rf.sector, null);
        });
        s.wait(1f);
        s.run(() -> click(h, Core.scene.getDialog(), Core.bundle.get("ok")));
        s.wait(1f);
        s.check("replay: Resonance Fault abandoned (no base), its progress elsewhere untouched", () -> !rf.sector.hasBase() && chapterSettings().equals(settingsBefore)
            && bf.unlocked() && rf.unlocked(), () -> "base " + rf.sector.hasBase());
        s.run(() -> {
            ui.planet.selectSector(rf.sector);
            click(h, ui.planet, Core.bundle.get("sectors.launch"));
        });
        s.wait(1f);
        s.run(() -> click(h, Core.scene.getDialog(), Core.bundle.get("launch.text")));
        landing(h, s, rf, "resonance-fault");
        s.check("replay: a fresh Resonance Fault (no objective done, its own loadout)", () -> Vesper.fault.active() && Vesper.fault.completed == 0 && !Vesper.fault.victory
            && state.rules.defaultTeam.core() != null && state.rules.defaultTeam.core().items.get(VItems.ferroprism) == 900, () -> "objectives " + Vesper.fault.completed);

        //losing the replay: the sector is lost, the chapter is not
        s.run(() -> state.rules.defaultTeam.cores().copy().each(c -> c.kill()));
        s.waitFor(() -> state.gameOver, 30f);
        s.wait(4f);
        h.shot("campaign-21-replay-lost", 28, 30, 2f, true);
        s.check("defeat: the replayed sector is lost", () -> state.gameOver && !rf.sector.hasBase(), () -> "");
        s.check("defeat: nothing of the chapter is lost (sectors, records, unlocks)", () -> chapterSettings().equals(settingsBefore) && Chapter.complete()
            && bf.unlocked() && ancientPlatform.unlocked() && bf.sector.hasSave() && fa.sector.isCaptured(), () -> chapterSettings());
        s.run(() -> {
            if(ui.restart.isShown()) ui.restart.hide();
            ui.paused.runExitSave();
        });
        s.waitFor(() -> state.isMenu(), 30f);

        //a custom game and saves of earlier versions never touch the campaign
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.foundryName), mindustry.game.Gamemode.survival));
        s.wait(1f);
        s.check("custom game: The Buried Foundry as a custom game is no campaign, and builds Vesper blocks", () -> !state.isCampaign() && Vesper.foundry.active()
            && surveyRig.environmentBuildable() && surveyRig.isPlaceable(), () -> "");
        s.run(() -> ResponseSuite.loadFixture("resonance-fault-0.3-midgame.msav"));
        s.wait(1f);
        s.check("old save: a 0.3 save still loads as a custom game", () -> !state.isCampaign() && Vesper.fault.active() && Vesper.fault.completed == 2, () -> "");
        s.run(() -> ResponseSuite.loadFixture("buried-foundry-0.4-dismantling.msav"));
        s.wait(1f);
        s.check("old save: a 0.4 save still loads as a custom game", () -> !state.isCampaign() && Vesper.foundry.active(), () -> "");
        s.run(() -> ResponseSuite.loadFixture("first-awakening-0.2-midgame.msav"));
        s.wait(1f);
        s.check("old save: a 0.2 save still loads as a custom game", () -> !state.isCampaign() && Vesper.mission.active(), () -> "");
        s.check("custom game and old saves: the campaign's progress is unchanged", () -> chapterSettings().equals(settingsBefore), () -> chapterSettings());
        s.run(() -> {
            logic.reset();
            state.set(mindustry.core.GameState.State.menu);
        });
    }

    // --- campaign-mp-host / campaign-mp-client: a campaign sector hosted for a second player

    static final String packet = "vesper-campaign";
    static final int mpPort = Strings.parseInt(System.getProperty("vesper.mp.port", "6578"), 6578);
    boolean guestDone, researchedSent;

    /** The host: a player's own campaign (First Awakening secured), Resonance Fault hosted and played to its capture with a guest watching. */
    void mpHost(VesperHarness h, Script s){
        SectorPreset rf = VSectors.resonanceFault;
        s.check("mp host: the host's campaign has First Awakening secured", () -> Chapter.secured(VSectors.firstAwakening) && rf.unlocked(), () -> "");
        s.run(() -> {
            netServer.addPacketHandler(packet, (player, data) -> {
                h.line("mp host: guest says " + data);
                if(data.equals("done")) guestDone = true;
            });
            ui.planet.show();
        });
        s.wait(1f);
        s.run(() -> {
            click(h, ui.planet, VPlanets.vesper.localizedName);
            ui.planet.selectSector(rf.sector);
        });
        s.wait(2f);
        s.run(() -> click(h, ui.planet, Core.bundle.get("sectors.launch")));
        s.wait(1f);
        s.run(() -> click(h, Core.scene.getDialog(), Core.bundle.get("launch.text")));
        landing(h, s, rf, "resonance-fault");
        s.run(() -> {
            try{
                net.host(mpPort);
                h.line("mp: hosting the campaign sector on port " + mpPort);
            }catch(Exception e){
                h.fail("mp host: hosting", e.toString());
            }
        });
        s.until("mp host: the guest joined", () -> Groups.player.size() >= 2, 180f, () -> Groups.player.size() + " players");
        s.wait(3f);

        new FaultSuite(true, false, "fault-loud", true).define(h, s);

        captured(h, s, rf);
        //research while hosting: the guest sees it through the host's rules, not in its own profile
        s.run(() -> ui.research.show());
        s.wait(1f);
        s.run(() -> research(h, powerAccumulator));
        s.wait(0.5f);
        s.run(() -> {
            ui.research.hide();
            Call.clientPacketReliable(packet, "researched");
            researchedSent = true;
        });
        s.check("mp host: researched while hosting", () -> powerAccumulator.unlocked(), () -> "");
        s.until("mp host: the guest finished its checks", () -> guestDone, 120f, null);
        s.wait(2f);
        s.run(() -> net.closeServer());
        saveAndQuit(h, s);
        s.check("mp host: the host's campaign kept the capture", () -> Chapter.secured(rf) && rf.sector.isCaptured(), () -> "");
    }

    /** The guest: a clean profile joining the host's campaign sector; it sees everything happen and keeps nothing. */
    void mpGuest(VesperHarness h, Script s){
        SectorPreset rf = VSectors.resonanceFault;
        String name = System.getProperty("vesper.mp.name", "Bravo");
        s.run(() -> {
            settingsBefore = chapterSettings();
            netClient.addPacketHandler(packet, data -> {
                if(data.equals("researched")) researchedSent = true;
            });
            player.name = name;
            Core.settings.put("name", name);
        });
        //the host needs time to land and host; keep trying until it answers
        s.step(new Script.Step(){
            float timer = 999f, total;

            @Override
            public boolean update(float delta){
                if(net.client() && state.isGame()) return true;
                total += delta;
                if((timer += delta) > 300f && !net.active()){
                    timer = 0f;
                    ui.join.connect("127.0.0.1", mpPort);
                }
                return total > 600f * 60f;
            }
        });
        s.wait(5f);
        s.check("mp guest: joined the host's Resonance Fault sector", () -> net.client() && state.isGame() && Vesper.fault.active(), () -> "");
        s.check("mp guest: the world it sees is the host's campaign sector", () -> state.isCampaign() && state.rules.sector != null && state.rules.sector.id == rf.sector.id
            && state.rules.sector.planet == VPlanets.vesper, () -> "sector " + state.rules.sector);
        s.check("mp guest: the host's unlocks reach it (it can build what the host researched)", () -> crystalExtractor.unlockedNow() && stabilizerPylon.unlockedNow()
            && !Core.settings.getBool(crystalExtractor.name + "-unlocked", false), () -> "");
        if(!headless) s.check("mp guest: the mission panel is shown", () -> ui.hudGroup.find("vesper-basin") != null, () -> "");
        s.run(() -> ui.planet.show());
        s.wait(1f);
        s.check("mp guest: no planet map for a guest (native)", () -> !ui.planet.isShown(), () -> "");
        s.run(() -> {
            if(Core.scene.getDialog() != null) Core.scene.getDialog().hide();
        });
        s.until("mp guest: sees the capture as the victory", () -> Vesper.fault.victory && captures >= 1, 40f * 60f, () -> "stored " + Vesper.fault.stored());
        s.check("mp guest: the records were announced to it too", () -> Chapter.Record.containment.found() && Chapter.Record.resonanceNetwork.found(), () -> "");
        s.wait(8f);
        s.check("mp guest: no chapter dialog for a guest", () -> !UiSuite.dialogText().contains(Core.bundle.format("vesper.secured.title", rf.localizedName)), () -> UiSuite.dialogText());
        s.until("mp guest: the host's research reaches it", () -> researchedSent && powerAccumulator.unlockedNow(), 120f, null);
        s.run(() -> {
            Call.serverPacketReliable(packet, "done");
            h.line("mp guest: done");
        });
        s.wait(2f);
        s.run(() -> netClient.disconnectQuietly());
        s.waitFor(() -> state.isMenu(), 30f);
        s.check("mp guest: its own profile kept nothing of the host's campaign", () -> chapterSettings().equals(settingsBefore)
            && !Core.settings.getBool(powerAccumulator.name + "-unlocked", false) && VPlanets.vesper.sectors.count(Sector::hasSave) == 0
            && !Core.settings.getBool("vesper-campaign-record-containment", false), () -> chapterSettings() + " (before: " + settingsBefore + ")");
    }

    // --- campaign-ui-a / campaign-ui-b: the campaign's screens, in the profiles the campaign left (runCampaignUi)

    /** Shot name prefix: window size and language. */
    String uiTag(){
        return phase + "-" + Core.graphics.getWidth() + "x" + Core.graphics.getHeight() + "-" + UiSuite.language();
    }

    void uiStart(VesperHarness h, Script s){
        s.run(() -> {
            String[] wh = System.getProperty("vesper.harness.window", "").split("x");
            if(wh.length == 2) UiSuite.resize(h, Strings.parseInt(wh[0], 1280), Strings.parseInt(wh[1], 720));
        });
        s.wait(1f);
        s.run(() -> h.line("ui: window " + Core.graphics.getWidth() + "x" + Core.graphics.getHeight() + ", language " + Core.bundle.getLocale()));
    }

    void uiShot(VesperHarness h, Script s, String name){
        s.step(new Script.Step(){
            boolean queued;

            @Override
            public boolean update(float delta){
                if(!queued){
                    queued = true;
                    h.captureNow(uiTag() + "-" + name, true);
                    return false;
                }
                return true;
            }
        });
        s.wait(0.3f);
    }

    /** Every button and label Vesper added to the planet map is on screen and clear of the game's own buttons. */
    void planetLayout(VesperHarness h, Script s, String what){
        s.run(() -> {
            float w = Core.graphics.getWidth(), ht = Core.graphics.getHeight();
            Button archive = button(ui.planet, Core.bundle.get("vesper.archive"));
            Button tech = button(ui.planet, Core.bundle.get("techtree")), back = button(ui.planet, Core.bundle.get("back"));
            if(archive == null || tech == null || back == null){
                h.fail("ui " + uiTag() + " " + what + ": planet map buttons", "archive " + archive + ", tech " + tech + ", back " + back);
                return;
            }
            var a = UiSuite.bounds(archive);
            boolean on = a.x >= 0 && a.y >= 0 && a.x + a.width <= w + 0.5f && a.y + a.height <= ht + 0.5f;
            boolean clear = !a.overlaps(UiSuite.bounds(tech)) && !a.overlaps(UiSuite.bounds(back));
            h.check("ui " + uiTag() + " " + what + ": the Archive button is on screen and clear of the game's buttons", on && clear,
                "archive " + UiSuite.fmt(a) + ", tech " + UiSuite.fmt(UiSuite.bounds(tech)) + " in " + (int)w + "x" + (int)ht);
        });
    }

    /** A dialog of the chapter: fully on screen, its buttons inside it. */
    void dialogLayout(VesperHarness h, Script s, String what){
        s.run(() -> {
            var d = Core.scene.getDialog();
            float w = Core.graphics.getWidth(), ht = Core.graphics.getHeight();
            if(d == null){
                h.fail("ui " + uiTag() + " " + what + ": dialog", "none shown");
                return;
            }
            var cont = UiSuite.bounds(d.cont);
            var buttons = UiSuite.bounds(d.buttons);
            h.check("ui " + uiTag() + " " + what + ": text and buttons fully on screen, not overlapping", cont.x >= 0 && cont.y >= 0 && cont.x + cont.width <= w + 0.5f
                && cont.y + cont.height <= ht + 0.5f && buttons.y >= 0 && buttons.y + buttons.height <= cont.y + 0.5f,
                "text " + UiSuite.fmt(cont) + ", buttons " + UiSuite.fmt(buttons) + " in " + (int)w + "x" + (int)ht);
        });
    }

    /** After First Awakening: one sector secured, the next one available, the third locked. */
    void uiAfterFirst(VesperHarness h, Script s){
        SectorPreset fa = VSectors.firstAwakening, rf = VSectors.resonanceFault, bf = VSectors.buriedFoundry;
        uiStart(h, s);
        s.run(() -> ui.planet.show());
        s.wait(1f);
        s.run(() -> click(h, ui.planet, VPlanets.vesper.localizedName));
        //the native unlock: the camera goes to the new sector and names it
        s.wait(1.2f);
        uiShot(h, s, "01-second-sector-unlocked");
        s.wait(5f);
        uiShot(h, s, "02-planet-vesper");
        planetLayout(h, s, "planet map");
        s.run(() -> ui.planet.selectSector(fa.sector));
        s.wait(1f);
        uiShot(h, s, "03-sector-secured");
        s.run(() -> ui.planet.selectSector(rf.sector));
        s.wait(1f);
        uiShot(h, s, "04-sector-available");
        s.run(() -> ui.planet.selectSector(bf.sector));
        s.wait(1f);
        uiShot(h, s, "05-sector-locked");
        s.check("ui " + phase + ": the third sector is locked and says what it waits for", () -> UiSuite.dialogText().contains(Core.bundle.format("requirement.capture", rf.localizedName)),
            () -> UiSuite.dialogText());
        s.run(() -> {
            ui.planet.selectSector(rf.sector);
            click(h, ui.planet, Core.bundle.get("sectors.launch"));
        });
        s.wait(1f);
        uiShot(h, s, "06-sector-description");
        s.run(() -> Core.scene.getDialog().hide());
        s.wait(0.5f);
        s.run(() -> click(h, ui.planet, Core.bundle.get("techtree")));
        s.wait(1.5f);
        uiShot(h, s, "07-tech-tree");
        s.run(() -> ui.research.hide());
        s.wait(0.5f);
        //the dialog shown after the first victory, in the secured sector itself
        s.run(() -> {
            ui.planet.selectSector(fa.sector);
            click(h, ui.planet, Core.bundle.get("sectors.go"));
        });
        s.waitFor(() -> state.isGame() && state.rules.sector == fa.sector, 60f);
        s.wait(3f);
        s.run(() -> Vesper.chapterUi.showSecured(fa));
        s.wait(1f);
        uiShot(h, s, "08-first-victory");
        dialogLayout(h, s, "first sector secured");
        s.run(() -> Core.scene.getDialog().hide());
        s.run(() -> {
            logic.reset();
            state.set(mindustry.core.GameState.State.menu);
        });
    }

    /** After the chapter: three sectors secured, the deep region marked, the ending and the archive. */
    void uiAfterChapter(VesperHarness h, Script s){
        SectorPreset bf = VSectors.buriedFoundry;
        uiStart(h, s);
        s.run(() -> ui.planet.show());
        s.wait(1f);
        s.run(() -> click(h, ui.planet, VPlanets.vesper.localizedName));
        s.wait(2f);
        uiShot(h, s, "01-chapter-planet");
        planetLayout(h, s, "planet map after the chapter");
        s.run(() -> {
            ui.planet.lookAt(VSectors.deepSignal.sector);
            ui.planet.selectSector(VSectors.deepSignal.sector);
        });
        s.wait(1.5f);
        uiShot(h, s, "02-deep-region");
        s.run(() -> click(h, ui.planet, Core.bundle.get("techtree")));
        s.wait(1.5f);
        uiShot(h, s, "03-tech-tree-complete");
        s.run(() -> ui.research.hide());
        s.wait(0.5f);
        s.run(() -> click(h, ui.planet, Core.bundle.get("vesper.archive")));
        s.wait(1f);
        uiShot(h, s, "04-archive");
        dialogLayout(h, s, "archive");
        s.run(() -> Core.scene.getDialog().hide());
        s.wait(0.5f);
        s.run(() -> {
            ui.planet.selectSector(bf.sector);
            click(h, ui.planet, Core.bundle.get("sectors.go"));
        });
        s.waitFor(() -> state.isGame() && state.rules.sector == bf.sector, 60f);
        s.wait(3f);
        s.run(() -> Vesper.chapterUi.showChapterComplete());
        s.wait(1f);
        uiShot(h, s, "05-chapter-complete");
        dialogLayout(h, s, "chapter complete");
        s.run(() -> Core.scene.getDialog().hide());
        s.run(() -> {
            logic.reset();
            state.set(mindustry.core.GameState.State.menu);
        });
    }

    /** The planet itself on the planet map, for the art review: whole, close to the chapter's sectors, and on its night side. */
    void look(VesperHarness h, Script s){
        uiStart(h, s);
        s.run(() -> {
            Core.settings.put("campaignselect", true);
            Core.settings.put("vesper-campaign-intro", true);
            ui.planet.show();
        });
        s.wait(1f);
        s.run(() -> click(h, ui.planet, VPlanets.vesper.localizedName));
        s.wait(3f);
        uiShot(h, s, "planet-whole");
        s.run(() -> {
            ui.planet.lookAt(VSectors.resonanceFault.sector);
            ui.planet.zoom = 0.55f;
        });
        s.wait(3f);
        uiShot(h, s, "planet-close");
        s.run(() -> {
            ui.planet.state.camPos.set(VPlanets.vesper.getLightNormal()).scl(-1f).setLength(ui.planet.state.camPos.len());
            ui.planet.zoom = 1f;
        });
        s.wait(3f);
        uiShot(h, s, "planet-night");
        s.run(() -> ui.planet.hide());
    }

    // --- steps shared by the sectors

    /** Waits for the sector's world after a launch and checks it is the campaign sector with the map's own rules. */
    void landing(VesperHarness h, Script s, SectorPreset preset, String scenario){
        s.waitFor(() -> state.isGame() && state.rules.sector == preset.sector && !player.dead(), 60f);
        s.run(h::freeCamera);
        s.wait(3f);
        s.check("landing: " + preset.name + " is being played as a campaign sector", () -> state.isCampaign() && state.rules.sector == preset.sector && preset.sector.isBeingPlayed(),
            () -> "sector " + state.rules.sector);
        s.check("landing: " + preset.name + " keeps the map's own rules (scenario, no win wave yet, whitelist)", () -> scenario.equals(state.rules.tags.get("vesper.scenario"))
            && state.rules.winWave == 0 && state.rules.waves && state.rules.blockWhitelist && state.rules.planet == VPlanets.vesper && !state.rules.infiniteResources,
            () -> "winWave " + state.rules.winWave + ", planet " + state.rules.planet);
        s.check("landing: " + preset.name + " has its sector save from the start", () -> preset.sector.hasSave() && control.saves.getCurrent() != null
            && control.saves.getCurrent().isAutosave(), () -> "");
    }

    void captured(VesperHarness h, Script s, SectorPreset preset){
        s.check("capture: " + preset.name + " captured natively, once", () -> preset.sector.isCaptured() && preset.sector.info.wasCaptured && captures == 1 && !state.gameOver,
            () -> "captured " + preset.sector.isCaptured() + ", events " + captures + ", game over " + state.gameOver);
        s.check("capture: the chapter records " + preset.name + " as secured", () -> Chapter.secured(preset), () -> "");
        s.run(() -> h.line("info: core at the capture of " + preset.name + ": " + preset.sector.items().toString().replace('\n', ' ')));
    }

    /** The chapter's dialog after a capture: what was found, what is available now, where to go. */
    void securedDialog(VesperHarness h, Script s, SectorPreset preset, String shot){
        s.waitFor(() -> UiSuite.dialogText().contains(Core.bundle.format("vesper.secured.title", preset.localizedName)), 20f);
        s.check("capture: the chapter shows what " + preset.name + " gave", () -> UiSuite.dialogText().contains(Core.bundle.format("vesper.secured.title", preset.localizedName)),
            () -> UiSuite.dialogText());
        h.shot(shot, 26, 26, 2f, true);
        s.run(() -> click(h, Core.scene.getDialog(), Core.bundle.get("continue")));
        s.wait(0.5f);
    }

    // --- helpers

    /** Items across the player's VESPER sectors, as the research dialog counts them. */
    static ItemSeq stock(){
        ItemSeq out = new ItemSeq();
        for(Sector sector : VPlanets.vesper.sectors){
            if(sector.hasBase() && !sector.isFrozen()) out.add(sector.items());
        }
        return out;
    }

    /** Clicks the node of this content in the open tech tree: on desktop that researches it (native). */
    static void research(VesperHarness h, UnlockableContent content){
        for(var child : ui.research.view.getChildren()){
            if(child instanceof ImageButton b && b.userObject == content.techNode){
                b.fireClick();
                return;
            }
        }
        h.fail("research: node of " + content.name, "not found in the tech tree");
    }

    static int countRecords(){
        int n = 0;
        for(var r : Chapter.Record.all) if(r.found()) n++;
        return n;
    }

    /** Everything the chapter keeps in the profile, as one string to compare. */
    static String chapterSettings(){
        StringBuilder out = new StringBuilder();
        for(var p : Chapter.sectors) out.append(p.name).append('=').append(Chapter.secured(p)).append(p.unlocked() ? "+" : "-").append(' ');
        for(var r : Chapter.Record.all) out.append(r.id).append('=').append(r.found()).append(' ');
        out.append("complete=").append(Chapter.complete()).append(" archive=").append(Chapter.archiveRead()).append(" platform=").append(ancientPlatform.unlocked());
        return out.toString();
    }

    /** Saves the sector and returns to the menu the way the pause menu's quit does. */
    static void saveAndQuit(VesperHarness h, Script s){
        s.run(() -> ui.paused.runExitSave());
        s.waitFor(() -> state.isMenu(), 30f);
        s.check("quit: back in the menu with the sector saved", () -> state.isMenu(), () -> "");
    }

    /** The button labelled with this text under the element. */
    static @Nullable Button button(@Nullable Element root, String text){
        Element e = root == null ? null : UiSuite.find(root, text);
        while(e != null && !(e instanceof Button)) e = e.parent;
        return (Button)e;
    }

    /** Clicks the button labelled with this text under the element; fails the check if there is none. */
    static void click(VesperHarness h, @Nullable Element root, String text){
        Button b = button(root, text);
        if(b == null){
            h.fail("ui: button '" + text + "'", "not found");
            return;
        }
        b.fireClick();
    }
}
