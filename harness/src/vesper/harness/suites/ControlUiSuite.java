package vesper.harness.suites;

import arc.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import vesper.*;
import vesper.harness.*;
import vesper.mission.*;
import vesper.systems.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.ContainmentLock.*;
import vesper.world.ProtocolController.*;
import vesper.world.ProtocolSite.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Captures of protocol routing as a player sees it, in the window size and language the client runs with
 * (-PharnessWindow, -PharnessLocale), in the purpose-built world of {@link ControlSuite}: the controller before its
 * survey; its panel when it knows its protocols, in each routing mode and with a short supply, while it restores a
 * site, during the reactivation sequence (running and standing still with its reason) and once reactivated; every
 * lock state (dark, open, closing, shut, switched off); the sites (sealed off, waiting for materials, under the
 * recovery protocol, restored and running, stopped by a pulse); the database pages; and the shipped map as it
 * starts, with its markers and objectives. The info panel shows the building under the mouse, so the suite pins the
 * desktop mouse over it. Fixtures: the survey and the restorations' work are completed by the test, the materials and
 * the cores are handed over (what belts would bring), the activity is held up for the pulse. Checks that every text
 * shown is translated and that the panel and its controls fit the window; the rest is evidence.
 */
public class ControlUiSuite implements VesperHarness.Suite{
    boolean listening;
    float holdShock = -1f;
    @Nullable Vec2 hover;
    boolean fuel;

    @Override
    public void define(VesperHarness h, Script s){
        if(headless) return;
        if(!listening){
            listening = true;
            Events.run(Trigger.update, () -> {
                if(holdShock >= 0f && Vesper.activity.active()) Vesper.activity.shock = holdShock;
                if(hover != null) IndustryUiSuite.pin(hover);
                if(fuel && state.isPlaying() && Worlds.at(ControlSuite.dynamos[1][0], ControlSuite.dynamos[1][1], 3) instanceof ResonanceDynamo.ResonanceDynamoBuild){
                    for(int i = 0; i < 3; i++){
                        var d = ControlSuite.dynamo(i);
                        if(d.items.get(resonanceCore) < 5) d.items.add(resonanceCore, 5);
                    }
                    var c = controller();
                    if(c != null && c.items.get(resonanceCore) < 10) c.items.add(resonanceCore, 10);
                    var line = ControlSuite.line();
                    if(line.siteState == SiteState.restored){
                        if(line.items.get(conductiveScrap) < 6) line.items.add(conductiveScrap, 12);
                        if(line.items.get(resonanceCrystal) < 2) line.items.add(resonanceCrystal, 4);
                    }
                }
            });
        }
        var a = Vesper.activity;
        s.run(() -> {
            String[] wh = System.getProperty("vesper.harness.window", "").split("x");
            if(wh.length == 2) UiSuite.resize(h, Strings.parseInt(wh[0], 1280), Strings.parseInt(wh[1], 720));
        });
        s.wait(1f);
        s.run(() -> {
            h.line("control-ui: window " + Core.graphics.getWidth() + "x" + Core.graphics.getHeight() + ", language " + UiSuite.language());
            fuel = false;
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.tags.put(FirstAwakening.tagScenario, CentralControl.scenarioId);
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagResponse, "true");
            r.tags.put(AncientSite.tagEnabled, "true");
            StringBuilder g = new StringBuilder();
            for(int[] l : ControlSuite.locks) g.append(g.length() == 0 ? "" : ";").append(l[0] + 1).append(',').append(l[1] + 1);
            r.tags.put(PlanetaryActivity.tagGates, g.toString());
            Worlds.generate(ControlSuite.width, ControlSuite.height, ControlSuite::generate, r);
        });
        s.wait(0.5f);
        s.run(() -> {
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
            a.awakened = true;
        });
        shot(h, s, "01-controller-unexplored", ControlSuite.controllerX + 2.5f, ControlSuite.controllerY + 2.5f, 2.5f);
        shot(h, s, "02-lock-dark", ControlSuite.locks[1][0] + 1.5f, ControlSuite.locks[1][1] + 1.5f, 4f);

