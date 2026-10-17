package vesper.harness.suites;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.net.*;
import vesper.*;
import vesper.harness.*;
import vesper.mission.*;
import vesper.world.DerelictReactor.*;
import vesper.world.Stabilizer.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Server side of the local multiplayer test (run on the official dedicated server, two clients join with the
 * mp-client suite). The server drives the script, asks both clients for their view of the game through a custom
 * packet and compares it with its own authoritative state. Clients perform the player actions (carrying items
 * into the reactor, igniting it, switching a stabilizer) through the normal network calls. First Awakening covers
 * the 0.2 mission; Resonance Fault covers the planetary response (stabilizers, pulses) and its own victory.
 */
public class MultiplayerHost implements VesperHarness.Suite{
    public static final String packet = "vesper-mp";
    public static final int port = Strings.parseInt(System.getProperty("vesper.mp.port", "6577"), 6577);

    /** Latest answer of each client to the current query: name -> key -> value. */
    final ObjectMap<String, StringMap> answers = new ObjectMap<>();
    String query = "";
    int queries;
    Map map, fault;
    String savedSnapshot = "";
    /** Fixture: while >= 0 the server holds the activity surge here (to reach the pulse level quickly). */
    float holdShock = -1f;
    boolean holding;
    /** Bottom-left tiles of the test stabilizer and extractors near the Resonance Fault core (sheltered crystal vein). */
    public static final int stabilizerX = 24, stabilizerY = 22;
    public static final int[][] faultExtractors = {{31, 25}, {33, 25}, {33, 27}}, faultSinks = {{30, 25}, {35, 25}, {35, 27}};
    public static final int powerX = 29, powerY = 24;

