package vesper.harness.suites;

import arc.*;
import arc.util.*;
import mindustry.gen.*;
import vesper.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.harness.maps.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.DerelictReactor.*;
import vesper.world.Stabilizer.*;

import static mindustry.Vars.*;
import static vesper.content.VItems.*;

/**
 * Client side of the local multiplayer test: joins the dedicated server, answers its queries with this client's
 * view of the game and performs the player actions it is asked for (carrying items, igniting the reactor,
 * switching a stabilizer, choosing a buried machine's fate, dropping out) with the normal network calls. The server records every check; this side only reports
 * and acts.
 */
public class MultiplayerClient implements VesperHarness.Suite{
    final String name = System.getProperty("vesper.mp.name", "Tester");
    final Bot bot = new Bot();
    boolean done, everConnected, delivering;
    /** The buried machine this client is restoring: "west", "east" or null. */
    @Nullable String restoring;
    float reconnectTimer;
    /** Cores to carry into the test dynamo. */
    int carrying;

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
            if(restoring != null){
                SiteBuild site = site(restoring);
                if(site == null || site.siteState != SiteState.restoring){
                    restoring = null;
                    bot.active = false;
                }else if(!bot.delivering()){
                    for(var stack : ((AncientSite)site.block).restoration){
                        if(site.remaining(stack.item) > 0){
                            bot.deliver(site, stack.item, site::remaining);
                            break;
                        }
                    }
                }
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
        }else if(data.equals("stabilizer-off") || data.equals("stabilizer-on")){
            h.line("mp client " + name + ": switching the stabilizer " + (data.endsWith("on") ? "on" : "off"));
            if(world.build(MultiplayerHost.stabilizerX, MultiplayerHost.stabilizerY) instanceof StabilizerBuild b) b.configure(data.endsWith("on"));
        }else if(data.startsWith("choose-")){
            //choose-<west|east>-<restore|dismantle>, as a player's tap on the dialog's button
            String[] parts = data.split("-");
            h.line("mp client " + name + ": choosing to " + parts[2] + " the " + parts[1] + " machine");
            if(site(parts[1]) != null) site(parts[1]).configure(parts[2].equals("restore") ? AncientSite.chooseRestore : AncientSite.chooseDismantle);
        }else if(data.startsWith("restore-")){
            h.line("mp client " + name + ": carrying the restoration into the " + data.substring(8) + " machine");
            restoring = data.substring(8);
            bot.active = true;
        }else if(data.equals("carry-cores")){
            h.line("mp client " + name + ": carrying resonance cores into the dynamo");
            var d = MultiplayerHost.mpDynamo();
            if(d != null){
                carrying = MultiplayerHost.carried;
                bot.active = true;
                bot.deliver(d, resonanceCore, item -> carrying - delivered(d));
            }
        }else if(data.equals("unloader-cores")){
            h.line("mp client " + name + ": setting the unloader to take resonance cores");
            if(world.build(13, 11) instanceof mindustry.world.blocks.storage.Unloader.UnloaderBuild u) u.configure(resonanceCore);
        }else if(data.equals("deep-start")){
            h.line("mp client " + name + ": asking the terminal to start the synchronization");
            if(DeepSignalSuite.terminal() != null) DeepSignalSuite.terminal().configure(true);
        }else if(data.startsWith("deep-node-")){
            //deep-node-<on|off>:<relay index>, as a player's tap on the relay
            boolean on = data.startsWith("deep-node-on");
            int i = Strings.parseInt(data.substring(data.indexOf(':') + 1), 0);
            h.line("mp client " + name + ": switching relay " + i + (on ? " on" : " off"));
            if(world.build(DeepSignalSuite.relays[i][0] + 1, DeepSignalSuite.relays[i][1] + 1) instanceof SignalNode.NodeBuild n) n.configure(on);
        }else if(data.equals("field-off") || data.equals("field-on")){
            h.line("mp client " + name + ": switching the field " + (data.endsWith("on") ? "on" : "off"));
            if(MultiplayerHost.mpField() != null) MultiplayerHost.mpField().configure(data.endsWith("on"));
        }else if(data.startsWith("rebuild-field:")){
            //as the rebuild button does: the team's plan for the lost block (sent by the server), with the setting it had
            String[] p = data.substring(14).split(",");
            h.line("mp client " + name + ": rebuilding the field from the team's plan at " + p[0] + "," + p[1] + " (" + p[2] + ")");
            bot.active = true;
            bot.build(VBlocks.containmentField, Strings.parseInt(p[0]), Strings.parseInt(p[1]), 0, Boolean.parseBoolean(p[2]));
        }else if(data.equals("disconnect")){
            h.line("mp client " + name + ": dropping out");
            netClient.disconnectQuietly();
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
        var stab = world.build(MultiplayerHost.stabilizerX, MultiplayerHost.stabilizerY) instanceof StabilizerBuild b ? b : null;
        var extractor = world.build(MultiplayerHost.faultExtractors[0][0], MultiplayerHost.faultExtractors[0][1]);
        return "map=" + (state.map == null ? "-" : state.map.name()) + ";size=" + world.width() + "x" + world.height() + ";foreign=" + Worlds.foreignTiles()
            + ";activity=" + a.active() + ";mission=" + m.active() + ";level=" + Strings.fixed(a.level, 1) + ";stage=" + a.stage.name()
            + ";awakened=" + a.awakened + ";pending=" + (a.pending > 0f) + ";incoming=" + (a.pendingSentinels + a.pendingCrawlers)
            + ";warning=" + (panel != null && panel.visible && a.pending > 0f) + ";wave=" + state.wave + ";winWave=" + state.rules.winWave
            + ";timer=" + state.rules.waveTimer + ";holding=" + m.holding() + ";released=" + m.released
            + ";enemies=" + Groups.unit.count(u -> u.team == state.rules.waveTeam)
            + ";delivered=" + (r == null ? "-" : r.delivered[0] + "+" + r.delivered[1]) + ";ignited=" + (r != null && r.ignited)
            + ";victory=" + m.victory + ";objective=" + (m.current() == null ? "done" : m.current().key)
            + ";gameover=" + state.gameOver + ";won=" + state.won
            + ";fault=" + Vesper.fault.active() + ";responses=" + a.responses() + ";stabilization=" + Strings.fixed(a.stabilization, 1)
            + ";stab=" + (stab == null ? "-" : (stab.online ? "on " : "off ") + Strings.fixed(stab.contribution, 1))
            + ";pulse=" + (a.pulseActive > 0f ? "active" : a.pulsePending > 0f ? "pending" : "none") + "@" + a.pulseX + "," + a.pulseY
            + ";extractor=" + (extractor == null ? "-" : Strings.fixed(Vesper.activity.resonance(extractor), 2))
            + ";faultVictory=" + Vesper.fault.victory + ";faultObjective=" + (Vesper.fault.current() == null ? "done" : Vesper.fault.current().key)
            + ";foundry=" + Vesper.foundry.active() + ";west=" + stateOf(site("west")) + ";east=" + stateOf(site("east")) + ";archive=" + stateOf(site("archive"))
            + ";westDelivered=" + delivered(site("west")) + ";eastDelivered=" + delivered(site("east"))
            + ";platform=" + (state.rules.isBanned(VBlocks.ancientPlatform) ? "locked" : "open") + ";foundryReleased=" + Vesper.foundry.released
            + ";foundryVictory=" + Vesper.foundry.victory + ";foundryObjective=" + (Vesper.foundry.current() == null ? "done" : Vesper.foundry.current().key)
            + ";works=" + Vesper.works.active() + ";relayDelivered=" + (Vesper.works.active() ? String.valueOf((long)Vesper.works.delivered()) : "-")
            + ";dynamoBurned=" + (MultiplayerHost.mpDynamo() == null ? "-" : MultiplayerHost.mpDynamo().coresBurned)
            + ";fieldOnline=" + (MultiplayerHost.mpField() == null ? "-" : MultiplayerHost.mpField().online)
            + ";fieldSpent=" + (MultiplayerHost.mpField() == null ? "-" : MultiplayerHost.mpField().coresSpent)
            + ";fieldOverloaded=" + (MultiplayerHost.mpField() != null && MultiplayerHost.mpField().overloaded())
            + ";worksVictory=" + Vesper.works.victory + ";worksObjective=" + (Vesper.works.current() == null ? "done" : Vesper.works.current().key)
            + ";unloaderItem=" + (world.build(13, 11) instanceof mindustry.world.blocks.storage.Unloader.UnloaderBuild u && u.sortItem != null ? u.sortItem.name : "-")
            + ";logisticsBurned=" + (Worlds.at(18, 10, 3) instanceof vesper.world.ResonanceDynamo.ResonanceDynamoBuild d ? d.coresBurned : -1)
            + ";deep=" + Vesper.deep.active() + ";" + deepView();
    }

