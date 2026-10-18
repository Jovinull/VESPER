package vesper.mission;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.*;
import vesper.content.*;
import vesper.world.AncientSite.*;

import static mindustry.Vars.*;

/**
 * Mission rules of The Buried Foundry, the experimental map of industrial archaeology: set up an industry at the rim
 * of an excavated foundry, survey its buried machines, restore or dismantle one, read the archive and survive what
 * reading it wakes. The waves hold before the fourth until the archive is read (the player decides when, with the
 * defenses ready); reading it arms the native win wave, so the game declares victory once the last wave is
 * destroyed. Restoring and dismantling change how the defense is built, never whether the mission can be finished:
 * no objective needs one choice in particular. Only maps tagged {@code vesper.scenario=buried-foundry} are affected;
 * progress persists in the {@code vesper.foundry} tag.
 */
public class BuriedFoundry implements Scenario{
    public static final String scenarioId = "buried-foundry", tagState = "vesper.foundry";
    /** Wave counter value at which the timer holds until the archive is read, and the attack waves in all. */
    public static final int holdWave = 4, attackWaves = 6;
    /** Countdown to the first of the final waves once the archive is read. */
    public static final float releaseDelay = 60f * 60f;

    public final Seq<Objective> objectives = new Seq<>();
    public int completed;
    public boolean released, victory;

    private @Nullable Rules bound;
    private boolean active;
    private float timer;

    public BuriedFoundry(){
        objectives.add(count("drills", VBlocks.surveyDrill, 4));
        objectives.add(count("forge", VBlocks.structuralForge, 1));
        objectives.add(count("foundry-rig", VBlocks.surveyRig, 1));
        objectives.add(new Objective("foundry-investigate", () -> sites(VBlocks.ancientRecoverer, SiteState.investigated, SiteState.dismantling,
            SiteState.restoring, SiteState.restored, SiteState.dismantled) > 0, null));
        objectives.add(new Objective("foundry-decide", () -> sites(VBlocks.ancientRecoverer, SiteState.restored, SiteState.dismantled) > 0, null));
        objectives.add(new Objective("foundry-archive", () -> sites(VBlocks.archiveTerminal, SiteState.investigated) > 0, null));
        objectives.add(new Objective("foundry-survive", () -> victory,
            () -> Core.bundle.format("vesper.objective.wave", state.wave, state.rules.winWave > 0 ? state.rules.winWave : attackWaves + 1)));

        Events.run(Trigger.update, this::update);
        Events.on(ResetEvent.class, e -> {
            bound = null;
            active = false;
        });
        Events.on(GameOverEvent.class, e -> {
            if(active() && e.winner == state.rules.defaultTeam) win();
        });
        //in the campaign the native capture is the victory (the game has no game over to win)
        Events.on(SectorCaptureEvent.class, e -> {
            if(active() && e.sector == state.rules.sector) win();
        });
    }

    private void win(){
        victory = true;
        completed = objectives.size;
        if(!net.client()) save();
    }

    private Objective count(String key, Block block, int amount){
        return new Objective(key, () -> state.rules.defaultTeam.data().getBuildings(block).size >= amount,
            () -> Math.min(state.rules.defaultTeam.data().getBuildings(block).size, amount) + "/" + amount);
    }

    /** Sites of a kind on the player's team in any of the given states. */
    public static int sites(Block block, SiteState... states){
        int n = 0;
        for(Building b : state.rules.defaultTeam.data().getBuildings(block)){
            if(b instanceof SiteBuild site){
                for(SiteState s : states) if(site.siteState == s){
                    n++;
                    break;
                }
            }
        }
        return n;
    }

    @Override
    public boolean active(){
        return active && bound == state.rules && state.isGame();
    }

    @Override
    public String key(){
        return "vesper.foundry";
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

    @Override
    public boolean released(){
        return released;
    }

    @Override
    public boolean holding(){
        return active() && !released && state.wave >= holdWave && !state.rules.waveTimer;
    }

    private void update(){
        if(!state.isGame()) return;
        if(state.rules != bound) bind();
        if(!active) return;
        if(net.client()){
            //GameOverEvent fires only on the server: read the outcome and the release from the synced state
            if(state.gameOver && state.won){
                victory = true;
                completed = objectives.size;
            }
            if(state.rules.winWave > 0) released = true;
        }
        if(state.gameOver) return;

        while(completed < objectives.size && objectives.get(completed).done.get()){
            completed++;
            if(!net.client()) save();
        }

        if(net.client() || (timer += Time.delta) < 30f) return;
        timer = 0f;

        boolean archive = sites(VBlocks.archiveTerminal, SiteState.investigated) > 0;
        if(!archive){
            //the final waves wait for the archive
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
        active = scenarioId.equals(state.rules.tags.get(FirstAwakening.tagScenario));
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