    @Override
    public void define(VesperHarness h, Script s){
        if(!holding){
            holding = true;
            Events.run(EventType.Trigger.update, () -> {
                if(holdShock >= 0f && Vesper.activity.active()) Vesper.activity.shock = holdShock;
            });
        }
        s.run(() -> {
            //the harness keeps the world after game over (no map rotation), so the victory and defeat checks can follow
            netServer.addPacketHandler(packet, (player, data) -> {
                String[] parts = data.split("\\|", 3);
                if(parts.length == 3 && parts[0].equals(query)) answers.put(parts[1], parse(parts[2]));
            });
            map = Worlds.bundled(ContentSuite.basinName);
            Worlds.play(map, Gamemode.survival);
            Core.settings.put("port", port);
            netServer.openServer();
            h.line("mp: hosting " + map.name() + " on port " + port);
        });
        s.until("mp: two clients joined", () -> Groups.player.size() >= 2 && Groups.player.count(p -> p.unit() != null && !p.dead()) >= 2, 240f,
            () -> Groups.player.size() + " players");
        s.wait(3f);

        ask(h, s, "join");
        expect(h, s, "mp: each client loaded the map as authored", a -> a.get("map").equals(map.name()) && a.get("size").equals("150x100")
            && a.get("foreign").equals("0") && a.get("activity").equals("true") && a.get("mission").equals("true"));

        //planetary activity is simulated on the server only; clients must receive it
        s.run(() -> Vesper.activity.surge(20f));
        s.wait(3f);
        ask(h, s, "activity");
        expect(h, s, "mp: activity reaches both clients", a -> Math.abs(Strings.parseFloat(a.get("level"), -99f) - Vesper.activity.level) <= 3f
            && a.get("stage").equals(Vesper.activity.stage.name()));

        //a client carries the restoration materials with its own unit through the normal deposit requests
        s.run(() -> {
            state.rules.defaultTeam.core().items.add(structuralAlloy, 300);
            state.rules.defaultTeam.core().items.add(recoveredCircuit, 100);
            send(first(), "deliver");
        });
        s.until("mp: a client restores the reactor; its deposits count on the server", () -> reactor().primed(), 300f,
            () -> "server sees " + reactor().delivered[0] + " alloy / " + reactor().delivered[1] + " circuits");
        s.wait(2f);
        ask(h, s, "restored");
        expect(h, s, "mp: both clients see the restoration", a -> a.get("delivered").equals("300+100"));

        //the hold of the fifth wave travels as rule changes
        s.run(() -> state.wave = 5);
        s.wait(2f);
        ask(h, s, "hold");
        expect(h, s, "mp: both clients see the fifth wave held", a -> a.get("timer").equals("false") && a.get("holding").equals("true"));

        //the other client asks for ignition; only the server may run it
        s.run(() -> send(second(), "ignite"));
        s.until("mp: ignition requested by a client runs on the server", () -> reactor().ignited, 15f, null);
        s.until("mp: the server schedules the awakening response", () -> Vesper.activity.pending > 0f, 10f, null);
        s.wait(2f);
        ask(h, s, "awakening");
        expect(h, s, "mp: both clients get the awakening warning and the released final waves",
            a -> a.get("awakened").equals("true") && a.get("pending").equals("true") && a.get("incoming").equals(incoming())
                && a.get("warning").equals("true") && a.get("winWave").equals("7") && a.get("released").equals("true"));
        s.run(() -> {
            for(Player p : Groups.player) Call.clientPacketReliable(p.con, packet, "shot:mp-awakening");
        });

        //the response is spawned once, by the server; clients only mirror it
        s.until("mp: the awakening response spawns on the server", () -> Vesper.activity.pending < 0f, 60f, null);
        s.wait(2f);
        ask(h, s, "spawned");
        expect(h, s, "mp: clients see exactly the server's enemies (no duplicates)", a -> a.get("enemies").equals(String.valueOf(enemies())));

        s.run(() -> logic.runWave());
        s.wait(3f);
        ask(h, s, "wave");
        expect(h, s, "mp: wave counter and enemies stay in sync", a -> a.get("wave").equals(String.valueOf(state.wave))
            && a.get("enemies").equals(String.valueOf(enemies())));

        //save and load on the server while both clients stay connected (the server re-sends the world)
        s.run(() -> {
            Fi file = h.out.child("mp-midgame.msav");
            SaveIO.save(file);
            savedSnapshot = snapshot();
            WorldReloader reloader = new WorldReloader();
            reloader.begin();
            SaveIO.load(file);
            state.rules.sector = null;
            state.set(State.playing);
            reloader.end();
            h.line("mp: saved and reloaded on the server: " + savedSnapshot);
        });
        s.until("mp: both clients are back in the reloaded game", () -> Groups.player.count(p -> p.unit() != null && !p.dead()) >= 2, 120f, null);
        s.wait(3f);
        s.check("mp: server state survives save and load", () -> snapshot().equals(savedSnapshot), () -> snapshot());
        ask(h, s, "reload");
        expect(h, s, "mp: clients see the reloaded state", a -> a.get("ignited").equals("true") && a.get("awakened").equals("true")
            && a.get("wave").equals(String.valueOf(state.wave)) && a.get("delivered").equals("300+100") && a.get("winWave").equals("7"));

        //victory: the last wave cleared with victory armed
        s.run(() -> {
            Groups.unit.each(u -> u.team == state.rules.waveTeam, Unit::kill);
            state.wave = state.rules.winWave;
        });
        s.until("mp: the server declares victory", () -> h.lastWinner == state.rules.defaultTeam, 30f, null);
        s.wait(2f);
        ask(h, s, "victory");
        expect(h, s, "mp: both clients see the victory and the completed mission",
            a -> a.get("gameover").equals("true") && a.get("won").equals("true") && a.get("victory").equals("true") && a.get("objective").equals("done"));
        s.run(() -> {
            for(Player p : Groups.player) Call.clientPacketReliable(p.con, packet, "shot:mp-victory");
        });
        s.wait(2f);

        //defeat: a fresh game, then the core falls
        s.run(() -> {
            WorldReloader reloader = new WorldReloader();
            reloader.begin();
            Rules rules = map.applyRules(Gamemode.survival);
            world.loadMap(map, rules);
            state.rules = rules;
            logic.play();
            reloader.end();
        });
        s.until("mp: both clients join the new game", () -> Groups.player.count(p -> p.unit() != null && !p.dead()) >= 2, 120f, null);
        s.wait(3f);
        s.run(() -> state.rules.defaultTeam.core().kill());
        s.until("mp: the server declares defeat", () -> h.lastWinner == state.rules.waveTeam, 30f, null);
        s.wait(2f);
        ask(h, s, "defeat");
        expect(h, s, "mp: both clients see the defeat", a -> a.get("gameover").equals("true") && a.get("won").equals("false"));

        // --- 0.3: the planetary response on Resonance Fault (test setup: a powered stabilizer and three extractors near the core)
        s.run(() -> {
            fault = Worlds.bundled(ContentSuite.faultName);
            WorldReloader reloader = new WorldReloader();
            reloader.begin();
            Rules rules = fault.applyRules(Gamemode.survival);
            world.loadMap(fault, rules);
            state.rules = rules;
            logic.play();
            reloader.end();
        });
        s.until("mp: both clients join Resonance Fault", () -> Groups.player.count(p -> p.unit() != null && !p.dead()) >= 2, 120f, null);
        s.wait(2f);
        s.run(() -> {
            Team team = state.rules.defaultTeam;
            world.tile(powerX, powerY).setNet(Blocks.powerSource, team, 0);
            world.tile(stabilizerX, stabilizerY).setNet(stabilizerPylon, team, 0);
            for(int[] e : faultExtractors) world.tile(e[0], e[1]).setNet(crystalExtractor, team, 0);
            //somewhere for the crystal to go, or a full extractor stops working
            for(int[] v : faultSinks) world.tile(v[0], v[1]).setNet(Blocks.itemVoid, team, 0);
        });
        s.wait(1f);
        s.run(() -> {
            Building source = world.build(powerX, powerY);
            source.configure(world.build(stabilizerX, stabilizerY).pos());
            for(int[] e : faultExtractors) source.configure(world.build(e[0], e[1]).pos());
        });
        s.wait(3f);
        ask(h, s, "fault");
        expect(h, s, "mp: clients run Resonance Fault with the planetary response", a -> a.get("map").equals(fault.name()) && a.get("fault").equals("true")
            && a.get("responses").equals("true") && a.get("stab").startsWith("on"));
        //a client switches the stabilizer off: only the server decides, everyone sees it
        s.run(() -> send(first(), "stabilizer-off"));
        s.until("mp: a client switches a stabilizer off; the server applies it", () -> !stabilizer().online, 15f, null);
        s.wait(2f);
        ask(h, s, "stab-off");
        expect(h, s, "mp: both clients see it off and the stabilization gone", a -> a.get("stab").equals("off 0.0") && a.get("stabilization").equals("0.0"));
        s.run(() -> send(second(), "stabilizer-on"));
        s.until("mp: the other client switches it back on", () -> stabilizer().online && Vesper.activity.stabilization > 11.9f, 15f, null);
        s.wait(2f);
        ask(h, s, "stab-on");
        expect(h, s, "mp: both clients see the stabilization", a -> a.get("stab").equals("on 12.0") && a.get("stabilization").equals("12.0"));
        //a pulse: announced and landed on the server, mirrored by the clients
        s.run(() -> {
            holdShock = 60f;
            Vesper.activity.pulseCooldown = 0f;
        });
        s.until("mp: the server announces a pulse at the extractors", () -> Vesper.activity.pulsePending > 0f, 60f, () -> "level " + (int)Vesper.activity.level);
        s.wait(1f);
        ask(h, s, "pulse-pending");
        expect(h, s, "mp: both clients see the announced pulse and where it lands", a -> a.get("pulse").startsWith("pending@" + Vesper.activity.pulseX + "," + Vesper.activity.pulseY));
        s.until("mp: the pulse lands on the server", () -> Vesper.activity.pulseActive > 0f, 25f, null);
        s.wait(1f);
        ask(h, s, "pulse-active");
        expect(h, s, "mp: both clients see the pulse and the boost of the extractor inside", a -> a.get("pulse").startsWith("active@")
            && Math.abs(Strings.parseFloat(a.get("extractor"), 0f) - Vesper.activity.resonance(world.build(faultExtractors[0][0], faultExtractors[0][1]))) < 0.15f);
        //save and load in the middle of the pulse
        s.run(() -> {
            Fi file = h.out.child("mp-fault.msav");
            SaveIO.save(file);
            savedSnapshot = faultSnapshot();
            WorldReloader reloader = new WorldReloader();
            reloader.begin();
            SaveIO.load(file);
            state.rules.sector = null;
            state.set(State.playing);
            reloader.end();
            h.line("mp: Resonance Fault saved and reloaded on the server: " + savedSnapshot);
        });
        s.until("mp: both clients are back in Resonance Fault", () -> Groups.player.count(p -> p.unit() != null && !p.dead()) >= 2, 120f, null);
        s.wait(2f);
        s.check("mp: pulse and stabilizer survive save and load on the server", () -> faultSnapshot().equals(savedSnapshot), this::faultSnapshot);
        ask(h, s, "fault-reload");
        expect(h, s, "mp: clients see the reloaded pulse and stabilizer", a -> a.get("pulse").startsWith("active@") && a.get("stab").startsWith("on"));
        //victory: the core holds the goal (test setup adds the crystal)
        s.run(() -> {
            holdShock = -1f;
            state.rules.defaultTeam.core().items.add(resonanceCrystal, ResonanceFault.goal);
        });
        s.until("mp: the server declares Resonance Fault won", () -> h.lastWinner == state.rules.defaultTeam, 30f, () -> "objectives " + Vesper.fault.completed);
        s.wait(2f);
        ask(h, s, "fault-victory");
        expect(h, s, "mp: both clients see the Resonance Fault victory", a -> a.get("gameover").equals("true") && a.get("won").equals("true")
            && a.get("faultVictory").equals("true") && a.get("faultObjective").equals("done"));
        s.run(() -> {
            for(Player p : Groups.player) Call.clientPacketReliable(p.con, packet, "shot:mp-fault-victory");
        });
        s.wait(2f);

        s.run(() -> {
            for(Player p : Groups.player) Call.clientPacketReliable(p.con, packet, "bye");
        });
        s.wait(3f);
    }