    /** The relay world as this client sees it: the terminal's state and progress, every relay's state, the victory. */
    static String deepView(){
        var t = DeepSignalSuite.terminal();
        if(t == null || !Vesper.deep.active()) return "terminal=-";
        StringBuilder relays = new StringBuilder();
        for(int i = 0; i < 3; i++){
            var n = world.build(DeepSignalSuite.relays[i][0] + 1, DeepSignalSuite.relays[i][1] + 1);
            relays.append(i == 0 ? "" : ",").append(n instanceof SignalNode.NodeBuild node ? node.state().name() : "-");
        }
        return "terminal=" + t.syncState.name() + ";progress=" + Strings.fixed(t.progress, 3) + ";interrupted=" + (t.interrupted > 0f) + ";relays=" + relays
            + ";deepVictory=" + Vesper.deep.victory + ";deepCompleted=" + Vesper.deep.completed;
    }

    /** Cores this client has put into the dynamo so far: what it holds now plus what it burned. */
    static int delivered(vesper.world.ResonanceDynamo.ResonanceDynamoBuild d){
        return d.coresBurned + d.items.get(resonanceCore);
    }

    static @Nullable SiteBuild site(String which){
        var b = switch(which){
            case "west" -> Worlds.at(BuriedFoundryMap.westX, BuriedFoundryMap.westY, 3);
            case "east" -> Worlds.at(BuriedFoundryMap.eastX, BuriedFoundryMap.eastY, 3);
            default -> Worlds.at(BuriedFoundryMap.archiveX, BuriedFoundryMap.archiveY, 2);
        };
        return b instanceof SiteBuild s ? s : null;
    }

    static String stateOf(@Nullable SiteBuild b){
        return b == null ? "-" : b.siteState.name();
    }

    static String delivered(@Nullable SiteBuild b){
        if(b == null) return "-";
        StringBuilder out = new StringBuilder();
        for(int d : b.delivered) out.append(out.length() == 0 ? "" : "+").append(d);
        return out.toString();
    }

    static ReactorBuild reactor(){
        return Vesper.mission.reactor();
    }
}
