package vesper.mission;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.world.*;
import vesper.content.*;

import static mindustry.Vars.*;

/**
 * Mission rules of Resonance Fault, the experimental map of the planetary response: mine resonance crystal along
 * the fault and store it in the core while the native waves keep coming. How hard the basin is pushed is the
 * player's choice: stabilizers keep it quiet and the extractors slow; the reactor and heavy industry make the
 * resonance machines up to twice as fast but bring pulses and patrols. The game declares victory (native game
 * over, the player's team winning) once the core holds the goal. Only maps tagged
 * {@code vesper.scenario=resonance-fault} are affected; progress persists in the {@code vesper.fault} tag.
 */
public class ResonanceFault implements Scenario{
    public static final String scenarioId = "resonance-fault", tagState = "vesper.fault";
    /** Resonance crystal the core must hold to win. */
    public static final int goal = 2000;
    /** Extractors the first objectives ask for. */
    public static final int extractors = 3;

    public final Seq<Objective> objectives = new Seq<>();
    public int completed;
    public boolean victory;

    private @Nullable Rules bound;
    private boolean active;

    public ResonanceFault(){
        objectives.add(new Objective("fault-extractors", () -> count(VBlocks.crystalExtractor, false) >= extractors,
            () -> Math.min(count(VBlocks.crystalExtractor, false), extractors) + "/" + extractors));
        objectives.add(new Objective("fault-power", () -> count(VBlocks.crystalExtractor, true) >= extractors,
            () -> Math.min(count(VBlocks.crystalExtractor, true), extractors) + "/" + extractors));
        objectives.add(new Objective("fault-store", () -> stored() >= goal, () -> stored() + "/" + goal));

        Events.run(Trigger.update, this::update);
        Events.on(ResetEvent.class, e -> {
            bound = null;
            active = false;
        });
        Events.on(GameOverEvent.class, e -> {
            if(active() && e.winner == state.rules.defaultTeam){
                victory = true;
                completed = objectives.size;
                save();
            }
        });
    }

    @Override
    public boolean active(){
        return active && bound == state.rules && state.isGame();
    }

    @Override
    public String key(){
        return "vesper.fault";
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

    /** Resonance crystal in the core (core items are synced to clients). */
    public int stored(){
        var core = state.rules.defaultTeam.core();
        return core == null ? 0 : core.items.get(VItems.resonanceCrystal);
    }

    static int count(Block block, boolean working){
        return state.rules.defaultTeam.data().getBuildings(block).count(b -> !working || b.efficiency > 0f);
    }

    private void update(){
        if(!state.isGame()) return;
        if(state.rules != bound) bind();
        if(!active) return;
        if(net.client() && state.gameOver && state.won){
            //GameOverEvent fires only on the server: read the outcome from the synced game over state
            victory = true;
            completed = objectives.size;
        }
        if(state.gameOver) return;

        while(completed < objectives.size && objectives.get(completed).done.get()){
            completed++;
            if(!net.client()) save();
        }
        //every objective done: the game declares the player's victory, the way the native win wave does
        if(!net.client() && completed >= objectives.size){
            state.gameOver = true;
            Events.fire(new GameOverEvent(state.rules.defaultTeam));
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