    /** Sends a query to every client and waits until both answered (or 15 s pass). */
    void ask(VesperHarness h, Script s, String id){
        s.run(() -> {
            query = id + "-" + (++queries);
            answers.clear();
            Call.clientPacketReliable(packet, "query:" + query);
        });
        s.waitFor(() -> answers.size >= 2, 15f);
        s.run(() -> {
            for(var e : answers) h.line("mp: " + e.key + " answered " + id + ": " + e.value);
        });
    }

    /** Records one check per client against its latest answer. */
    void expect(VesperHarness h, Script s, String check, Boolf<StringMap> cond){
        s.run(() -> {
            if(answers.size < 2) h.fail(check, "only " + answers.size + " client(s) answered");
            for(var e : answers){
                boolean ok;
                try{
                    ok = cond.get(e.value);
                }catch(Throwable t){
                    ok = false;
                }
                h.check(check + " [" + e.key + "]", ok, ok ? "" : "client: " + e.value);
            }
        });
    }

    interface Boolf<T>{
        boolean get(T t);
    }

    static StringMap parse(String data){
        StringMap out = new StringMap();
        for(String pair : data.split(";")){
            int i = pair.indexOf('=');
            if(i > 0) out.put(pair.substring(0, i), pair.substring(i + 1));
        }
        return out;
    }

