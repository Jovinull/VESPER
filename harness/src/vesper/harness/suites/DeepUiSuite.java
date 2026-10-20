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
import vesper.world.SignalNode.*;
import vesper.world.SignalTerminal.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Captures of the deep network as a player sees it, in the window size and language the client runs with
 * (-PharnessWindow, -PharnessLocale), in the purpose-built world of {@link DeepSignalSuite}: the terminal before its
 * survey, every relay state with its native bars (dark, switched off, without power, without cores, operational,
 * interrupted with its cause, hit by a pulse, in sync), the terminal's panel when it waits, when it is ready, while
 * it synchronizes, interrupted and done; the database pages; and the shipped map as it starts, with its objectives.
 * The info panel shows the building under the mouse, so the suite pins the desktop mouse over it. Fixtures: the
 * survey is completed by the test, states are set by switching relays off and holding the activity up. Checks
 * that every text shown is translated and that the terminal's panel fits the window; the rest is evidence.
 */
public class DeepUiSuite implements VesperHarness.Suite{
    boolean listening;
    float holdShock = -1f;
    @Nullable Vec2 hover;

    @Override
    public void define(VesperHarness h, Script s){
        if(headless) return;
        if(!listening){
            listening = true;
            Events.run(Trigger.update, () -> {
                if(holdShock >= 0f && Vesper.activity.active()) Vesper.activity.shock = holdShock;
                if(hover != null) IndustryUiSuite.pin(hover);
            });
        }
        var a = Vesper.activity;
        s.run(() -> {
            String[] wh = System.getProperty("vesper.harness.window", "").split("x");
            if(wh.length == 2) UiSuite.resize(h, Strings.parseInt(wh[0], 1280), Strings.parseInt(wh[1], 720));
        });
        s.wait(1f);
        s.run(() -> {
            h.line("deep-ui: window " + Core.graphics.getWidth() + "x" + Core.graphics.getHeight() + ", language " + UiSuite.language());
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.tags.put(FirstAwakening.tagScenario, DeepSignal.scenarioId);
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagResponse, "true");
            r.tags.put(AncientSite.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagGates, DeepSignalSuite.ventX + "," + DeepSignalSuite.ventY);
            Worlds.generate(DeepSignalSuite.width, DeepSignalSuite.height, DeepSignalSuite::generate, r);
        });
        s.wait(0.5f);
        s.run(() -> {
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
        });
        shot(h, s, "01-terminal-unexplored", DeepSignalSuite.terminalX + 2f, DeepSignalSuite.terminalY + 2f, 3f);
        shot(h, s, "02-relay-dark", DeepSignalSuite.relays[0][0] + 1.5f, DeepSignalSuite.relays[0][1] + 1.5f, 4f);

        // --- the survey done: relays known, in four different states
        s.run(() -> {
            terminal().investigate(1f);
            for(int i = 0; i < 3; i++) if(i != 1) DeepSignalSuite.feed(world.build(DeepSignalSuite.relays[i][0] - 2, DeepSignalSuite.relays[i][1] + 1), resonanceCore, 40);
            node(0).handleStack(resonanceCore, 10, null);
            node(2).configure(false);
        });
        s.wait(2f);
        panel(h, s, "03-terminal-waiting");
        shot(h, s, "04-relay-operational", DeepSignalSuite.relays[0][0] + 1.5f, DeepSignalSuite.relays[0][1] + 1.5f, 4f);
        shot(h, s, "05-relay-unpowered", DeepSignalSuite.relays[1][0] + 1.5f, DeepSignalSuite.relays[1][1] + 1.5f, 4f);
        shot(h, s, "06-relay-offline", DeepSignalSuite.relays[2][0] + 1.5f, DeepSignalSuite.relays[2][1] + 1.5f, 4f);
        s.run(() -> {
            DeepSignalSuite.feed(world.build(DeepSignalSuite.relays[1][0] - 2, DeepSignalSuite.relays[1][1] + 1), resonanceCore, 40);
            node(2).configure(true);
        });
        s.wait(1.5f);
        shot(h, s, "07-relay-starved", DeepSignalSuite.relays[1][0] + 1.5f, DeepSignalSuite.relays[1][1] + 1.5f, 4f);
        s.run(() -> {
            node(1).handleStack(resonanceCore, 10, null);
            node(2).handleStack(resonanceCore, 10, null);
        });
        s.wait(1f);
        panel(h, s, "08-terminal-ready");
        s.check("deep-ui: the terminal's panel fits the window and offers the start", () -> fits() && terminal().canStart(), () -> "");

