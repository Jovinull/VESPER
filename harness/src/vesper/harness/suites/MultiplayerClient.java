package vesper.harness.suites;

import arc.*;
import arc.util.*;
import mindustry.gen.*;
import vesper.*;
import vesper.harness.*;
import vesper.world.DerelictReactor.*;

import static mindustry.Vars.*;
import static vesper.content.VItems.*;

/**
 * Client side of the local multiplayer test: joins the dedicated server, answers its queries with this client's
 * view of the game and performs the player actions it is asked for (carrying items, igniting the reactor) with
 * the normal network calls. The server records every check; this side only reports and acts.
 */
public class MultiplayerClient implements VesperHarness.Suite{
    final String name = System.getProperty("vesper.mp.name", "Tester");
    final Bot bot = new Bot();
    boolean done, everConnected, delivering;
    float reconnectTimer;

    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> {
            netClient.addPacketHandler(MultiplayerHost.packet, data -> handle(h, data));
            player.name = name;
            Core.settings.put("name", name);
            ui.join.connect("127.0.0.1", MultiplayerHost.port);
            h.line("mp client " + name + ": connecting to port " + MultiplayerHost.port);
        });
        s.step(d -> {
            if(net.client()) everConnected = true;
            //fallback only: the server keeps clients connected across its reloads
            if(everConnected && !net.active() && !done && (reconnectTimer += Time.delta) > 180f){
                reconnectTimer = 0f;
                h.line("mp client " + name + ": disconnected, reconnecting");
                ui.join.connect("127.0.0.1", MultiplayerHost.port);
            }
            if(delivering && reactor() != null){
                if(reactor().remaining(structuralAlloy) > 0) bot.deliver(reactor(), structuralAlloy);
                else if(reactor().remaining(recoveredCircuit) > 0) bot.deliver(reactor(), recoveredCircuit);
                else delivering = bot.active = false;
            }
            return done;
        });
    }

    void handle(VesperHarness h, String data){
        if(data.startsWith("query:")){
            String id = data.substring(6);
            Call.serverPacketReliable(MultiplayerHost.packet, id + "|" + name + "|" + view());
        }else if(data.equals("deliver")){
            h.line("mp client " + name + ": carrying the restoration materials");
            bot.active = delivering = true;
        }else if(data.equals("ignite")){
            h.line("mp client " + name + ": asking to ignite the reactor");
            if(reactor() != null) reactor().configure(true);
        }else if(data.startsWith("shot:")){
            h.captureNow(data.substring(5) + "-" + name.toLowerCase(), true);
        }else if(data.equals("bye")){
            done = true;
        }
    }

    /** This client's view of the game, as key=value pairs. */
    String view(){
        var a = Vesper.activity;
        var m = Vesper.mission;
        var r = reactor();
        var panel = ui.hudGroup.find("vesper-basin");
        return "map=" + (state.map == null ? "-" : state.map.name()) + ";size=" + world.width() + "x" + world.height() + ";foreign=" + Worlds.foreignTiles()
            + ";activity=" + a.active() + ";mission=" + m.active() + ";level=" + Strings.fixed(a.level, 1) + ";stage=" + a.stage.name()
            + ";awakened=" + a.awakened + ";pending=" + (a.pending > 0f) + ";incoming=" + (a.pendingSentinels + a.pendingCrawlers)
            + ";warning=" + (panel != null && panel.visible && a.pending > 0f) + ";wave=" + state.wave + ";winWave=" + state.rules.winWave
            + ";timer=" + state.rules.waveTimer + ";holding=" + m.holding() + ";released=" + m.released
            + ";enemies=" + Groups.unit.count(u -> u.team == state.rules.waveTeam)
            + ";delivered=" + (r == null ? "-" : r.delivered[0] + "+" + r.delivered[1]) + ";ignited=" + (r != null && r.ignited)
            + ";victory=" + m.victory + ";objective=" + (m.current() == null ? "done" : m.current().key)
            + ";gameover=" + state.gameOver + ";won=" + state.won;
    }

    static ReactorBuild reactor(){
        return Vesper.mission.reactor();
    }
}
