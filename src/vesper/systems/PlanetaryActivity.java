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
import mindustry.world.blocks.production.*;
import vesper.content.*;

import static mindustry.Vars.*;

/**
 * Planetary activity: a 0-100 index of how strongly the ancient planet reacts to the player's presence.
 * <p>
 * Working industry raises it slowly and can reach Stirring, never Awakened; ancient technology (see
 * {@link ActivitySource}) and surges from it can push the basin into Awakened, which wakes ancient machines: one
 * awakening response, then patrols while the basin stays Awakened. Stabilizers (see {@link ActivityStabilizer})
 * hold it down with diminishing returns. The level pays off too: ancient reactors give more power and resonance
 * machines work faster (see {@link #resonance}). Every hostile response is announced by a countdown before it
 * arrives.
 * <p>
 * Maps tagged {@link #tagResponse} add the planetary response: resonance pulses above {@link #pulseLevel} (an
 * announced flare at the player's resonance machines that boosts them, slows the ordinary industry next to them,
 * overloads nearby stabilizers and lifts the level for a while) and patrols that grow and come more often with the
 * level, capped by the number of hostile units alive. Without the tag, patrols keep their fixed 0.2 size and count.
 * <p>
 * Only maps whose rules carry the {@link #tagEnabled} tag use it, so vanilla maps and other mods are untouched.
 * The server (or single player) samples it once per second and writes it to the rules tags, which saves and the
 * network world data already carry; clients receive live values through a custom packet and never simulate.
 * All state belongs to the current rules object and is dropped when the world changes.
 */
public class PlanetaryActivity{
    public static final String tagEnabled = "vesper.reactivity", tagState = "vesper.activity", tagGates = "vesper.gates", tagResponse = "vesper.response";
    public static final String packetType = "vesper-activity";
    public static final float stirringLevel = 30f, awakenedLevel = 70f;

    /** Industrial weight of a working building of each block (points at full efficiency). Filled by content. */
    public static final ObjectFloatMap<Block> weights = new ObjectFloatMap<>();
    /** Blocks that feed on resonance: faster with activity and inside pulses, and what pulses aim at. Filled by content. */
    public static final ObjectSet<Block> resonant = new ObjectSet<>();

    public float industryCap = 50f, riseRate = 2f, fallRate = 0.5f, shockDecay = 40f / 180f, hysteresis = 3f;
    public float awakeningWarning = 30f * 60f, patrolWarning = 20f * 60f, patrolCooldown = 150f * 60f;
    public int maxPatrols = 2;
    /** The strongest stabilizer takes this many points off the target; each further one this fraction of the previous. */
    public float stabilizerStrength = 12f, stabilizerFalloff = 0.6f;
    /** Resonance machines run at x1 up to the floor level, rising linearly to the maximum at 100. */
    public float resonanceFloor = 30f, resonanceMax = 2f;
    /** Pulses (maps tagged vesper.response): from this level, announced, landing at the player's resonance machines. */
    public float pulseLevel = 50f, pulseWarning = 20f * 60f, pulseDuration = 20f * 60f, pulseRadius = 10f * tilesize,
        pulseShock = 10f, pulseBoost = 1.5f, pulseSlowdown = 0.5f, pulseCooldownSlow = 150f * 60f, pulseCooldownFast = 75f * 60f;
    /** Patrols on maps tagged vesper.response: more often and larger with the level, never past the unit cap. */
    public float responseCooldownSlow = 120f * 60f, responseCooldownFast = 60f * 60f;
    public int responseUnitCap = 12;

    public enum Stage{
        dormant, stirring, awakened;

        public String localized(){
            return Core.bundle.get("vesper.activity.stage." + name());
        }
    }

    //state of the current world; see read()/write() for what persists
    public float level, shock, industry, ancient, stabilization;
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
    /** Ticks until the announced pulse lands (-1: none), ticks left of the pulse in progress (-1: none), ticks until the next may be announced. */
    public float pulsePending = -1f, pulseActive = -1f, pulseCooldown;
    /** Epicentre of the announced or running pulse (tile coordinates) and the number of pulses so far. */
    public int pulseX, pulseY, pulses;

