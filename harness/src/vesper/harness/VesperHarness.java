package vesper.harness;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.mod.*;
import mindustry.type.*;

import java.lang.reflect.*;
import java.nio.*;

import static mindustry.Vars.*;

/**
 * Development harness, inert unless the client runs with -Dvesper.harness. It checks every Vesper texture
 * region in the live atlas, builds the gallery world, saves it as a map, captures world-only screenshots
 * (the frame is read before the UI is drawn) and exits. Output goes to <data dir>/vesper-harness/.
 */
public class VesperHarness extends Mod{
    final boolean enabled = System.getProperty("vesper.harness") != null;
    final StringBuilder report = new StringBuilder();
    final Seq<Shot> shots = new Seq<>();
    Fi out;
    Vec2 camTarget;
    float camScale = 4f;
    Shot pending;
    int pendingFrames;
    boolean iconsPending;

    static class Shot{
        String name;
        float x, y, scale, settle;
        Runnable before;

        Shot(String name, float x, float y, float scale, float settle, Runnable before){
            this.name = name;
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.settle = settle;
            this.before = before;
        }
    }

    public VesperHarness(){
        if(!enabled) return;

        Events.on(ClientLoadEvent.class, e -> Time.run(90f, this::start));

        //runs before camera.update(), so the target wins over player-follow camera logic
        Events.run(Trigger.preDraw, () -> {
            if(camTarget != null){
                Core.camera.position.set(camTarget);
                renderer.targetscale = camScale;
                renderer.camerascale = camScale;
            }
        });

        //world is fully drawn here and the UI is not yet, so captures contain only the game world
        Events.run(Trigger.postDraw, () -> {
            if(iconsPending){
                iconsPending = false;
                dumpIcons();
            }
            if(pending != null && --pendingFrames <= 0){
                capture(pending.name);
                pending = null;
                Time.run(4f, this::next);
            }
        });
    }

    void start(){
        out = Core.settings.getDataDirectory().child("vesper-harness");
        out.deleteDirectory();
        out.mkdirs();
        line("Vesper harness on Mindustry " + Version.buildString());
        checkContent();

        try{
            logic.reset();
            world.loadGenerator(Gallery.width, Gallery.height, Gallery::generate);
            state.rules = rules();
            logic.play();
            line("gallery world generated: " + Gallery.width + "x" + Gallery.height + ", buildings: " + Groups.build.size());
        }catch(Throwable t){
            fail("gallery generation failed", t);
            return;
        }

        Time.run(20f, () -> {
            Gallery.connectLinks();
            saveMap();
            spawnExhibitUnits();
            iconsPending = true;
            planShots();
            Time.run(240f, this::next);
        });
    }

    Rules rules(){
        Rules r = new Rules();
        r.waves = false;
        r.waveTimer = false;
        r.canGameOver = false;
        r.fog = false;
        r.lighting = false;
        r.infiniteResources = true;
        r.placeRangeCheck = false;
        //sandbox gallery: player blocks need no power or inputs, so every showroom machine animates
        r.teams.get(Gallery.player).cheat = true;
        return r;
    }

    void saveMap(){
        try{
            Fi file = out.child("vesper-gallery.msav");
            MapIO.writeMap(file, new Map(StringMap.of(
                "name", "Vesper Gallery",
                "author", "Vesper",
                "description", "Art preview sandbox for The Silent Basin: two working production chains, a defensive line and a showroom of every Vesper block."
            )));
            line("map saved: " + file.name() + " (" + file.length() + " bytes)");
        }catch(Throwable t){
            fail("map save failed", t);
        }
    }

    void spawnExhibitUnits(){
        float[][] spots = {{70, 25}, {73, 25}, {70, 22}, {74, 22}};
        UnitType[] types = {
            content.unit("vesper-scout-drone"), content.unit("vesper-maintenance-drone"),
            content.unit("vesper-ancient-sentinel"), content.unit("vesper-rust-crawler")
        };
        for(int i = 0; i < types.length; i++){
            if(types[i] == null){
                line("MISSING unit type #" + i);
                continue;
            }
            Unit u = types[i].spawn(Gallery.player, spots[i][0] * tilesize, spots[i][1] * tilesize, 90f);
            u.apply(StatusEffects.unmoving, 60f * 60f * 60f * 3f);
            u.apply(StatusEffects.disarmed, 60f * 60f * 60f * 3f);
        }
        content.unit("vesper-scout-drone").spawn(Gallery.player, 9 * tilesize, 36 * tilesize, 0f);
    }

