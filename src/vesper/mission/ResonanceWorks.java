package vesper.mission;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import vesper.content.*;
import vesper.world.ResonanceDynamo.*;
import vesper.world.WorksRelay.*;

import static mindustry.Vars.*;

/**
 * Mission rules of Resonance Works, a standalone map of the resonance industry (not part of Chapter One): an ancient
 * power works waits for power, and the objective is industrial operation, not generation on paper. The Works Relay
 * counts the energy it actually receives over time; what an unfed, starved or overloaded grid never delivered never
 * counts. Resonance cores are the fuel: the dynamos burn them for the relay, the resonators make them from crystal
 * and circuits. How hard the basin is pushed is the player's choice (the fed works disturb it too). The game
 * declares victory once the relay has received the goal. Only maps tagged {@code vesper.scenario=resonance-works} are
 * affected; progress persists in the {@code vesper.works} tag, the energy in the relay itself.
 */
public class ResonanceWorks implements Scenario{
    public static final String scenarioId = "resonance-works", tagState = "vesper.works";
    /** Energy the relay must receive (power units: 240000 is 8.3 minutes of its full 480 power/s). */
    public static final int goal = 240000;
    /** Cores the dynamos must have burned before the operation counts as running on its own fuel. */
    public static final int coresBurned = 30;

    public final Seq<Objective> objectives = new Seq<>();
    public int completed;
    public boolean victory;

    private @Nullable Rules bound;
    private boolean active;
    private int relayPos = -1;

    public ResonanceWorks(){
        objectives.add(new Objective("works-dynamo", () -> dynamoFeedsRelay(), null));
        objectives.add(new Objective("works-cores", () -> burned() >= coresBurned, () -> Math.min(burned(), coresBurned) + "/" + coresBurned));
        objectives.add(new Objective("works-deliver", () -> delivered() >= goal, () -> Core.bundle.format("vesper.works.progress",
            UI.formatAmount((long)delivered()), UI.formatAmount(goal), relay() == null ? 0 : (int)relay().rate)));

        Events.run(Trigger.update, this::update);
        Events.on(ResetEvent.class, e -> {
            bound = null;
            active = false;
            relayPos = -1;
        });
        Events.on(GameOverEvent.class, e -> {
            if(active() && e.winner == state.rules.defaultTeam) win();
        });
    }

    @Override
    public boolean active(){
        return active && bound == state.rules && state.isGame();
    }

    @Override
    public String key(){
        return "vesper.works";
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

    /** The map's relay (looked up once, then by position). */
    public @Nullable RelayBuild relay(){
        if(world.build(relayPos) instanceof RelayBuild r) return r;
        var found = state.rules.defaultTeam.data().getBuildings(VBlocks.worksRelay);
        if(found.isEmpty() || !(found.first() instanceof RelayBuild r)) return null;
        relayPos = r.pos();
        return r;
    }

    /** Energy the relay has received (synced with the block). */
    public double delivered(){
        var r = relay();
        return r == null ? 0 : r.delivered;
    }

    /** Cores burned by the team's dynamos (each dynamo keeps its count, synced and saved). */
    public int burned(){
        int sum = 0;
        for(var b : state.rules.defaultTeam.data().getBuildings(VBlocks.resonanceDynamo)) if(b instanceof ResonanceDynamoBuild d) sum += d.coresBurned;
        return sum;
    }

    /** A working dynamo on the relay's grid. */
    boolean dynamoFeedsRelay(){
        var r = relay();
        if(r == null || r.power == null) return false;
        for(var b : r.power.graph.producers) if(b instanceof ResonanceDynamoBuild d && d.productionEfficiency > 0f) return true;
        return false;
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
            //GameOverEvent fires only on the server: read the outcome from the synced game over state
            victory = true;
            completed = objectives.size;
        }
        if(state.gameOver) return;

        while(completed < objectives.size && objectives.get(completed).done.get()){
            completed++;
            if(!net.client()) save();
        }
        //every objective done: the game declares the player's victory (a standalone map: never a campaign capture)
        if(!net.client() && completed >= objectives.size && !victory){
            state.gameOver = true;
            Events.fire(new GameOverEvent(state.rules.defaultTeam));
        }
    }

    private void bind(){
        bound = state.rules;
        active = scenarioId.equals(state.rules.tags.get(FirstAwakening.tagScenario));
        completed = 0;
        victory = false;
        relayPos = -1;
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
