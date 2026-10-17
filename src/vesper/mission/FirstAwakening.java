package vesper.mission;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;
import vesper.*;
import vesper.content.*;
import vesper.world.DerelictReactor.*;

import static mindustry.Vars.*;

/**
 * Mission rules of First Awakening, layered on the native waves and game over.
 * <ul>
 * <li>Objectives guide the player (the HUD shows the current one). Progress only moves forward and persists in
 * the rules tags.</li>
 * <li>The wave timer holds before the fifth wave until the basin has awakened (the player ignited the reactor),
 * so the final waves always follow the awakening; the native win wave is armed at that moment and the game
 * declares victory once the last wave is destroyed.</li>
 * </ul>
 * Native map objectives are not used: while one is displayed, the HUD hides the wave countdown, which this
 * mission needs. Only maps tagged {@code vesper.scenario=first-awakening} are affected.
 */
public class FirstAwakening implements Scenario{
    public static final String tagScenario = "vesper.scenario", scenarioId = "first-awakening", tagState = "vesper.mission";
    /** Value of {@code state.wave} (the HUD's wave counter) at which the timer holds until the awakening. */
    public static final int holdWave = 5;
    /** Number of attack waves; the native win wave is the counter value after the last one spawned. */
    public static final int attackWaves = 6;
    /** Countdown to the fifth wave once the basin awakens, so the awakening response arrives first. */
    public static final float releaseDelay = 75f * 60f;

    public final Seq<Objective> objectives = new Seq<>();
    /** Number of completed objectives; the next one is objectives.get(completed). */
    public int completed;
    public boolean released, victory;

    private @Nullable Rules bound;
    private boolean active;
    private float timer;

    public FirstAwakening(){
        count("drills", VBlocks.surveyDrill, 4);
        count("forge", VBlocks.structuralForge, 1);
        count("turrets", VBlocks.kineticTurret, 2);
        count("power", VBlocks.combustionEngine, 1);
        count("assembler", VBlocks.componentAssembler, 1);
        objectives.add(new Objective("restore", () -> reactor() != null && (reactor().primed() || reactor().ignited),
            () -> reactor() == null ? "" : (int)(reactor().progress() * 100f) + "%"));
        objectives.add(new Objective("ignite", () -> Vesper.activity.awakened || (reactor() != null && reactor().ignited), null));
        //same numbers as the native wave counter next to it
        objectives.add(new Objective("survive", () -> victory && state.gameOver,
            () -> Core.bundle.format("vesper.objective.wave", state.wave, state.rules.winWave > 0 ? state.rules.winWave : attackWaves + 1)));

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

    private void count(String key, Block block, int amount){
        objectives.add(new Objective(key, () -> state.rules.defaultTeam.data().getBuildings(block).size >= amount,
            () -> Math.min(state.rules.defaultTeam.data().getBuildings(block).size, amount) + "/" + amount));
    }

    @Override
    public boolean active(){
        return active && bound == state.rules && state.isGame();
    }

    @Override
    public String key(){
        return "vesper.mission";
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

    /** The scenario's reactor, if the map has one. */
    public @Nullable ReactorBuild reactor(){
        var list = state.rules.defaultTeam.data().getBuildings(VBlocks.derelictReactor);
        return list.isEmpty() ? null : (ReactorBuild)list.first();
    }

    /** Whether the final waves are being held right now (shown by the HUD; synced through the wave rules). */
    public boolean holding(){
        return active() && !Vesper.activity.awakened && state.wave >= holdWave && !state.rules.waveTimer;
    }

    private void update(){
        if(!state.isGame()) return;
        if(state.rules != bound) bind();
        if(!active) return;
        if(net.client()){
            //GameOverEvent fires only on the server and the mission tags are not re-sent after joining:
            //read the outcome from the synced game over state and the release from the synced win wave
            if(state.gameOver && state.won){
                victory = true;
                completed = objectives.size;
            }
            if(state.rules.winWave > 0) released = true;
        }
        if(state.gameOver) return;

        //objectives only advance; they are evaluated everywhere from synced world state
        while(completed < objectives.size && objectives.get(completed).done.get()){
            completed++;
            if(!net.client()) save();
        }

        if(net.client() || (timer += Time.delta) < 30f) return;
        timer = 0f;

        if(!Vesper.activity.awakened){
            //hold the final waves until the player wakes the basin
            if(state.wave >= holdWave && (state.rules.waveTimer || state.rules.waveSending)){
                setRule("waveTimer", false);
                setRule("waveSending", false);
                state.rules.waveTimer = state.rules.waveSending = false;
            }
        }else if(!released){
            released = true;
            state.rules.winWave = attackWaves + 1;
            state.rules.waveTimer = state.rules.waveSending = true;
            setRule("winWave", state.rules.winWave);
            setRule("waveTimer", true);
            setRule("waveSending", true);
            if(state.wave >= holdWave) state.wavetime = releaseDelay;
            save();
        }
    }

    private void bind(){
        bound = state.rules;
        active = scenarioId.equals(state.rules.tags.get(tagScenario));
        completed = 0;
        released = victory = false;
        timer = 0f;
        if(!active) return;
        String[] v = state.rules.tags.get(tagState, "").split(";");
        if(v.length >= 4 && v[0].equals("1")){
            completed = Math.min(Strings.parseInt(v[1], 0), objectives.size);
            released = v[2].equals("1");
            victory = v[3].equals("1");
        }
    }

    private void save(){
        state.rules.tags.put(tagState, "1;" + completed + ";" + (released ? 1 : 0) + ";" + (victory ? 1 : 0));
    }

    private static void setRule(String name, Object value){
        if(net.server()) Call.setRule(name, String.valueOf(value));
    }
}
