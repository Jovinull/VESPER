package vesper.campaign;

import arc.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.game.Objectives.*;
import mindustry.game.Schematic.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.world.blocks.storage.*;
import vesper.*;
import vesper.content.*;
import vesper.world.AncientSite.*;

import static mindustry.Vars.*;

/**
 * Chapter One of the VESPER campaign, The Silent Basin: First Awakening, Resonance Fault and The Buried Foundry on
 * the VESPER planet. The native campaign does the rest (sector saves, capture, the planet map, research); this adds
 * what securing a sector means for the chapter: records of what the player found, the chapter's end, and the
 * planet's free launches. Progress lives in the profile's settings like the rest of the campaign and is written
 * by the host or the single player only; a client announces what it sees and keeps nothing. Custom games never
 * touch it.
 */
public class Chapter{
    /** The chapter's sectors in order; securing one makes the next available (through the tech tree). */
    public static final Seq<SectorPreset> sectors = new Seq<>();
    static final String prefix = "vesper-campaign-";

    /** Short records the player recovers, a few per sector; texts in the bundles ({@code vesper.record.<id>.*}). */
    public enum Record{
        reactorSeals("reactor-seals", 0),
        resonanceNetwork("resonance-network", 1),
        containment("containment", 1),
        restoredMachine("restored-machine", 2),
        dismantledMachine("dismantled-machine", 2),
        archiveIndex("archive-index", 2),
        deepSignal("deep-signal", 2);

        public static final Record[] all = values();

        public final String id;
        /** Index in {@link #sectors} of the sector where it is found. */
        public final int sector;

        Record(String id, int sector){
            this.id = id;
            this.sector = sector;
        }

        public boolean found(){
            return Core.settings.getBool(prefix + "record-" + id, false) || session.contains(this);
        }

        public String title(){
            return Core.bundle.get("vesper.record." + id + ".title");
        }

        public String text(){
            return Core.bundle.get("vesper.record." + id + ".text");
        }
    }

    /** A record found right now (fired on every side; persisted by the host). */
    public static class RecordEvent{
        public final Record record;

        RecordEvent(Record record){
            this.record = record;
        }
    }

    /** A chapter sector was captured; fired after the chapter's progress is written. */
    public static class SecuredEvent{
        public final SectorPreset preset;
        /** First capture of this sector, and whether this capture ended the chapter. */
        public final boolean initial, chapterComplete;

        SecuredEvent(SectorPreset preset, boolean initial, boolean chapterComplete){
            this.preset = preset;
            this.initial = initial;
            this.chapterComplete = chapterComplete;
        }
    }

    /** Records announced in the current world (all a multiplayer guest keeps of them). */
    static final ObjectSet<Record> session = new ObjectSet<>();

    public Chapter(){
        Events.on(ClientLoadEvent.class, e -> registerExpedition());
        Events.on(SectorCaptureEvent.class, e -> captured(e.sector, e.initialCapture));
        Events.on(SiteEvent.class, e -> site(e.site));
        Events.run(Trigger.update, this::update);
        //leaving a world forgets what was only seen there (a guest's records were never its own); the host's are in the settings
        Events.on(ResetEvent.class, e -> session.clear());
    }

    /** Whether the world being played is a sector of this chapter. */
    public static boolean inChapter(){
        return state.isCampaign() && state.rules.sector != null && sectors.contains(state.rules.sector.preset);
    }

    public static boolean secured(SectorPreset preset){
        return Core.settings.getBool(prefix + "secured-" + preset.name, false);
    }

    /** Whether all three sectors were secured, at any time (replaying or losing one later changes nothing). */
    public static boolean complete(){
        return Core.settings.getBool(prefix + "chapter-one", false);
    }

    /** Whether the archive of The Buried Foundry was read in the campaign: the Ancient Platform is recovered technology. */
    public static boolean archiveRead(){
        return Core.settings.getBool(prefix + "archive-read", false);
    }

    /** Records a discovery once; only in a chapter sector. */
    public static void discover(Record record){
        if(!inChapter() || record.found()) return;
        session.add(record);
        if(!net.client()) Core.settings.put(prefix + "record-" + record.id, true);
        Events.fire(new RecordEvent(record));
    }

    void update(){
        if(!state.isGame() || !inChapter()) return;
        var reactor = Vesper.mission.active() ? Vesper.mission.reactor() : null;
        //the reactor's seals show once it is restored: someone shut it down on purpose
        if(reactor != null && (reactor.primed() || reactor.ignited)) discover(Record.reactorSeals);
        if(Vesper.fault.active()){
            //powered extractors hear the network; the first response shows how it behaves
            if(Vesper.fault.completed >= 2) discover(Record.resonanceNetwork);
            if(Vesper.activity.pulses > 0 || Vesper.activity.patrols > 0 || Vesper.activity.pending > 0f) discover(Record.containment);
        }
    }

    void site(SiteBuild site){
        if(!inChapter()) return;
        if(site.block == VBlocks.ancientRecoverer){
            if(site.siteState == SiteState.restored) discover(Record.restoredMachine);
            if(site.siteState == SiteState.dismantled) discover(Record.dismantledMachine);
        }else if(site.block == VBlocks.archiveTerminal && site.siteState == SiteState.investigated){
            discover(Record.archiveIndex);
            if(!net.client() && !archiveRead()){
                Core.settings.put(prefix + "archive-read", true);
                //the platform's research objective is met now; the native check unlocks it once its other requirements are
                if(!headless) control.checkAutoUnlocks();
            }
        }
    }

    void captured(Sector sector, boolean initial){
        int index = sectors.indexOf(sector.preset);
        if(index < 0) return;
        boolean ended = false;
        //the sector's records belong to its story even when a trigger was skipped (a quiet Resonance Fault, say)
        for(Record r : Record.all){
            if(r.sector == index && r != Record.restoredMachine && r != Record.dismantledMachine && sector == state.rules.sector) discover(r);
        }
        if(!net.client()){
            Core.settings.put(prefix + "secured-" + sector.preset.name, true);
            if(!complete() && sectors.allMatch(Chapter::secured)){
                Core.settings.put(prefix + "chapter-one", true);
                ended = true;
                //the deep region starts to answer on the planet's surface
                sector.planet.reloadMeshAsync();
            }
        }
        Events.fire(new SecuredEvent(sector.preset, initial, ended));
    }

    /**
     * Chapter sectors start with their own supplies (each map's loadout, as authored), so a launch here costs
     * nothing. The native launch dialog charges the destination map's loadout and the core of the launch
     * schematic: the copy of the map the preset keeps for that dialog drops its loadout (the sector still lands
     * with the real one, read from the map file), and the expedition schematic costs nothing.
     */
    public static void freeLaunch(SectorPreset preset){
        var map = preset.generator.map;
        if(map == null) return;
        Rules rules = map.rules();
        rules.loadout.clear();
        map.tags.put("rules", JsonIO.write(rules));
    }

    static void registerExpedition(){
        CoreBlock core = (CoreBlock)VBlocks.coreFoothold;
        var list = schematics.getLoadouts().get(core, Seq::new);
        if(list.contains(s -> s instanceof Expedition)) return;
        list.add(new Expedition(core));
    }

    /** The launch schematic of the planet: one foothold core, free (the map provides the real one). */
    static class Expedition extends Schematic{
        Expedition(CoreBlock core){
            super(Seq.with(new Stile(core, 0, 0, null, (byte)0)), new StringMap(), core.size, core.size);
        }

        @Override
        public ItemSeq requirements(){
            return new ItemSeq();
        }
    }
}