    static void send(Player p, String message){
        if(p != null) Call.clientPacketReliable(p.con, packet, message);
    }

    static Player first(){
        return Groups.player.copy(new Seq<>()).sort(Structs.comparing(p -> p.name)).first();
    }

    static Player second(){
        return Groups.player.copy(new Seq<>()).sort(Structs.comparing(p -> p.name)).peek();
    }

    static ReactorBuild reactor(){
        return Vesper.mission.reactor();
    }

    static int enemies(){
        return Groups.unit.count(u -> u.team == state.rules.waveTeam);
    }

    static String incoming(){
        return (Vesper.activity.pendingSentinels + Vesper.activity.pendingCrawlers) + "";
    }

    static StabilizerBuild stabilizer(){
        return world.build(stabilizerX, stabilizerY) instanceof StabilizerBuild b ? b : null;
    }

    String faultSnapshot(){
        var a = Vesper.activity;
        var b = stabilizer();
        //the stage is left out: the held surge keeps moving the level while the clients reconnect
        return "pulse=" + (a.pulseActive > 0f ? "active" : a.pulsePending > 0f ? "pending" : "none") + "@" + a.pulseX + "," + a.pulseY
            + " pulses=" + a.pulses + " stabilizer=" + (b == null ? "-" : b.online) + " fault=" + Vesper.fault.completed;
    }

    static String snapshot(){
        var r = reactor();
        return "wave=" + state.wave + " winWave=" + state.rules.winWave + " awakened=" + Vesper.activity.awakened + " ignited=" + (r != null && r.ignited)
            + " delivered=" + (r == null ? "-" : r.delivered[0] + "+" + r.delivered[1]) + " enemies=" + enemies();
    }
}