        // --- surveyed: the protocols known, the junction open for its materials, the wings sealed off
        s.run(() -> {
            controller().investigate(1f);
            fuel = true;
            controller().locks().each(l -> l.configure(false));
        });
        s.wait(2f);
        panel(h, s, "03-controller-known");
        shot(h, s, "04-lock-off", ControlSuite.locks[1][0] + 1.5f, ControlSuite.locks[1][1] + 1.5f, 4f);
        shot(h, s, "05-array-sealed-off", ControlSuite.arrayX + 2f, ControlSuite.arrayY + 2f, 3f);
        shot(h, s, "06-junction-materials", ControlSuite.junctionX + 1.5f, ControlSuite.junctionY + 1.5f, 3.5f);
        s.run(() -> {
            var j = ControlSuite.junction();
            j.handleStack(structuralAlloy, 80, null);
            j.handleStack(recoveredCircuit, 40, null);
        });
        s.wait(4f);
        shot(h, s, "07-junction-recovery", ControlSuite.junctionX + 1.5f, ControlSuite.junctionY + 1.5f, 3.5f);
        panel(h, s, "08-controller-restoring");

        // --- the locks: closing, shut; a short supply in each mode
        s.run(() -> controller().locks().each(l -> l.configure(true)));
        s.wait(5f);
        shot(h, s, "09-lock-closing", ControlSuite.locks[1][0] + 1.5f, ControlSuite.locks[1][1] + 1.5f, 4f);
        s.wait(10f);
        shot(h, s, "10-lock-shut", ControlSuite.locks[1][0] + 1.5f, ControlSuite.locks[1][1] + 1.5f, 4f);
        s.run(() -> {
            //one dynamo held empty: a demand of 6.5 on a supply of 5
            fuel = false;
            ControlSuite.dynamo(2).items.clear();
            controller().configure(Mode.containment.ordinal());
        });
        s.wait(8f);
        panel(h, s, "11-controller-containment-short");
        s.check("control-ui: the panel fits the window and shows the three modes", () -> fits(Core.bundle.get("vesper.protocol.mode.recovery")), () -> "");
        s.run(() -> controller().configure(Mode.recovery.ordinal()));
        s.wait(8f);
        panel(h, s, "12-controller-recovery-overdrive");
        s.run(() -> {
            fuel = true;
            controller().configure(Mode.balanced.ordinal());
        });

        // --- the wings restored (the work completed by the test); the sequence, a pulse on the line, the end
        s.waitFor(() -> controller().access(), 60f);
        s.run(() -> {
            for(var site : new ProtocolSiteBuild[]{ControlSuite.array(), ControlSuite.line()}){
                for(var stack : ((ProtocolSite)site.block).restoration) site.handleStack(stack.item, stack.amount, null);
            }
        });
        s.wait(3f);
        shot(h, s, "13-array-recovery", ControlSuite.arrayX + 2f, ControlSuite.arrayY + 2f, 3f);
        s.run(() -> {
            ControlSuite.array().work(1e6f);
            ControlSuite.line().work(1e6f);
        });
        s.wait(3f);
        shot(h, s, "14-array-running", ControlSuite.arrayX + 2f, ControlSuite.arrayY + 2f, 3f);
        shot(h, s, "15-line-running", ControlSuite.lineX + 2f, ControlSuite.lineY + 2f, 3f);
        s.waitFor(() -> controller().containedOnce, 20f);
        s.run(() -> controller().configure(true));
        s.wait(5f);
        panel(h, s, "16-controller-sequence");
        s.check("control-ui: during the sequence the panel still fits the window", () -> fits(Core.bundle.get("vesper.protocol.mode.balanced")), () -> "");
        s.run(() -> {
            controller().locks().each(l -> l.configure(false));
            holdShock = 60f;
            a.pulseCooldown = 0f;
        });
        s.waitFor(() -> a.pulseActive > 0f, 60f);
        s.run(() -> a.pulseCooldown = 1e9f);
        s.wait(1f);
        shot(h, s, "17-subsystem-pulse", ControlSuite.arrayX + 2f, ControlSuite.arrayY + 2f, 2f);
        panel(h, s, "18-controller-stalled");
        s.run(() -> {
            holdShock = -1f;
            controller().progress = 0.999f;
        });
        s.waitFor(() -> a.pulseActive < 0f, 25f);
        s.waitFor(() -> controller().sequence == ProtocolController.Sequence.complete, 30f);
        s.wait(1f);
        panel(h, s, "19-controller-reactivated");
        s.check("control-ui: every controller, lock and site text is translated", () -> {
            for(Mode m : Mode.all){
                if(!IndustryUiSuite.translated(m.localized()) || !IndustryUiSuite.translated(Core.bundle.get("vesper.protocol.mode." + m.name() + ".effect"))) return false;
            }
            for(String k : new String[]{"vesper.lock.unknown", "vesper.lock.offline", "vesper.lock.sealed", "vesper.lock.open", "vesper.protocol.start",
                "vesper.protocol.reason.materials", "vesper.protocol.reason.nothing", "vesper.protocol.reason.power", "vesper.protocol.reason.routing",
                "vesper.protocol.reason.cores", "vesper.protocol.sequence.complete", "vesper.protocol.site.unknown", "vesper.protocol.site.sealed",
                "vesper.protocol.site.running", "vesper.protocol.site.pulse", "vesper.protocol.site.idle", "vesper.site.central-controller.purpose",
                "vesper.site.distribution-junction.purpose", "vesper.site.conduction-array.purpose", "vesper.site.processing-line.purpose",
                "vesper.pulse.contained", "vesper.activity.toast.contained", "vesper.central.title", "vesper.central.start"}){
                if(!IndustryUiSuite.translated(Core.bundle.get(k))) return false;
            }
            for(var b : new mindustry.world.Block[]{centralController, containmentLock, distributionJunction, conductionArray, processingLine}){
                if(!IndustryUiSuite.translated(b.localizedName) || !IndustryUiSuite.translated(b.description)) return false;
            }
            return true;
        }, () -> centralController.localizedName);
        s.run(() -> {
            hover = null;
            fuel = false;
            control.input.config.hideConfig();
            if(ui.restart.isShown()) ui.restart.hide();
        });