        // --- synchronizing, interrupted, hit by a pulse, done
        s.run(() -> terminal().configure(true));
        s.wait(10f);
        panel(h, s, "09-terminal-syncing");
        shot(h, s, "10-relay-in-sync", DeepSignalSuite.relays[0][0] + 1.5f, DeepSignalSuite.relays[0][1] + 1.5f, 4f);
        s.run(() -> node(0).configure(false));
        s.wait(2f);
        panel(h, s, "11-terminal-interrupted");
        shot(h, s, "12-relay-interrupted", DeepSignalSuite.relays[0][0] + 1.5f, DeepSignalSuite.relays[0][1] + 1.5f, 4f);
        s.run(() -> {
            node(0).configure(true);
            holdShock = 30f;
            a.pulseCooldown = 0f;
        });
        s.waitFor(() -> a.pulseActive > 0f, 60f);
        s.run(() -> a.pulseCooldown = 1e9f);
        s.wait(1f);
        shot(h, s, "13-relay-pulse", DeepSignalSuite.relays[2][0] + 1.5f, DeepSignalSuite.relays[2][1] + 1.5f, 2.5f);
        s.run(() -> {
            holdShock = -1f;
            terminal().progress = 0.999f;
        });
        s.waitFor(() -> a.pulseActive < 0f, 25f);
        s.waitFor(() -> terminal().syncState == SyncState.synced, 30f);
        s.wait(1f);
        panel(h, s, "14-terminal-synchronized");
        s.check("deep-ui: every relay and terminal text is translated", () -> {
            for(NodeState n : NodeState.values()) if(!IndustryUiSuite.translated(n.localized())) return false;
            for(SyncState n : SyncState.values()) if(!IndustryUiSuite.translated(n.localized())) return false;
            for(String k : new String[]{"vesper.terminal.ready", "vesper.terminal.waiting", "vesper.terminal.done", "vesper.terminal.start", "vesper.site.signal-terminal.purpose",
                "vesper.node.dir.north", "vesper.node.dir.west", "vesper.node.dir.east", "vesper.deep.title", "vesper.deep.start"}){
                if(!IndustryUiSuite.translated(Core.bundle.get(k))) return false;
            }
            return IndustryUiSuite.translated(signalNode.localizedName) && IndustryUiSuite.translated(signalNode.description)
                && IndustryUiSuite.translated(signalTerminal.localizedName) && IndustryUiSuite.translated(signalTerminal.description);
        }, () -> signalNode.localizedName);
        s.run(() -> {
            hover = null;
            control.input.config.hideConfig();
            if(ui.restart.isShown()) ui.restart.hide();
        });

        // --- the database pages
        s.run(() -> ui.content.show(signalNode));
        h.shot("deep-" + IndustryUiSuite.window() + "-" + UiSuite.language() + "-15-relay-stats", 0f, 0f, 2f, true);
        s.run(() -> ui.content.hide());
        s.run(() -> ui.content.show(cargoUnloader));
        h.shot("deep-" + IndustryUiSuite.window() + "-" + UiSuite.language() + "-16-unloader-stats", 0f, 0f, 2f, true);
        s.run(() -> ui.content.hide());

        // --- the shipped map as it starts: the terminal's marker and the objectives in the HUD
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.deepName), Gamemode.survival));
        s.wait(3f);
        shot(h, s, "17-map-control-hall", 64f, 49f, 2f);
        shot(h, s, "18-map-north-relay", 60.5f, 79.5f, 2.5f);
        s.check("deep-ui: the scenario's texts are translated", () -> translatedObjectives(), () -> Core.bundle.get("vesper.deep.title"));
        s.run(() -> {
            hover = null;
            if(ui.restart.isShown()) ui.restart.hide();
        });
    }

    static boolean translatedObjectives(){
        for(var o : Vesper.deep.objectives) if(!IndustryUiSuite.translated(o.text())) return false;
        return IndustryUiSuite.translated(Core.bundle.get("vesper.deep.title"));
    }

    void shot(VesperHarness h, Script s, String name, float tileX, float tileY, float scale){
        s.run(() -> hover = new Vec2(tileX * tilesize, tileY * tilesize));
        h.shot("deep-" + IndustryUiSuite.window() + "-" + UiSuite.language() + "-" + name, tileX, tileY, scale, true);
    }

    /** The terminal's panel, opened as a tap opens it. */
    void panel(VesperHarness h, Script s, String name){
        s.run(() -> control.input.config.showConfig(terminal()));
        shot(h, s, name, DeepSignalSuite.terminalX + 2f, DeepSignalSuite.terminalY + 2f, 2.5f);
    }

    /** The configuration panel lies inside the window. */
    static boolean fits(){
        var table = control.input.config.getSelected() == null ? null : UiSuite.find(Core.scene.root, Core.bundle.get("vesper.terminal.start"));
        if(table == null) return false;
        var b = UiSuite.bounds(table);
        return b.x >= 0 && b.y >= 0 && b.x + b.width <= Core.graphics.getWidth() + 0.5f && b.y + b.height <= Core.graphics.getHeight() + 0.5f;
    }

    static TerminalBuild terminal(){
        return SignalTerminal.find(state.rules.defaultTeam);
    }

    static NodeBuild node(int i){
        return DeepSignalSuite.node(i);
    }
}