    void planShots(){
        //native scale (4 = one sprite pixel per screen pixel), then 2x close-ups and a zoomed-out overview
        shots.add(new Shot("01-chain-forge-core", 22, 24, 4f, 30f, null));
        shots.add(new Shot("02-power-refinery-defense", 30, 13, 4f, 30f, null));
        shots.add(new Shot("03-showroom", 61, 32, 4f, 30f, null));
        shots.add(new Shot("04-closeup-ancient", 58, 39.5f, 8f, 30f, null));
        shots.add(new Shot("05-closeup-industry", 58, 35.5f, 8f, 30f, null));
        shots.add(new Shot("06-closeup-extraction-logistics", 59, 29.5f, 8f, 30f, null));
        shots.add(new Shot("07-closeup-defense-units", 62, 24.5f, 8f, 30f, null));
        shots.add(new Shot("08-closeup-chain", 11, 28.5f, 8f, 30f, null));
        shots.add(new Shot("09-closeup-power-refinery", 27, 8.5f, 8f, 30f, null));
        //functional check without cheat: chains must deliver on their own for 30 s
        shots.add(new Shot("10-chains-without-cheat", 22, 20, 4f, 60f * 30f, this::beginChainCheck));
        shots.add(new Shot("11-defense-engagement", 41, 13, 4f, 150f, () -> {
            endChainCheck();
            spawnRaid();
        }));
        shots.add(new Shot("12-defense-engagement-later", 41, 13, 4f, 120f, null));
        shots.add(new Shot("13-overview", 39, 24, 2f, 30f, null));
    }

    int alloyAtStart, ammoAtStart;

    /** Core alloy and kinetic-turret ammo before switching cheat off for the player team. */
    void beginChainCheck(){
        state.rules.teams.get(Gallery.player).cheat = false;
        //start from empty so only what the chains deliver without cheat is counted
        var core = Gallery.player.core();
        var alloy = content.item("vesper-structural-alloy");
        if(core != null && alloy != null) core.items.set(alloy, 0);
        if(world.build(30, 10) instanceof mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild t){
            t.ammo.clear();
            t.totalAmmo = 0;
        }
        alloyAtStart = coreAlloy();
        ammoAtStart = turretAmmo();
        line("chain check: cheat off for 30 s; core alloy " + alloyAtStart + ", kinetic turret ammo " + ammoAtStart);
    }

    void endChainCheck(){
        int alloy = coreAlloy(), ammo = turretAmmo();
        line("chain check: after 30 s core alloy " + alloy + " (" + (alloy - alloyAtStart >= 0 ? "+" : "") + (alloy - alloyAtStart)
            + "), kinetic turret ammo " + ammo + " -> " + (alloy > alloyAtStart ? "forge chain WORKS" : "forge chain DID NOT DELIVER")
            + ", " + (ammo > 0 ? "refinery-to-turret chain WORKS" : "refinery-to-turret chain DID NOT DELIVER"));
        state.rules.teams.get(Gallery.player).cheat = true;
    }

    int coreAlloy(){
        var core = Gallery.player.core();
        var alloy = content.item("vesper-structural-alloy");
        return core == null || alloy == null ? -1 : core.items.get(alloy);
    }

    int turretAmmo(){
        Building b = world.build(30, 10);
        return b instanceof mindustry.world.blocks.defense.turrets.ItemTurret.ItemTurretBuild t ? t.totalAmmo : -1;
    }

    void spawnRaid(){
        UnitType crawler = content.unit("vesper-rust-crawler"), sentinel = content.unit("vesper-ancient-sentinel");
        crawler.spawn(Team.crux, 50 * tilesize, 8 * tilesize, 180f);
        crawler.spawn(Team.crux, 52 * tilesize, 12 * tilesize, 180f);
        sentinel.spawn(Team.crux, 51 * tilesize, 5 * tilesize, 180f);
        sentinel.spawn(Team.crux, 53 * tilesize, 14 * tilesize, 180f);
        line("raid spawned: 2 rust crawlers, 2 ancient sentinels (team crux)");
    }

    void next(){
        if(shots.isEmpty()){
            finish();
            return;
        }
        Shot s = shots.remove(0);
        if(s.before != null) s.before.run();
        camTarget = new Vec2(s.x * tilesize, s.y * tilesize);
        camScale = s.scale;
        Time.run(s.settle, () -> {
            pending = s;
            pendingFrames = 2;
        });
    }

    void capture(String name){
        int w = Core.graphics.getBackBufferWidth(), h = Core.graphics.getBackBufferHeight();
        Pixmap p = ScreenUtils.getFrameBufferPixmap(0, 0, w, h, true);
        ByteBuffer px = p.pixels;
        for(int i = 3; i < px.capacity(); i += 4) px.put(i, (byte)255);
        Fi file = out.child("shots").child(name + ".png");
        PixmapIO.writePng(file, p);
        p.dispose();
        line("shot " + name + " " + w + "x" + h + " scale " + camScale);
    }

