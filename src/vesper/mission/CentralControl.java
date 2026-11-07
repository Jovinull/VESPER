package vesper.mission;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import vesper.world.AncientSite.*;
import vesper.world.ContainmentLock.*;
import vesper.world.ProtocolController;
import vesper.world.ProtocolController.*;
import vesper.world.ProtocolSite.*;

import static mindustry.Vars.*;

/**
 * Mission rules of Central Control, the second sector of Chapter Two: the complex the deep network answered to still
 * runs two industrial protocols, containment and recovery, from one controller. The player surveys the controller,
 * brings it power and cores, has its recovery protocol restore the distribution junction (access to the wings) and
 * both subsystems (materials first), shuts every vent at once with its containment protocol, and runs the
 * reactivation sequence while both subsystems keep working (see {@link ProtocolController}). The partial
 * reactivation is the victory: the native capture in the campaign, a game over won in a custom game. Only maps
 * tagged {@code vesper.scenario=central-control} are affected; progress persists in the {@code vesper.central} tag,
 * the protocols in the controller itself.
 */
public class CentralControl implements Scenario{
    public static final String scenarioId = "central-control", tagState = "vesper.central";

    public final Seq<Objective> objectives = new Seq<>();
    public int completed;
    public boolean victory;

    private @Nullable Rules bound;
    private boolean active;

    public CentralControl(){
        objectives.add(new Objective("cc-survey", () -> controller() != null && controller().known(),
            () -> controller() == null ? "" : Core.bundle.format("vesper.deep.survey", (int)(controller().investigation * 100))));
        objectives.add(new Objective("cc-power", () -> controller() != null && controller().known() && controller().power.status >= 0.9f
            && controller().items.has(controller().fuel()), this::powerText));
        objectives.add(new Objective("cc-access", () -> controller() != null && controller().access(), () -> siteText(false)));
        objectives.add(new Objective("cc-subsystems", () -> controller() != null && controller().restored(), () -> siteText(true)));
        objectives.add(new Objective("cc-containment", () -> controller() != null && controller().containedOnce, this::lockText));
        objectives.add(new Objective("cc-sequence", () -> controller() != null && controller().sequence == ProtocolController.Sequence.complete, this::sequenceText));

        Events.run(Trigger.update, this::update);
        Events.on(ResetEvent.class, e -> {
            bound = null;
            active = false;
        });
        Events.on(GameOverEvent.class, e -> {
            if(active() && e.winner == state.rules.defaultTeam) win();
        });
        //in the campaign the native capture is the victory
        Events.on(SectorCaptureEvent.class, e -> {
            if(active() && e.sector == state.rules.sector) win();
        });
    }

    @Override
    public boolean active(){
        return active && bound == state.rules && state.isGame();
    }

    @Override
    public String key(){
        return "vesper.central";
    }

    @Override
    public Seq<Objective> objectives(){
        return objectives;
    }

    @Override
    public int completed(){
        return completed;
    }

    @Override
    public boolean victory(){
        return victory;
    }

    public @Nullable ControllerBuild controller(){
        return ProtocolController.find(state.rules.defaultTeam);
    }

    String powerText(){
        var c = controller();
        if(c == null) return "";
        return Core.bundle.format("vesper.central.power", (int)(c.power.status * 100f), c.items.get(c.fuel()));
    }

    String siteText(boolean subsystems){
        var c = controller();
        if(c == null) return "";
        StringBuilder out = new StringBuilder();
        for(ProtocolSiteBuild s : c.sites()){
            if(subsystems != ((vesper.world.ProtocolSite)s.block).subsystem) continue;
            out.append(out.length() == 0 ? "" : "\n").append(s.block.localizedName).append(": ").append(s.barText());
        }
        return out.toString();
    }

    String lockText(){
        var c = controller();
        if(c == null) return "";
        var locks = c.locks();
        return Core.bundle.format("vesper.central.locks", locks.count(LockBuild::sealed), locks.size);
    }

    String sequenceText(){
        var c = controller();
        if(c == null) return "";
        return switch(c.sequence){
            case idle -> c.canStart() ? Core.bundle.get("vesper.central.start") : "";
            case running -> Core.bundle.format("vesper.central.sequence", (int)(c.progress * 100f)) + (c.reason() == null ? "" : "\n" + c.reason());
            case complete -> Core.bundle.get("vesper.protocol.bar.complete");
        };
    }

    private void win(){
        victory = true;
        completed = objectives.size;
        if(!net.client()) save();
    }

    private void update(){
        if(!state.isGame()) return;
        if(state.rules != bound) bind();
        if(!active) return;
        if(net.client() && state.gameOver && state.won){
            victory = true;
            completed = objectives.size;
        }
        if(state.gameOver) return;
        while(completed < objectives.size && objectives.get(completed).done.get()){
            completed++;
            if(!net.client()) save();
        }
        //the partial reactivation: the native capture in the campaign, a won game over otherwise
        if(!net.client() && completed >= objectives.size && !victory){
            if(state.isCampaign()){
                Call.sectorCapture();
            }else{
                state.gameOver = true;
                Events.fire(new GameOverEvent(state.rules.defaultTeam));
            }
        }
    }

    private void bind(){
        bound = state.rules;
        active = scenarioId.equals(state.rules.tags.get(FirstAwakening.tagScenario));
        completed = 0;
        victory = false;
        if(!active) return;
        String[] v = state.rules.tags.get(tagState, "").split(";");
        if(v.length >= 3 && v[0].equals("1")){
            completed = Math.min(Strings.parseInt(v[1], 0), objectives.size);
            victory = v[2].equals("1");
        }
    }

    private void save(){
        state.rules.tags.put(tagState, "1;" + completed + ";" + (victory ? 1 : 0));
    }
}
