package vesper.ui;

import arc.*;
import arc.graphics.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import vesper.*;
import vesper.graphics.*;
import vesper.mission.*;
import vesper.systems.*;
import vesper.systems.PlanetaryActivity.*;
import vesper.world.*;

import static mindustry.Vars.*;

/**
 * Client-side feedback for Vesper scenarios: a small native-styled panel with the mission objective and the
 * planetary activity bar, plus announcements when the state changes (threats, pulses, objectives). It only
 * observes the synced state, so it behaves the same in single player and as a multiplayer client, and it never
 * replays notices after a load.
 */
public class BasinHud{
    private final PlanetaryActivity activity = Vesper.activity;
    private final FirstAwakening mission = Vesper.mission;

    private boolean seen;
    private Stage lastStage = Stage.dormant;
    private boolean lastPending, lastReleased, lastVictory, lastHolding, lastPulsePending, lastPulseActive;
    private int lastCompleted;
    private float effectTimer, pulseEffectTimer;
    /** Announcements wait for the previous one, so two state changes in a row never overlap on screen. */
    private final Seq<String> announcements = new Seq<>();
    private float announceUntil;
    static final float announceSeconds = 4.5f;

    public BasinHud(){
        Events.on(ClientLoadEvent.class, e -> build());
        Events.on(ResetEvent.class, e -> {
            seen = false;
            announcements.clear();
        });
        Events.run(Trigger.update, this::observe);
    }

    private boolean visible(){
        return ui.hudfrag.shown && (activity.active() || Vesper.scenario() != null);
    }

    private void build(){
        ui.hudGroup.fill(t -> {
            t.name = "vesper-basin";
            //left edge, vertically centred: clear of the wave panel (top left), the minimap (top right) and the build
            //menu (bottom right) at every window size the UI suite checks
            t.left().marginLeft(4f);
            t.visible(this::visible);
            t.table(Styles.black6, p -> {
                p.margin(8f).defaults().left().width(270f);
                p.label(() -> Vesper.scenario() != null ? Core.bundle.get(Vesper.scenario().key() + ".title") : Core.bundle.get("vesper.activity.title"))
                    .color(Pal.accent);
                p.row();
                p.label(this::objectiveText).wrap().padTop(4f).visible(() -> Vesper.scenario() != null);
                p.row();
                p.label(() -> Core.bundle.get("vesper.activity.title") + " [accent](?)").color(Color.lightGray).padTop(8f)
                    .visible(() -> activity.active() && Vesper.scenario() != null);
                p.row();
                p.add(new Bar(
                    () -> Core.bundle.format("vesper.activity.bar", (int)activity.level, activity.stage.localized()),
                    () -> stageColor(activity.stage),
                    activity::fraction)).height(26f).padTop(3f).visible(activity::active);
                p.row();
                p.label(this::sourcesText).color(Color.lightGray).fontScale(0.85f).wrap().padTop(2f).visible(activity::active);
                p.row();
                //what the level buys: resonance machines run faster (shown where the planetary response is on)
                p.label(() -> Core.bundle.format("vesper.activity.resonance", Strings.autoFixed(activity.resonanceMultiplier(), 2)))
                    .color(VPal.resonanceLight).fontScale(0.85f).wrap().padTop(2f).visible(activity::responses);
                p.row();
                //one line for the threat countdown or, when nothing is coming, the next consequence (no empty gap between)
                p.label(this::footerText).wrap().padTop(6f).visible(activity::active);
            }).touchable(arc.scene.event.Touchable.enabled).get()
                .clicked(() -> ui.showInfoText("@vesper.activity.title", activity.responses() ? "@vesper.response.help" : "@vesper.activity.help"));
        });
    }

    public static Color stageColor(Stage stage){
        return switch(stage){
            case dormant -> VPal.resonanceDark;
            case stirring -> VPal.industrial;
            case awakened -> VPal.danger;
        };
    }

