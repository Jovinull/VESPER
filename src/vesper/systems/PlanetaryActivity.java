package vesper.systems;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.power.PowerGenerator.*;
import vesper.content.*;

import static mindustry.Vars.*;

/**
 * Planetary activity: a 0-100 index of how strongly the ancient planet reacts to the player's presence.
 * <p>
 * Working industry raises it slowly and can reach Stirring, never Awakened; ancient technology (see
 * {@link ActivitySource}) and surges from it can push the basin into Awakened, which wakes ancient machines: one
 * awakening response, then a few patrols while the basin stays Awakened. Every hostile response is announced
 * by a countdown before it arrives.
 * <p>
 * Only maps whose rules carry the {@link #tagEnabled} tag use it, so vanilla maps and other mods are untouched.
 * The server (or single player) samples it once per second and writes it to the rules tags, which saves and the
 * network world data already carry; clients receive live values through a custom packet and never simulate.
 * All state belongs to the current rules object and is dropped when the world changes.
 */
public class PlanetaryActivity{
    public static final String tagEnabled = "vesper.reactivity", tagState = "vesper.activity", tagGates = "vesper.gates";
    public static final String packetType = "vesper-activity";
    public static final float stirringLevel = 30f, awakenedLevel = 70f;

    /** Industrial weight of a working building of each block (points at full efficiency). Filled by content. */
    public static final ObjectFloatMap<Block> weights = new ObjectFloatMap<>();

    public float industryCap = 50f, riseRate = 2f, fallRate = 0.5f, shockDecay = 40f / 180f, hysteresis = 3f;
    public float awakeningWarning = 30f * 60f, patrolWarning = 20f * 60f, patrolCooldown = 150f * 60f;
    public int maxPatrols = 2;

    public enum Stage{
        dormant, stirring, awakened;

        public String localized(){
            return Core.bundle.get("vesper.activity.stage." + name());
        }
    }

    //state of the current world; see read()/write() for what persists
    public float level, shock, industry, ancient;
    public boolean awakened;
    public int patrols;
    /** Ticks until a patrol may be scheduled. */
    public float cooldown;
    /** Ticks until the announced incursion arrives, or -1 when nothing is coming. */
    public float pending = -1f;
    public int pendingCrawlers, pendingSentinels;
    /** Whether the pending incursion is the awakening response (otherwise a patrol). */
    public boolean pendingAwakening;
    public Stage stage = Stage.dormant;

    private @Nullable Rules bound;
    private boolean active;
    private float sampleTimer;
    private String lastSent = "";

    public PlanetaryActivity(){
        Events.run(Trigger.update, this::update);
        Events.on(ResetEvent.class, e -> unbind());
        //the per-second sample may be up to a second old; saves get the exact state
        Events.on(SaveWriteEvent.class, e -> {
            if(active() && !net.client()) state.rules.tags.put(tagState, write());
        });

        if(netClient != null){
            netClient.addPacketHandler(packetType, data -> {
                if(state.isGame()){
                    state.rules.tags.put(tagState, data);
                    read(data);
                }
            });
        }
    }

    /** @return whether the current world uses planetary activity. */
    public boolean active(){
        return active && bound == state.rules && state.isGame();
    }

    /** Power multiplier for ancient machinery that feeds on the basin's resonance: 0.5x dormant to 1.5x at 100. */
    public float outputMultiplier(){
        return active() ? 0.5f + level / 100f : 1f;
    }

    /** Activity as a 0-1 fraction, 0 outside Vesper scenarios. */
    public float fraction(){
        return active() ? level / 100f : 0f;
    }

    /** A sudden disturbance (an ancient machine waking): raises the level at once and decays over minutes. */
    public void surge(float amount){
        if(!active() || net.client()) return;
        shock += amount;
        level = Math.min(100f, level + amount);
        sample(0f);
    }

    /** Size of the awakening response for the current industry, so the player can predict it. */
    public int awakeningCrawlers(){
        return 2 + Math.round(industry / 12f);
    }

    public int awakeningSentinels(){
        return 2;
    }

    private void update(){
        if(!state.isGame()){
            if(bound != null) unbind();
            return;
        }
        if(state.rules != bound) bind();
        if(!active || net.client() || state.isPaused() || state.gameOver) return;

        if(pending >= 0f){
            pending -= Time.delta;
            if(pending <= 0f){
                spawnIncursion();
            }
        }
        cooldown = Math.max(0f, cooldown - Time.delta);

        sampleTimer += Time.delta;
        if(sampleTimer >= 60f){
            sample(sampleTimer / 60f);
            sampleTimer = 0f;
        }
    }

    private void bind(){
        bound = state.rules;
        active = state.rules.tags.getBool(tagEnabled);
        clear();
        if(active) read(state.rules.tags.get(tagState, ""));
        lastSent = "";
    }

    private void unbind(){
        bound = null;
        active = false;
        clear();
    }

    private void clear(){
        level = shock = industry = ancient = cooldown = sampleTimer = 0f;
        awakened = pendingAwakening = false;
        patrols = pendingCrawlers = pendingSentinels = 0;
        pending = -1f;
        stage = Stage.dormant;
    }

