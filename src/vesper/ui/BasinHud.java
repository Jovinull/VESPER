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

import static mindustry.Vars.*;

/**
 * Client-side feedback for Vesper scenarios: a small native-styled panel with the mission objective and the
 * planetary activity bar, plus announcements when the state changes. It only observes the synced state, so it
 * behaves the same in single player and as a multiplayer client, and it never replays notices after a load.
 */
public class BasinHud{
    private final PlanetaryActivity activity = Vesper.activity;
    private final FirstAwakening mission = Vesper.mission;

    private boolean seen;
    private Stage lastStage = Stage.dormant;
    private boolean lastPending, lastReleased, lastVictory, lastHolding;
    private int lastCompleted;
    private float effectTimer;
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
        return ui.hudfrag.shown && (activity.active() || mission.active());
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
                p.label(() -> mission.active() ? Core.bundle.get("vesper.mission.title") : Core.bundle.get("vesper.activity.title"))
                    .color(Pal.accent);
                p.row();
                p.label(this::objectiveText).wrap().padTop(4f).visible(mission::active);
                p.row();
                p.add("@vesper.activity.title").color(Color.lightGray).padTop(8f).visible(() -> activity.active() && mission.active());
                p.row();
                p.add(new Bar(
                    () -> Core.bundle.format("vesper.activity.bar", (int)activity.level, activity.stage.localized()),
                    () -> stageColor(activity.stage),
                    activity::fraction)).height(26f).padTop(3f).visible(activity::active);
                p.row();
                //one line for the threat countdown or, when nothing is coming, the next consequence (no empty gap between)
                p.label(this::footerText).wrap().padTop(6f).visible(activity::active);
            }).touchable(arc.scene.event.Touchable.enabled).get()
                .clicked(() -> ui.showInfoText("@vesper.activity.title", "@vesper.activity.help"));
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
        var objective = mission.current();
        if(objective == null) return Core.bundle.get("vesper.objective.complete");
        String progress = objective.progress == null ? "" : objective.progress.get();
        return Core.bundle.format("vesper.objective.line", mission.completed + 1, mission.objectives.size, objective.text())
            + (progress.isEmpty() ? "" : " [lightgray]" + progress);
    }

    private String footerText(){
        if(activity.pending > 0f) return "[#" + VPal.dangerLight + "]" + warningText();
        String hint = hintText();
        return hint.isEmpty() ? "" : "[lightgray]" + hint;
    }

    private String warningText(){
        return Core.bundle.format(activity.pendingAwakening ? "vesper.activity.incoming.awakening" : "vesper.activity.incoming.patrol",
            UI.formatTime(activity.pending), activity.pendingSentinels + activity.pendingCrawlers);
    }

    /** What the player can expect next; keeps the consequences of their choices predictable. */
    private String hintText(){
        var reactor = mission.reactor();
        if(!activity.awakened && reactor != null && reactor.primed() && !reactor.ignited){
            return Core.bundle.format("vesper.activity.hint.ignite", activity.awakeningSentinels() + activity.awakeningCrawlers());
        }
        if(mission.holding()){
            return Core.bundle.get("vesper.mission.hold");
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
        if(!state.isGame() || !(activity.active() || mission.active())){
            return;
        }
        boolean pending = activity.pending > 0f;
        if(!seen){
            seen = true;
            lastStage = activity.stage;
            lastPending = pending;
            lastCompleted = mission.completed;
            lastReleased = mission.released;
            lastVictory = mission.victory;
            lastHolding = mission.holding();
            return;
        }

        if(activity.stage.ordinal() > lastStage.ordinal()){
            ui.hudfrag.showToast(Icon.warning, Core.bundle.get("vesper.activity.toast." + activity.stage.name()));
        }
        if(pending && !lastPending){
            announcements.add(Core.bundle.format(activity.pendingAwakening ? "vesper.activity.announce.awakening" : "vesper.activity.announce.patrol",
                (int)(activity.pending / 60f)));
            Sounds.explosionArtilleryShock.play(0.5f);
        }
        if(!pending && lastPending){
            ui.hudfrag.showToast(Icon.warning, Core.bundle.get("vesper.activity.toast.emerged"));
        }
        if(mission.completed > lastCompleted && mission.completed <= mission.objectives.size){
            var done = mission.objectives.get(mission.completed - 1);
            ui.hudfrag.showToast(Icon.ok, Core.bundle.format("vesper.objective.done", done.text()));
        }
        if(mission.released && !lastReleased){
            announcements.add(Core.bundle.get("vesper.mission.final"));
        }
        if(mission.holding() && !lastHolding){
            ui.hudfrag.showToast(Icon.warning, Core.bundle.get("vesper.mission.hold.toast"));
        }
        if(mission.victory && !lastVictory){
            //the native game over dialog only says "game over"; say what was won first
            announcements.clear();
            announceUntil = 0f;
            announcements.add(Core.bundle.get("vesper.mission.victory"));
        }

        lastStage = activity.stage;
        lastPending = pending;
        lastCompleted = mission.completed;
        lastReleased = mission.released;
        lastVictory = mission.victory;
        lastHolding = mission.holding();

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
    }
}