    private String objectiveText(){
        var scenario = Vesper.scenario();
        if(scenario == null) return "";
        var objective = scenario.current();
        if(objective == null) return Core.bundle.get(Core.bundle.has(scenario.key() + ".complete") ? scenario.key() + ".complete" : "vesper.objective.complete");
        String progress = objective.progress == null ? "" : objective.progress.get();
        return Core.bundle.format("vesper.objective.line", scenario.completed() + 1, scenario.objectives().size, objective.text())
            + (progress.isEmpty() ? "" : " [lightgray]" + progress);
    }

    private String sourcesText(){
        int industry = (int)activity.industry, ancient = (int)(activity.ancient + activity.shock);
        return activity.stabilization > 0.05f ? Core.bundle.format("vesper.activity.sources.stabilized", industry, ancient, Strings.autoFixed(activity.stabilization, 1)) :
            Core.bundle.format("vesper.activity.sources", industry, ancient);
    }

    private String footerText(){
        //threats first (incursion, pulse), each on its own line; otherwise the next consequence
        StringBuilder out = new StringBuilder();
        if(activity.pending > 0f) out.append("[#").append(VPal.dangerLight).append("]").append(warningText());
        String pulse = pulseText();
        if(!pulse.isEmpty()) out.append(out.length() > 0 ? "\n" : "").append("[#").append(VPal.resonanceLight).append("]").append(pulse);
        if(out.length() > 0) return out.toString();
        String hint = hintText();
        return hint.isEmpty() ? "" : "[lightgray]" + hint;
    }

    private String pulseText(){
        if(activity.pulsePending > 0f){
            return Core.bundle.format("vesper.pulse.incoming", UI.formatTime(activity.pulsePending), (int)activity.pulseShock,
                Strings.autoFixed(activity.pulseBoost, 1));
        }
        if(activity.pulseActive > 0f){
            return Core.bundle.format("vesper.pulse.active", UI.formatTime(activity.pulseActive), Strings.autoFixed(activity.pulseBoost, 1));
        }
        return "";
    }

    private String warningText(){
        //First Awakening's machines come out of the facility; on maps with the planetary response, out of the fault's gates
        return Core.bundle.format(activity.pendingAwakening ? "vesper.activity.incoming.awakening" + (activity.responses() ? ".gates" : "") : "vesper.activity.incoming.patrol",
            UI.formatTime(activity.pending), activity.pendingSentinels + activity.pendingCrawlers);
    }

    /** What the player can expect next; keeps the consequences of their choices predictable. */
    private String hintText(){
        var reactor = mission.reactor();
        if(!activity.awakened && reactor != null && reactor.primed() && !reactor.ignited){
            float power = ((DerelictReactor)reactor.block).powerProduction * 60f;
            return Core.bundle.format("vesper.activity.hint.ignite", activity.awakeningSentinels() + activity.awakeningCrawlers(),
                (int)(power * 0.5f), (int)(power * 1.5f));
        }
        if(mission.holding()){
            return Core.bundle.get("vesper.mission.hold");
        }
        if(activity.responses()){
            //maps with the planetary response: what the next step up the scale brings
            if(activity.stage == Stage.awakened) return Core.bundle.format("vesper.response.hint.awakened", activity.responseUnitCap);
            if(activity.level >= activity.pulseLevel) return Core.bundle.format("vesper.response.hint.pulses", (int)PlanetaryActivity.awakenedLevel);
            if(activity.stage == Stage.stirring) return Core.bundle.format("vesper.response.hint.stirring", (int)activity.pulseLevel);
            return Core.bundle.format("vesper.response.hint.dormant", (int)activity.resonanceFloor);
        }
        if(!activity.awakened && activity.stage == Stage.stirring){
            return Core.bundle.get("vesper.activity.hint.stirring");
        }
        if(activity.awakened && activity.stage == Stage.awakened && activity.patrols < activity.maxPatrols){
            return Core.bundle.get("vesper.activity.hint.awakened");
        }
        return "";
    }

