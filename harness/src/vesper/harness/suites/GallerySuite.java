package vesper.harness.suites;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.ItemTurret.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.harness.maps.*;

import static mindustry.Vars.*;

/**
 * Visual regression of the bundled gallery: the composed icons of every Vesper content, close-ups of each
 * family, and a functional check with cheat switched off (the chains must still deliver).
 */
public class GallerySuite implements VesperHarness.Suite{
    int alloyAtStart;
    boolean iconsDone;

    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.galleryName), Gamemode.survival));
        s.check("gallery: loads with the player team on cheat", () -> state.rules.teams.get(Gallery.player).cheat && Gallery.player.core() != null,
            () -> Groups.build.size() + " buildings");
        s.check("gallery: the map loads exactly as authored (no foreign ores or blocks)", () -> Worlds.foreignTiles() == 0,
            () -> Worlds.foreignTiles() + " tiles with content from outside Vesper");
        s.run(this::spawnExhibitUnits);
        s.run(() -> {
            iconsDone = false;
            Events.run(Trigger.postDraw, () -> {
                if(!iconsDone){
                    iconsDone = true;
                    dumpIcons(h);
                }
            });
        });
        s.waitFor(() -> iconsDone, 5f);
        s.wait(3f);

        //native scale (4 = one sprite pixel per screen pixel), then 2x close-ups and a zoomed-out overview
        h.shot("gallery-01-chain-forge-core", 22, 24, 4f, false);
        h.shot("gallery-02-power-refinery-defense", 30, 13, 4f, false);
        h.shot("gallery-03-showroom", 61, 32, 4f, false);
        h.shot("gallery-04-closeup-ancient", 58, 39.5f, 8f, false);
        h.shot("gallery-05-closeup-industry", 58, 35.5f, 8f, false);
        h.shot("gallery-06-closeup-extraction-logistics", 59, 29.5f, 8f, false);
        h.shot("gallery-07-closeup-defense-units", 62, 24.5f, 8f, false);
        h.shot("gallery-08-closeup-chain", 11, 28.5f, 8f, false);
        h.shot("gallery-09-closeup-power-refinery", 27, 8.5f, 8f, false);

        //without cheat for 30 s, the forge chain must deliver alloy and the refinery must feed the turret
        s.run(() -> {
            state.rules.teams.get(Gallery.player).cheat = false;
            Gallery.player.core().items.set(VItems.structuralAlloy, 0);
            if(world.build(30, 10) instanceof ItemTurretBuild t){
                t.ammo.clear();
                t.totalAmmo = 0;
            }
            alloyAtStart = Gallery.player.core().items.get(VItems.structuralAlloy);
        });
        s.wait(30f);
        h.shot("gallery-10-chains-without-cheat", 22, 20, 4f, false);
        s.check("gallery: forge chain delivers without cheat", () -> Gallery.player.core().items.get(VItems.structuralAlloy) > alloyAtStart,
            () -> "core alloy " + alloyAtStart + " -> " + Gallery.player.core().items.get(VItems.structuralAlloy));
        s.check("gallery: refinery feeds the kinetic turret without cheat", () -> world.build(30, 10) instanceof ItemTurretBuild t && t.totalAmmo > 0,
            () -> "ammo " + (world.build(30, 10) instanceof ItemTurretBuild t ? t.totalAmmo : -1));
        s.run(() -> {
            state.rules.teams.get(Gallery.player).cheat = true;
            UnitType crawler = VUnits.rustCrawler, sentinel = VUnits.ancientSentinel;
            crawler.spawn(Team.crux, 50 * tilesize, 8 * tilesize, 180f);
            crawler.spawn(Team.crux, 52 * tilesize, 12 * tilesize, 180f);
            sentinel.spawn(Team.crux, 51 * tilesize, 5 * tilesize, 180f);
            sentinel.spawn(Team.crux, 53 * tilesize, 14 * tilesize, 180f);
        });
        s.wait(2.5f);
        h.shot("gallery-11-defense-engagement", 41, 13, 4f, false);
        s.wait(2f);
        h.shot("gallery-12-defense-engagement-later", 41, 13, 4f, false);
        h.shot("gallery-13-overview", 39, 24, 2f, false);
        s.run(h::freeCamera);
    }

    void spawnExhibitUnits(){
        float[][] spots = {{70, 25}, {73, 25}, {70, 22}, {74, 22}};
        UnitType[] types = {VUnits.scoutDrone, VUnits.maintenanceDrone, VUnits.ancientSentinel, VUnits.rustCrawler};
        for(int i = 0; i < types.length; i++){
            Unit u = types[i].spawn(Gallery.player, spots[i][0] * tilesize, spots[i][1] * tilesize, 90f);
            u.apply(StatusEffects.unmoving, 60f * 60f * 60f * 3f);
            u.apply(StatusEffects.disarmed, 60f * 60f * 60f * 3f);
        }
        VUnits.scoutDrone.spawn(Gallery.player, 9 * tilesize, 36 * tilesize, 0f);
    }

    /** Renders the icons the game composed for every Vesper block, unit and item (what menus and tooltips show). */
    static void dumpIcons(VesperHarness h){
        Seq<UnlockableContent> list = new Seq<>();
        for(Seq<Content> group : content.getContentMap()){
            for(Content c : group){
                if(c instanceof UnlockableContent u && c.minfo.mod != null && c.minfo.mod.name.equals("vesper") && u.fullIcon.found()) list.add(u);
            }
        }
        int cell = 140, cols = 10, rows = (list.size + cols - 1) / cols;
        int w = cell * cols, ht = cell * rows;
        Draw.flush();
        Mat proj = new Mat(Draw.proj());
        FrameBuffer buffer = new FrameBuffer(w, ht);
        buffer.begin(Color.valueOf("3a3a42"));
        Draw.proj().setOrtho(0, 0, w, ht);
        for(int i = 0; i < list.size; i++){
            TextureRegion r = list.get(i).fullIcon;
            Draw.rect(r, (i % cols) * cell + cell / 2f, ht - ((i / cols) * cell + cell / 2f), r.width, r.height);
        }
        Draw.flush();
        Pixmap p = ScreenUtils.getFrameBufferPixmap(0, 0, w, ht, true);
        buffer.end();
        buffer.dispose();
        Draw.proj(proj);
        PixmapIO.writePng(h.out.child("icons.png"), p);
        p.dispose();
        h.line("icons rendered: " + list.size + " (icons.png)");
    }
}
