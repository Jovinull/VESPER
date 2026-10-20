package vesper.mission;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import vesper.world.AncientSite.*;
import vesper.world.SignalNode.*;
import vesper.world.SignalTerminal;
import vesper.world.SignalTerminal.*;

import static mindustry.Vars.*;

/**
 * Mission rules of Deep Signal, the first sector of Chapter Two: under the Basin a network of the builders still
 * runs an automatic protocol. The player surveys its control terminal, brings its three relay nodes online (power
 * and resonance cores, each in its own chamber) and starts a synchronization that only advances while all three
 * keep running (see {@link SignalTerminal}). The synchronized network is the victory: the native capture in the
 * campaign, a game over won in a custom game (which touches no campaign progress). Only maps tagged
 * {@code vesper.scenario=deep-signal} are affected; progress persists in the {@code vesper.deep} tag, the
 * synchronization in the terminal itself.
 */
public class DeepSignal implements Scenario{
    public static final String scenarioId = "deep-signal", tagState = "vesper.deep";

    public final Seq<Objective> objectives = new Seq<>();
    public int completed;
    public boolean victory;

    private @Nullable Rules bound;
    private boolean active;

    public DeepSignal(){
        objectives.add(new Objective("deep-terminal", () -> terminal() != null && terminal().siteState != SiteState.unexplored,
            () -> terminal() == null ? "" : Core.bundle.format("vesper.deep.survey", (int)(terminal().investigation * 100))));
        objectives.add(new Objective("deep-nodes", () -> terminal() != null && terminal().allOperational(),
            this::nodesText));
        objectives.add(new Objective("deep-sync", () -> terminal() != null && terminal().syncState == SignalTerminal.SyncState.synced, this::syncText));

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
        return "vesper.deep";
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

    public @Nullable TerminalBuild terminal(){
        return SignalTerminal.find(state.rules.defaultTeam);
    }

    /** Where a node lies from the terminal, as a player reads the map: west, north, east or south. */
    public static String direction(TerminalBuild terminal, NodeBuild node){
        float a = Angles.angle(terminal.x, terminal.y, node.x, node.y);
        String dir = a >= 45f && a < 135f ? "north" : a >= 135f && a < 225f ? "west" : a >= 225f && a < 315f ? "south" : "east";
        return Core.bundle.get("vesper.node.dir." + dir);
    }

    String nodesText(){
        var t = terminal();
        if(t == null) return "";
        var nodes = t.nodes();
        StringBuilder out = new StringBuilder().append(nodes.count(NodeBuild::operational)).append('/').append(nodes.size);
        for(NodeBuild n : nodes){
            out.append("\n").append(direction(t, n)).append(": ").append(n.reason().localized());
        }
        return out.toString();
    }

    String syncText(){
        var t = terminal();
        if(t == null) return "";
        return switch(t.syncState){
            case idle -> t.canStart() ? Core.bundle.get("vesper.deep.start") : nodesText();
            case syncing -> Core.bundle.format("vesper.deep.syncing", (int)(t.progress * 100f), (int)(t.checkpoint * 100f))
                + (t.interrupted > 0f ? "\n" + Core.bundle.format("vesper.terminal.interrupted", Strings.autoFixed(Math.max(0f, t.grace() - t.interrupted) / 60f, 0)) + "\n" + nodesText() : "");
            case synced -> Core.bundle.get("vesper.terminal.sync.synced");
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
        //the synchronized network: the native capture in the campaign, a won game over otherwise
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