    /** Announces transitions of the synced state. The first frame of a world only records it. */
    private void observe(){
        var scenario = Vesper.scenario();
        if(!state.isGame() || !(activity.active() || scenario != null)){
            return;
        }
        boolean pending = activity.pending > 0f, pulsePending = activity.pulsePending > 0f, pulseActive = activity.pulseActive > 0f;
        boolean victory = scenario != null && scenario.victory();
        int completed = scenario == null ? 0 : scenario.completed();
        if(!seen){
            seen = true;
            lastStage = activity.stage;
            lastPending = pending;
            lastCompleted = completed;
            lastReleased = mission.released;
            lastVictory = victory;
            lastHolding = mission.holding();
            lastPulsePending = pulsePending;
            lastPulseActive = pulseActive;
            return;
        }

        if(activity.stage.ordinal() > lastStage.ordinal()){
            ui.hudfrag.showToast(Icon.warning, Core.bundle.get("vesper.activity.toast." + activity.stage.name()));
        }
        if(pending && !lastPending){
            announcements.add(Core.bundle.format(activity.pendingAwakening ? "vesper.activity.announce.awakening" + (activity.responses() ? ".gates" : "") : "vesper.activity.announce.patrol",
                (int)(activity.pending / 60f)));
            Sounds.explosionArtilleryShock.play(0.5f);
        }
        if(!pending && lastPending){
            ui.hudfrag.showToast(Icon.warning, Core.bundle.get("vesper.activity.toast.emerged"));
        }
        if(scenario != null && completed > lastCompleted && completed <= scenario.objectives().size){
            var done = scenario.objectives().get(completed - 1);
            ui.hudfrag.showToast(Icon.ok, Core.bundle.format("vesper.objective.done", done.text()));
        }
        if(pulsePending && !lastPulsePending){
            announcements.add(Core.bundle.format("vesper.pulse.announce", (int)(activity.pulsePending / 60f)));
        }
        if(pulseActive && !lastPulseActive){
            ui.hudfrag.showToast(Icon.warning, Core.bundle.format("vesper.pulse.toast", Strings.autoFixed(activity.pulseBoost, 1)));
            VFx.pulseImpact.at(activity.pulseX * tilesize, activity.pulseY * tilesize, activity.pulseRadius);
            Sounds.explosionArtilleryShock.at(activity.pulseX * tilesize, activity.pulseY * tilesize, 1.4f, 0.5f);
        }
        if(mission.released && !lastReleased){
            announcements.add(Core.bundle.get("vesper.mission.final"));
        }
        if(mission.holding() && !lastHolding){
            ui.hudfrag.showToast(Icon.warning, Core.bundle.get("vesper.mission.hold.toast"));
        }
        if(victory && !lastVictory){
            //the native game over dialog only says "game over"; say what was won first
            announcements.clear();
            announceUntil = 0f;
            announcements.add(Core.bundle.get(scenario.key() + ".victory"));
        }

        lastStage = activity.stage;
        lastPending = pending;
        lastCompleted = completed;
        lastReleased = mission.released;
        lastVictory = victory;
        lastHolding = mission.holding();
        lastPulsePending = pulsePending;
        lastPulseActive = pulseActive;

        if(!announcements.isEmpty() && Time.globalTime >= announceUntil){
            ui.announce(announcements.remove(0), announceSeconds);
            announceUntil = Time.globalTime + announceSeconds * 60f;
        }

        //the gates flare while something is about to come out of them
        if(pending && (effectTimer += Time.delta) >= 45f){
            effectTimer = 0f;
            for(Tile gate : activity.gates()){
                VFx.gateWarning.at(gate.worldx(), gate.worldy());
            }
        }
        //the pulse shows where it will land, then the area it covers
        if((pulsePending || pulseActive) && (pulseEffectTimer += Time.delta) >= 50f){
            pulseEffectTimer = 0f;
            (pulsePending ? VFx.pulseWarning : VFx.pulseField).at(activity.pulseX * tilesize, activity.pulseY * tilesize, activity.pulseRadius);
        }
    }
}