        // --- the database pages
        s.run(() -> ui.content.show(centralController));
        h.shot("control-" + IndustryUiSuite.window() + "-" + UiSuite.language() + "-20-controller-stats", 0f, 0f, 2f, true);
        s.run(() -> ui.content.hide());
        s.run(() -> ui.content.show(containmentLock));
        h.shot("control-" + IndustryUiSuite.window() + "-" + UiSuite.language() + "-21-lock-stats", 0f, 0f, 2f, true);
        s.run(() -> ui.content.hide());

        // --- the shipped map as it starts: the markers and the objectives in the HUD
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.controlName), Gamemode.survival));
        s.wait(3f);
        shot(h, s, "22-map-chamber", 70.5f, 50.5f, 2f);
        shot(h, s, "23-map-processing", 118f, 40f, 2f);
        shot(h, s, "24-map-corridor", 70f, 76f, 2f);
        s.check("control-ui: the scenario's texts are translated", () -> {
            for(var o : Vesper.central.objectives) if(!IndustryUiSuite.translated(o.text())) return false;
            return IndustryUiSuite.translated(Core.bundle.get("vesper.central.title"));
        }, () -> Core.bundle.get("vesper.central.title"));
        s.run(() -> {
            hover = null;
            if(ui.restart.isShown()) ui.restart.hide();
        });
    }

    void shot(VesperHarness h, Script s, String name, float tileX, float tileY, float scale){
        //a panel left open from the last panel shot would cover what this one shows: closed, as a tap elsewhere closes it
        s.run(() -> control.input.config.hideConfig());
        capture(h, s, name, tileX, tileY, scale);
    }

    void capture(VesperHarness h, Script s, String name, float tileX, float tileY, float scale){
        s.run(() -> hover = new Vec2(tileX * tilesize, tileY * tilesize));
        h.shot("control-" + IndustryUiSuite.window() + "-" + UiSuite.language() + "-" + name, tileX, tileY, scale, true);
    }

    /** The controller's panel, opened as a tap opens it. */
    void panel(VesperHarness h, Script s, String name){
        s.run(() -> control.input.config.showConfig(controller()));
        capture(h, s, name, ControlSuite.controllerX + 2.5f, ControlSuite.controllerY + 2.5f, 2.5f);
    }

    /** The configuration panel, down to a control labelled with this text, lies inside the window. */
    static boolean fits(String label){
        var button = control.input.config.getSelected() == null ? null : UiSuite.find(Core.scene.root, label);
        if(button == null) return false;
        var b = UiSuite.bounds(button);
        return b.x >= 0 && b.y >= 0 && b.x + b.width <= Core.graphics.getWidth() + 0.5f && b.y + b.height <= Core.graphics.getHeight() + 0.5f;
    }

    static ControllerBuild controller(){
        return ProtocolController.find(state.rules.defaultTeam);
    }
}
