package vesper.harness;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.world.blocks.defense.turrets.Turret.*;

import static mindustry.Vars.*;

/**
 * Combat log for playthrough suites, information only (it records no check): the core's health over time, who
 * hits it and from where, the enemies close to it, the turrets' ammunition and targets, and every building lost. It
 * explains a lost core instead of only reporting it.
 */
public class Combat{
    final VesperHarness h;
    public boolean active;
    float timer, time;
    /** Bullet hits on the core by attacker type, and the tiles they were fired from. */
    final ObjectIntMap<String> coreHits = new ObjectIntMap<>();
    final ObjectSet<String> hitFrom = new ObjectSet<>();
    final Seq<String> lost = new Seq<>();
    float minCore = Float.MAX_VALUE;

    public Combat(VesperHarness h){
        this.h = h;
        Events.on(BuildDamageEvent.class, e -> {
            if(!active || e.build == null || e.build.team != state.rules.defaultTeam || !(e.build instanceof mindustry.world.blocks.storage.CoreBlock.CoreBuild)) return;
            String who = attacker(e.source);
            coreHits.increment(who);
            //where the shooter stood (the bullet itself lands on the core)
            if(e.source != null && e.source.owner instanceof Posc shooter && hitFrom.size < 40){
                hitFrom.add(who + "@" + (int)(shooter.x() / tilesize) + "," + (int)(shooter.y() / tilesize));
            }
        });
        Events.on(BlockDestroyEvent.class, e -> {
            if(active && e.tile.team() == state.rules.defaultTeam) lost.add(Strings.fixed(time / 3600f, 1) + "min " + e.tile.block().name + "@" + e.tile.x + "," + e.tile.y);
        });
        Events.run(Trigger.update, this::update);
    }

    static String attacker(@Nullable Bullet b){
        if(b == null) return "?";
        return b.owner instanceof Unit u ? u.type.name : b.owner instanceof Building build ? build.block.name : b.type instanceof BulletType ? "bullet" : "?";
    }

    public void start(String label){
        active = true;
        timer = time = 0f;
        coreHits.clear();
        hitFrom.clear();
        lost.clear();
        minCore = Float.MAX_VALUE;
        h.line("combat: tracking " + label);
    }

    void update(){
        if(!active || !state.isPlaying()) return;
        time += Time.delta;
        var core = state.rules.defaultTeam.core();
        if(core != null) minCore = Math.min(minCore, core.health);
        if((timer += Time.delta) < 10f * 60f) return;
        timer = 0f;
        h.line("combat: " + snapshot());
    }

    /** Core health, enemies by distance to the core, turrets with ammunition and with a target. */
    public String snapshot(){
        var core = state.rules.defaultTeam.core();
        StringBuilder out = new StringBuilder();
        out.append("t+").append(Strings.fixed(time / 3600f, 1)).append("min wave ").append(state.wave).append(" core ")
            .append(core == null ? "lost" : (int)core.health + "/" + (int)core.maxHealth);
        int near = 0;
        ObjectIntMap<String> types = new ObjectIntMap<>();
        StringBuilder close = new StringBuilder();
        for(Unit u : Groups.unit){
            if(u.team != state.rules.waveTeam) continue;
            types.increment(u.type.name);
            if(core != null && u.within(core, 30f * tilesize)){
                near++;
                if(close.length() < 240) close.append(' ').append(u.type.name.replace("vesper-", "")).append('@').append(u.tileX()).append(',').append(u.tileY())
                    .append(':').append((int)u.health);
            }
        }
        out.append(" | enemies ").append(state.enemies).append(" ").append(types).append(", within 30 tiles of the core ").append(near).append(close);
        int turrets = 0, armed = 0, aiming = 0, ammo = 0;
        for(Building b : state.rules.defaultTeam.data().buildings){
            if(b instanceof TurretBuild t){
                turrets++;
                if(t.hasAmmo()) armed++;
                if(t.isShooting()) aiming++;
                if(b instanceof mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild it) ammo += it.totalAmmo;
            }
        }
        out.append(" | turrets ").append(turrets).append(" armed ").append(armed).append(" shooting ").append(aiming).append(" ammo ").append(ammo);
        return out.toString();
    }

    /** Summary lines: lowest core health, hits on the core by attacker, where they came from, what was lost. */
    public void report(String label){
        h.line("combat: " + label + " - lowest core health " + (minCore == Float.MAX_VALUE ? "-" : (int)minCore) + "; core hit by " + coreHits
            + (hitFrom.isEmpty() ? "" : " from " + hitFrom));
        h.line("combat: " + label + " - buildings lost while tracked: " + (lost.isEmpty() ? "none" : lost.toString(", ")));
        active = false;
    }
}