    /** One simulation step covering dt seconds; server or single player only. */
    private void sample(float dt){
        industry = 0f;
        ancient = 0f;
        for(Building b : state.rules.defaultTeam.data().buildings){
            float w = weights.get(b.block, 0f);
            if(w > 0f) industry += w * working(b);
            if(b instanceof ActivitySource s) ancient += s.activity();
        }
        industry = Math.min(industry, industryCap);

        shock = Math.max(0f, shock - shockDecay * dt);
        float target = Mathf.clamp(industry + ancient + shock, 0f, 100f);
        level = level < target ? Math.min(target, level + riseRate * dt) : Math.max(target, level - fallRate * dt);

        Stage next = level >= awakenedLevel || (stage == Stage.awakened && level >= awakenedLevel - hysteresis) ? Stage.awakened :
            level >= stirringLevel || (stage != Stage.dormant && level >= stirringLevel - hysteresis) ? Stage.stirring : Stage.dormant;
        stage = next;

        if(stage == Stage.awakened && !awakened){
            //the first time the basin wakes: one larger response from the ancient gates
            awakened = true;
            schedule(true, awakeningCrawlers(), awakeningSentinels(), awakeningWarning);
            cooldown = patrolCooldown;
        }else if(stage == Stage.awakened && pending < 0f && cooldown <= 0f && patrols < maxPatrols){
            patrols++;
            schedule(false, 2, 1, patrolWarning);
        }

        publish();
    }

    private void schedule(boolean awakening, int crawlers, int sentinels, float warning){
        pendingAwakening = awakening;
        pendingCrawlers = crawlers;
        pendingSentinels = sentinels;
        pending = warning;
    }

    private void spawnIncursion(){
        pending = -1f;
        Seq<Tile> gates = gates();
        Team team = state.rules.waveTeam;
        int index = 0;
        for(int i = 0; i < pendingSentinels + pendingCrawlers; i++){
            UnitType type = i < pendingSentinels ? VUnits.ancientSentinel : VUnits.rustCrawler;
            Tile gate = gates.get(index++ % gates.size);
            Tmp.v1.rnd(tilesize * 1.5f);
            type.spawn(team, gate.worldx() + Tmp.v1.x, gate.worldy() + Tmp.v1.y,
                Angles.angle(gate.worldx(), gate.worldy(), world.unitWidth() / 2f, world.unitHeight() / 2f), spawner::spawnEffect);
        }
        pendingCrawlers = pendingSentinels = 0;
        cooldown = patrolCooldown;
        publish();
    }

    /** Gates from the map tag (tile coordinates "x,y;x,y"), falling back to the wave spawns. */
    public Seq<Tile> gates(){
        Seq<Tile> out = new Seq<>();
        for(String pair : state.rules.tags.get(tagGates, "").split(";")){
            String[] xy = pair.split(",");
            if(xy.length != 2) continue;
            Tile t = world.tile(Strings.parseInt(xy[0].trim(), -1), Strings.parseInt(xy[1].trim(), -1));
            if(t != null) out.add(t);
        }
        if(out.isEmpty()) out.addAll(spawner.getSpawns());
        if(out.isEmpty()) out.add(world.tile(world.width() / 2, world.height() - 2));
        return out;
    }

    static float working(Building b){
        if(!b.enabled) return 0f;
        if(b instanceof GeneratorBuild g) return Mathf.clamp(g.productionEfficiency);
        return b.shouldConsume() ? Mathf.clamp(b.efficiency) : 0f;
    }

    /** Stores the state in the rules tags (saves) and sends it to clients when it changed. */
    private void publish(){
        String data = write();
        state.rules.tags.put(tagState, data);
        if(net.server() && !data.equals(lastSent)){
            Call.clientPacketReliable(packetType, data);
        }
        lastSent = data;
    }

    String write(){
        return Strings.format("1;@;@;@;@;@;@;@;@;@;@;@",
            Strings.fixed(level, 2), Strings.fixed(shock, 2), Strings.fixed(industry, 2), Strings.fixed(ancient, 2),
            awakened ? 1 : 0, patrols, (int)cooldown, (int)pending, pendingCrawlers, pendingSentinels, (pendingAwakening ? 1 : 0) + "," + stage.ordinal());
    }

    void read(String data){
        String[] v = data.split(";");
        if(v.length < 12 || !v[0].equals("1")) return;
        level = Strings.parseFloat(v[1], 0f);
        shock = Strings.parseFloat(v[2], 0f);
        industry = Strings.parseFloat(v[3], 0f);
        ancient = Strings.parseFloat(v[4], 0f);
        awakened = v[5].equals("1");
        patrols = Strings.parseInt(v[6], 0);
        cooldown = Strings.parseInt(v[7], 0);
        pending = Strings.parseInt(v[8], -1);
        pendingCrawlers = Strings.parseInt(v[9], 0);
        pendingSentinels = Strings.parseInt(v[10], 0);
        String[] last = v[11].split(",");
        pendingAwakening = last[0].equals("1");
        stage = Stage.values()[Mathf.clamp(last.length > 1 ? Strings.parseInt(last[1], 0) : 0, 0, Stage.values().length - 1)];
    }
}