    /** Renders the icons the game composed for every Vesper block, unit and item (what menus and tooltips show). */
    void dumpIcons(){
        Seq<UnlockableContent> list = new Seq<>();
        for(Seq<Content> group : content.getContentMap()){
            for(Content c : group){
                if(c instanceof UnlockableContent u && c.minfo.mod != null && c.minfo.mod.name.equals("vesper") && u.fullIcon.found()) list.add(u);
            }
        }
        int cell = 140, cols = 10, rows = (list.size + cols - 1) / cols;
        int w = cell * cols, h = cell * rows;
        Draw.flush();
        Mat proj = new Mat(Draw.proj());
        FrameBuffer buffer = new FrameBuffer(w, h);
        buffer.begin(Color.valueOf("3a3a42"));
        Draw.proj().setOrtho(0, 0, w, h);
        for(int i = 0; i < list.size; i++){
            TextureRegion r = list.get(i).fullIcon;
            Draw.rect(r, (i % cols) * cell + cell / 2f, h - ((i / cols) * cell + cell / 2f), r.width, r.height);
        }
        Draw.flush();
        Pixmap p = ScreenUtils.getFrameBufferPixmap(0, 0, w, h, true);
        buffer.end();
        buffer.dispose();
        Draw.proj(proj);
        PixmapIO.writePng(out.child("icons.png"), p);
        p.dispose();
        line("icons rendered: " + list.size + " (icons.png)");
    }

    void checkContent(){
        int checked = 0, iconMissing = 0;
        Seq<String> absent = new Seq<>();
        for(Seq<Content> list : content.getContentMap()){
            for(Content c : list){
                if(c.minfo.mod == null || !c.minfo.mod.name.equals("vesper") || !(c instanceof UnlockableContent u)) continue;
                checked++;
                boolean iconOk = u.fullIcon != null && u.fullIcon.found() && u.uiIcon != null && u.uiIcon.found();
                if(!iconOk) iconMissing++;
                Seq<String> fields = new Seq<>();
                regions(u, u.getClass(), "", fields);
                line(u.getContentType() + " " + u.name + (iconOk ? " icon OK" : " ICON MISSING")
                    + (fields.isEmpty() ? "" : "; absent optional regions: " + fields.toString(", ")));
                for(String f : fields) absent.add(u.name + "." + f);
            }
        }
        line("content checked: " + checked + ", missing icons: " + iconMissing + ", absent optional regions: " + absent.size);
        var bundled = maps.all().find(m -> m.mod != null && m.mod.name.equals("vesper"));
        line(bundled == null ? "bundled map: none listed" : "bundled map listed: '" + bundled.name() + "' " + bundled.width + "x" + bundled.height);
    }

    /** Lists TextureRegion fields (including arrays) that resolved to nothing in the atlas. */
    void regions(Object owner, Class<?> type, String prefix, Seq<String> absent){
        for(Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()){
            for(Field f : k.getDeclaredFields()){
                if(Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                if(!TextureRegion.class.isAssignableFrom(ft) && !(ft.isArray() && TextureRegion.class.isAssignableFrom(baseComponent(ft)))) continue;
                try{
                    f.setAccessible(true);
                    collect(f.get(owner), prefix + f.getName(), absent);
                }catch(Throwable ignored){
                }
            }
        }
    }

    static Class<?> baseComponent(Class<?> t){
        while(t.isArray()) t = t.getComponentType();
        return t;
    }

    void collect(Object value, String name, Seq<String> absent){
        if(value == null) return;
        if(value instanceof TextureAtlas.AtlasRegion r){
            if(!r.found()) absent.add(name);
        }else if(value.getClass().isArray()){
            int len = Array.getLength(value), missing = 0;
            for(int i = 0; i < len; i++){
                Object v = Array.get(value, i);
                if(v instanceof TextureAtlas.AtlasRegion r && !r.found()) missing++;
                else if(v != null && v.getClass().isArray()) collect(v, name + "[" + i + "]", absent);
            }
            if(missing > 0) absent.add(name + "(" + missing + "/" + len + ")");
        }
    }

    void line(String s){
        Log.info("[vesper-harness] " + s);
        report.append(s).append('\n');
    }

    void fail(String what, Throwable t){
        line("FAILED: " + what + ": " + Strings.getStackTrace(t));
        finish();
    }

    void finish(){
        camTarget = null;
        if(out != null) out.child("report.txt").writeString(report.toString());
        Log.info("[vesper-harness] done, exiting");
        Time.run(30f, () -> Core.app.exit());
    }
}