    private @Nullable Rules bound;
    private boolean active, responses;
    private float sampleTimer;
    private String lastSent = "";
    private final Seq<ActivityStabilizer> stabilizers = new Seq<>();

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
                    //the per-stabilizer shares follow from synced state; working them out here keeps the bars current between block snapshots
                    if(active()) assignContributions();
                }
            });
        }
    }

    /** @return whether the current world uses planetary activity. */
    public boolean active(){
        return active && bound == state.rules && state.isGame();
    }

    /** @return whether the current world has the planetary response (pulses and scaling patrols). */
    public boolean responses(){
        return active() && responses;
    }

    /** Power multiplier for ancient machinery that feeds on the basin's resonance: 0.5x dormant to 1.5x at 100. */
    public float outputMultiplier(){
        return active() ? 0.5f + level / 100f : 1f;
    }

    /** Speed of resonance machines from the level alone: x1 up to the floor, rising linearly to the maximum at 100. */
    public float resonanceMultiplier(){
        return active() ? 1f + Math.max(0f, level - resonanceFloor) / (100f - resonanceFloor) * (resonanceMax - 1f) : 1f;
    }

    /** Speed multiplier of a resonance machine: the level, and the boost of a pulse it stands in. */
    public float resonance(Building build){
        return resonanceMultiplier() * (inPulse(build) ? pulseBoost : 1f);
    }

    /** Whether a pulse is running and reaches this building. */
    public boolean inPulse(Building build){
        return pulseActive > 0f && active() && build.within(pulseX * tilesize, pulseY * tilesize, pulseRadius);
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

    /**
     * Patrol size for the current level (on maps without the planetary response: always 2 crawlers and 1 sentinel):
     * mild just past 70 (2+1), heavy near 100 (7+3), so running the basin flat out costs a real defense.
     */
    public int patrolCrawlers(){
        return responses ? 2 + (int)(Math.max(0f, level - awakenedLevel) / 6f) : 2;
    }

    public int patrolSentinels(){
        return responses ? 1 + (int)(Math.max(0f, level - awakenedLevel) / 15f) : 1;
    }

    /** Points a set of stabilizers takes off the target: strongest first, each further one a fraction of the previous. */
    public float stabilization(FloatSeq operations){
        operations.sort();
        float total = 0f, weight = 1f;
        for(int i = operations.size - 1; i >= 0; i--){
            total += stabilizerStrength * operations.get(i) * weight;
            if(operations.get(i) > 0f) weight *= stabilizerFalloff;
        }
        return total;
    }

    private void update(){
        if(!state.isGame()){
            if(bound != null) unbind();
            return;
        }
        if(state.rules != bound) bind();
        //a captured campaign sector is secured: its waves are over and the basin stops answering (no more patrols or pulses)
        if(!active || net.client() || state.isPaused() || state.gameOver || (state.isCampaign() && !state.rules.waves)) return;

        if(pending >= 0f){
            pending -= Time.delta;
            if(pending <= 0f){
                spawnIncursion();
            }
        }
        cooldown = Math.max(0f, cooldown - Time.delta);

        if(responses){
            pulseCooldown = Math.max(0f, pulseCooldown - Time.delta);
            if(pulsePending >= 0f && (pulsePending -= Time.delta) <= 0f){
                landPulse();
            }
            if(pulseActive >= 0f && (pulseActive -= Time.delta) <= 0f){
                pulseActive = -1f;
                publish();
            }
        }

        sampleTimer += Time.delta;
        if(sampleTimer >= 60f){
            sample(sampleTimer / 60f);
            sampleTimer = 0f;
        }
    }

    private void bind(){
        bound = state.rules;
        active = state.rules.tags.getBool(tagEnabled);
        responses = state.rules.tags.getBool(tagResponse);
        clear();
        if(active) read(state.rules.tags.get(tagState, ""));
        lastSent = "";
    }

    private void unbind(){
        bound = null;
        active = responses = false;
        clear();
    }

    private void clear(){
        level = shock = industry = ancient = stabilization = cooldown = sampleTimer = pulseCooldown = 0f;
        awakened = pendingAwakening = false;
        patrols = pendingCrawlers = pendingSentinels = pulseX = pulseY = pulses = 0;
        pending = pulsePending = pulseActive = -1f;
        stage = Stage.dormant;
    }

    /** One simulation step covering dt seconds; server or single player only. */
    private void sample(float dt){
        industry = 0f;
        ancient = 0f;
        FloatSeq operations = new FloatSeq();
        for(Building b : state.rules.defaultTeam.data().buildings){
            float w = weights.get(b.block, 0f);
            if(w > 0f) industry += w * working(b);
            if(b instanceof ActivitySource s) ancient += s.activity();
            if(b instanceof ActivityStabilizer s) operations.add(s.stabilizerOperation());
        }
        industry = Math.min(industry, industryCap);
        stabilization = stabilization(operations);
        assignContributions();

        shock = Math.max(0f, shock - shockDecay * dt);
        float target = Mathf.clamp(industry + ancient + shock - stabilization, 0f, 100f);
        level = level < target ? Math.min(target, level + riseRate * dt) : Math.max(target, level - fallRate * dt);

        Stage next = level >= awakenedLevel || (stage == Stage.awakened && level >= awakenedLevel - hysteresis) ? Stage.awakened :
            level >= stirringLevel || (stage != Stage.dormant && level >= stirringLevel - hysteresis) ? Stage.stirring : Stage.dormant;
        stage = next;

        if(stage == Stage.awakened && !awakened){
            //the first time the basin wakes: one larger response from the ancient gates
            awakened = true;
            schedule(true, awakeningCrawlers(), awakeningSentinels(), awakeningWarning);
            cooldown = patrolCooldown;
        }else if(stage == Stage.awakened && pending < 0f && cooldown <= 0f && (responses ? state.enemies < responseUnitCap : patrols < maxPatrols)){
            patrols++;
            schedule(false, patrolCrawlers(), patrolSentinels(), patrolWarning);
        }

        //a pulse is announced at the player's resonance machines while the level stays high enough
        if(responses && pulsePending < 0f && pulseActive < 0f && pulseCooldown <= 0f && level >= pulseLevel){
            Building epicentre = pulseTarget();
            if(epicentre != null){
                pulseX = epicentre.tileX();
                pulseY = epicentre.tileY();
                pulsePending = pulseWarning;
            }
        }

        publish();
    }

    /** Tells each stabilizer what it takes off after diminishing returns (strongest first, as in {@link #stabilization}). */
    private void assignContributions(){
        stabilizers.clear();
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(b instanceof ActivityStabilizer s) stabilizers.add(s);
        }
        stabilizers.sort(s -> -s.stabilizerOperation());
        float weight = 1f;
        for(ActivityStabilizer s : stabilizers){
            float op = s.stabilizerOperation();
            s.stabilizerContribution(stabilizerStrength * op * weight);
            if(op > 0f) weight *= stabilizerFalloff;
        }
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
        //patrols come sooner the higher the level on maps with the planetary response
        cooldown = responses && !pendingAwakening ? Mathf.lerp(responseCooldownSlow, responseCooldownFast,
            Mathf.clamp((level - awakenedLevel) / (100f - awakenedLevel))) : patrolCooldown;
        pendingCrawlers = pendingSentinels = 0;
        publish();
    }

    /** The working resonance machine closest to an ancient gate (the fault), lowest position first on ties. */
    @Nullable Building pulseTarget(){
        Seq<Tile> gates = gates();
        Building best = null;
        float bestDst = Float.MAX_VALUE;
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(!resonant.contains(b.block) || b.efficiency <= 0f) continue;
            float dst = Float.MAX_VALUE;
            for(Tile g : gates) dst = Math.min(dst, b.dst2(g.worldx(), g.worldy()));
            if(dst < bestDst || (dst == bestDst && best != null && b.pos() < best.pos())){
                best = b;
                bestDst = dst;
            }
        }
        return best;
    }

    private void landPulse(){
        pulsePending = -1f;
        pulseActive = pulseDuration;
        pulses++;
        //the flare lifts the target; the level climbs towards it at the usual rate (stabilizers can absorb it)
        shock += pulseShock;
        pulseCooldown = Mathf.lerp(pulseCooldownSlow, pulseCooldownFast, Mathf.clamp((level - pulseLevel) / (100f - pulseLevel)));
        //interference: the ordinary industry next to the resonance machines slows down while the pulse lasts (saved with each building)
        float wx = pulseX * tilesize, wy = pulseY * tilesize;
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(!resonant.contains(b.block) && (b.block instanceof GenericCrafter || b.block instanceof Drill) && b.within(wx, wy, pulseRadius)){
                b.applySlowdown(pulseSlowdown, pulseDuration);
            }
        }
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
        //efficiency includes the resonance multiplier of resonance machines; a machine counts once at most
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

    /** Version 2 (0.3) appends stabilization and the pulse to the 0.2 fields; version 1 still reads. */
    String write(){
        return Strings.format("2;@;@;@;@;@;@;@;@;@;@;@;@;@;@;@;@;@;@",
            Strings.fixed(level, 2), Strings.fixed(shock, 2), Strings.fixed(industry, 2), Strings.fixed(ancient, 2),
            awakened ? 1 : 0, patrols, (int)cooldown, (int)pending, pendingCrawlers, pendingSentinels, (pendingAwakening ? 1 : 0) + "," + stage.ordinal(),
            Strings.fixed(stabilization, 2), (int)pulsePending, (int)pulseActive, (int)pulseCooldown, pulseX, pulseY, pulses);
    }

    void read(String data){
        String[] v = data.split(";");
        boolean v1 = v.length >= 12 && v[0].equals("1"), v2 = v.length >= 19 && v[0].equals("2");
        if(!v1 && !v2) return;
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
        if(v2){
            stabilization = Strings.parseFloat(v[12], 0f);
            pulsePending = Strings.parseInt(v[13], -1);
            pulseActive = Strings.parseInt(v[14], -1);
            pulseCooldown = Strings.parseInt(v[15], 0);
            pulseX = Strings.parseInt(v[16], 0);
            pulseY = Strings.parseInt(v[17], 0);
            pulses = Strings.parseInt(v[18], 0);
        }
    }
}
