package vesper.harness.suites;

import arc.files.*;
import arc.graphics.g2d.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.mod.Mods.*;
import mindustry.world.blocks.environment.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.world.*;

import java.lang.reflect.*;

import static mindustry.Vars.*;

/** Every Vesper content has its icons and regions, the bundled maps are listed, and both bundles have the same keys and every text the missions use. */
public class ContentSuite implements VesperHarness.Suite{
    public static final String galleryName = "Vesper Gallery", basinName = "First Awakening - The Silent Basin", faultName = "Resonance Fault - The Silent Basin",
        foundryName = "The Buried Foundry - The Silent Basin";

    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> {
            int checked = 0;
            Seq<String> missingIcons = new Seq<>(), absent = new Seq<>();
            for(Seq<Content> list : content.getContentMap()){
                for(Content c : list){
                    if(c.minfo.mod == null || !c.minfo.mod.name.equals("vesper") || !(c instanceof UnlockableContent u)) continue;
                    checked++;
                    if(u.fullIcon == null || !u.fullIcon.found() || u.uiIcon == null || !u.uiIcon.found()) missingIcons.add(u.name);
                    regions(u, u.getClass(), u.name + ".", absent);
                }
            }
            if(headless){
                h.line("info: " + checked + " Vesper contents loaded on a dedicated server (icons and regions need a renderer, not checked)");
            }else{
            h.check("content icons", missingIcons.isEmpty(), checked + " contents, missing icons: " + missingIcons);
            h.line("optional regions not present (informational): " + absent.size + " " + absent);

            DerelictReactor reactor = (DerelictReactor)VBlocks.derelictReactor;
            h.check("reactor state regions", reactor.glowRegion.found() && reactor.activeRegion.found(), "-glow and -active");
            StaticWall wall = (StaticWall)VBlocks.machineBasaltWall;
            h.check("basalt ridge regions", wall.variantRegions.length == 2 && wall.variantRegions[0].found() && wall.variantRegions[1].found()
                && wall.large.found(), "2 variants + large");
            }

            for(String name : new String[]{galleryName, basinName, faultName, foundryName}){
                var map = maps.all().find(m -> m.mod != null && m.mod.name.equals("vesper") && m.name().equals(name));
                h.check("bundled map listed: " + name, map != null, map == null ? "not found" : map.width + "x" + map.height);
            }

            LoadedMod mod = mods.getMod("vesper");
            ObjectSet<String> en = keys(mod.root.child("bundles").child("bundle.properties"));
            ObjectSet<String> pt = keys(mod.root.child("bundles").child("bundle_pt_BR.properties"));
            Seq<String> onlyEn = en.toSeq().select(k -> !pt.contains(k)), onlyPt = pt.toSeq().select(k -> !en.contains(k));
            h.check("bundles have the same keys", onlyEn.isEmpty() && onlyPt.isEmpty(),
                en.size + " keys; missing in pt_BR: " + onlyEn + "; missing in en: " + onlyPt);
            //texts looked up by key at run time: a missing one would show as ???key??? in game
            Seq<String> used = new Seq<>();
            for(var scenario : new vesper.mission.Scenario[]{vesper.Vesper.mission, vesper.Vesper.fault, vesper.Vesper.foundry}){
                used.addAll(scenario.key() + ".title", scenario.key() + ".victory");
                for(var objective : scenario.objectives()) used.add("vesper.objective." + objective.key);
            }
            used.addAll("vesper.fault.complete", "vesper.response.help", "vesper.pulse.incoming", "vesper.pulse.active", "vesper.pulse.announce", "vesper.pulse.toast",
                "vesper.stabilizer.bar", "vesper.stabilizer.off", "vesper.resonance.bar", "stat.vesper-resonance", "stat.vesper-stabilization",
                "vesper.activity.sources.stabilized", "vesper.activity.resonance", "vesper.activity.incoming.awakening.gates", "vesper.activity.announce.awakening.gates");
            for(var stage : new String[]{"dormant", "stirring", "pulses", "awakened"}) used.add("vesper.response.hint." + stage);
            //industrial archaeology: hold and release of the foundry, the markers, every site state and text, the rig
            used.addAll("vesper.foundry.hold", "vesper.foundry.hold.toast", "vesper.foundry.final", "vesper.foundry.complete", "vesper.mission.hold", "vesper.mission.hold.toast",
                "vesper.mission.final", "vesper.marker.foundry", "vesper.marker.archive", "vesper.site.unexplored", "vesper.site.choice", "vesper.site.effect",
                "vesper.site.restore", "vesper.site.dismantle", "vesper.site.restore.confirm", "vesper.site.dismantle.confirm", "vesper.site.restoring",
                "vesper.site.dismantling", "vesper.site.dismantled", "vesper.site.bar.investigation", "vesper.site.bar.restoration", "vesper.site.bar.dismantle",
                "vesper.site.ancient-recoverer.purpose", "vesper.site.archive-terminal.purpose", "vesper.rig.idle", "vesper.rig.bar");
            for(var siteState : vesper.world.AncientSite.SiteState.values()) used.add("vesper.site.state." + siteState.name());
            Seq<String> missing = used.select(k -> !en.contains(k));
            h.check("every text the missions and the planetary response use exists", missing.isEmpty(), used.size + " keys, missing: " + missing);
        });
    }

    static ObjectSet<String> keys(Fi file){
        ObjectSet<String> out = new ObjectSet<>();
        for(String line : file.readString("UTF-8").split("\n")){
            line = line.trim();
            int eq = line.indexOf('=');
            if(line.isEmpty() || line.startsWith("#") || eq < 0) continue;
            out.add(line.substring(0, eq).trim());
        }
        return out;
    }

    /** Engine regions that most content legitimately leaves out. */
    static final ObjectSet<String> optional = ObjectSet.with("customShadowRegion", "teamRegion", "teamRegions", "wreckRegions", "outlineRegion",
        "treadRegion", "baseJointRegion", "jointRegion", "footRegion", "legRegion", "legBaseRegion", "baseRegion", "rimRegion");

    /** Lists TextureRegion fields (including arrays) that resolved to nothing in the atlas. */
    static void regions(Object owner, Class<?> type, String prefix, Seq<String> absent){
        for(Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()){
            for(Field f : k.getDeclaredFields()){
                if(Modifier.isStatic(f.getModifiers())) continue;
                Class<?> ft = f.getType();
                while(ft.isArray()) ft = ft.getComponentType();
                if(!TextureRegion.class.isAssignableFrom(ft) || optional.contains(f.getName())) continue;
                try{
                    f.setAccessible(true);
                    collect(f.get(owner), prefix + f.getName(), absent);
                }catch(Throwable ignored){
                }
            }
        }
    }

    static void collect(@Nullable Object value, String name, Seq<String> absent){
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
}
