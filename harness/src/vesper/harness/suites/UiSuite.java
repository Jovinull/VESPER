package vesper.harness.suites;

import arc.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import vesper.*;
import vesper.harness.*;
import vesper.systems.PlanetaryActivity.*;
import vesper.world.DerelictReactor.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * HUD inspection, not gameplay: the mission state is set directly (reactor filled, wave counter moved, victory
 * forced; on Resonance Fault a stabilizer and extractors placed and the activity surge held) to show each HUD state
 * quickly, including the tallest panel (a patrol and a pulse announced at once). Run it per window size and language (-PharnessWindow=1280x720
 * -PharnessLocale=pt_BR); each state is captured and the panel must stay on screen without covering any vanilla
 * HUD element (wave panel, minimap, build menu...).
 */
public class UiSuite implements VesperHarness.Suite{
    String tag = "";
    /** Fixture: while >= 0 the activity surge is held here (to reach a state quickly). */
    float holdShock = -1f;
    boolean listening;

    @Override
    public void define(VesperHarness h, Script s){
        if(headless) return;
        if(!listening){
            listening = true;
            Events.run(EventType.Trigger.update, () -> {
                if(holdShock >= 0f && Vesper.activity.active()) Vesper.activity.shock = holdShock;
            });
        }
        s.run(() -> {
            String window = System.getProperty("vesper.harness.window", "");
            String[] wh = window.split("x");
            if(wh.length == 2) resize(h, Strings.parseInt(wh[0], 1280), Strings.parseInt(wh[1], 720));
        });
        s.wait(1f);
        s.run(() -> {
            tag = Core.graphics.getWidth() + "x" + Core.graphics.getHeight() + "-" + language();
            h.line("ui: window " + Core.graphics.getWidth() + "x" + Core.graphics.getHeight() + ", scale " + Scl.scl(1f) + ", language " + Core.bundle.getLocale());
            Worlds.play(Worlds.bundled(ContentSuite.basinName), Gamemode.survival);
        });
        s.wait(2f);
        //a locale Vesper has no bundle for (pt_PT for a bare "pt") would silently show its English text
        s.check("ui: Vesper is translated to the game language", () -> language().equals("en")
            || mods.getMod(Vesper.class).root.child("bundles").child("bundle_" + Core.bundle.getLocale() + ".properties").exists(), () -> "game language " + Core.bundle.getLocale());
        state(h, s, "1-start");

        //the hold: reactor primed by test setup, fifth wave reached
        s.run(() -> {
            ReactorBuild r = Vesper.mission.reactor();
            r.handleStack(structuralAlloy, 300, null);
            r.handleStack(recoveredCircuit, 100, null);
            state.wave = 5;
        });
        s.wait(2f);
        s.check("ui: hold state reached", () -> Vesper.mission.holding(), () -> "");
        state(h, s, "2-hold-primed");

        s.run(() -> Vesper.mission.reactor().configure(true));
        //with almost no industry the level needs a few seconds after the surge to reach Awakened
        s.waitFor(() -> Vesper.activity.pending > 0f, 20f);
        s.wait(1f);
        state(h, s, "3-awakening-warning");

        s.run(() -> {
            Groups.unit.each(u -> u.team == state.rules.waveTeam, Unit::kill);
            state.wave = state.rules.winWave;
        });
        s.waitFor(() -> Vesper.mission.victory, 10f);
        s.wait(0.5f);
        state(h, s, "4-victory");
        s.run(() -> {
            if(ui.restart.isShown()) ui.restart.hide();
        });

        //Resonance Fault: the planetary response lines (stabilization, resonance multiplier, pulse and patrol warnings)
        s.run(() -> {
            Worlds.play(Worlds.bundled(ContentSuite.faultName), Gamemode.survival);
            Team team = state.rules.defaultTeam;
            world.tile(MultiplayerHost.powerX, MultiplayerHost.powerY).setBlock(Blocks.powerSource, team, 0);
            world.tile(MultiplayerHost.stabilizerX, MultiplayerHost.stabilizerY).setBlock(stabilizerPylon, team, 0);
            for(int[] e : MultiplayerHost.faultExtractors) world.tile(e[0], e[1]).setBlock(crystalExtractor, team, 0);
            for(int[] v : MultiplayerHost.faultSinks) world.tile(v[0], v[1]).setBlock(Blocks.itemVoid, team, 0);
        });
        s.wait(0.5f);
        s.run(() -> {
            Building source = world.build(MultiplayerHost.powerX, MultiplayerHost.powerY);
            source.configure(world.build(MultiplayerHost.stabilizerX, MultiplayerHost.stabilizerY).pos());
            for(int[] e : MultiplayerHost.faultExtractors) source.configure(world.build(e[0], e[1]).pos());
            holdShock = 10f;
        });
        s.wait(3f);
        s.check("ui: Resonance Fault shows its mission and the stabilization", () -> Vesper.scenario() == Vesper.fault && Vesper.activity.stabilization > 11f, () -> "");
        state(h, s, "5-fault-start");
        s.run(() -> {
            holdShock = 60f;
            Vesper.activity.pulseCooldown = 0f;
        });
        s.waitFor(() -> Vesper.activity.pulsePending > 0f, 40f);
        s.wait(1f);
        state(h, s, "6-fault-pulse-warning");
        s.run(() -> {
            holdShock = 90f;
            Vesper.activity.cooldown = 0f;
        });
        s.waitFor(() -> Vesper.activity.stage == Stage.awakened && Vesper.activity.pending > 0f && Vesper.activity.pulsePending > 0f, 40f);
        s.wait(1f);
        s.check("ui: a patrol and a pulse announced together", () -> Vesper.activity.pending > 0f && Vesper.activity.pulsePending > 0f, () -> "");
        state(h, s, "7-fault-patrol-and-pulse");
        s.run(() -> {
            holdShock = -1f;
            Groups.unit.each(u -> u.team == state.rules.waveTeam, Unit::kill);
        });
    }

    /** The desktop backend (SDL) can resize its window, but the cross-platform Graphics API has no call for it. */
    static void resize(VesperHarness h, int w, int ht){
        try{
            Object app = Core.app;
            long window = (long)app.getClass().getMethod("getWindow").invoke(app);
            Class<?> sdl = Class.forName("arc.backend.sdl.jni.SDL");
            try{
                sdl.getMethod("SDL_RestoreWindow", long.class).invoke(null, window);
            }catch(NoSuchMethodException ignored){
            }
            Core.graphics.getClass().getMethod("setWindowSize", int.class, int.class).invoke(Core.graphics, w, ht);
        }catch(Throwable t){
            h.line("ui: could not resize the window: " + t);
        }
    }

    void state(VesperHarness h, Script s, String name){
        s.run(() -> {
            Element panel = panel();
            if(panel == null){
                h.fail("ui " + name + ": panel visible", "panel not found");
                return;
            }
            Rect p = bounds(panel);
            float w = Core.graphics.getWidth(), ht = Core.graphics.getHeight();
            h.check("ui " + tag + " " + name + ": panel fully on screen", p.x >= 0 && p.y >= 0 && p.x + p.width <= w + 0.5f && p.y + p.height <= ht + 0.5f,
                "panel " + fmt(p) + " in " + (int)w + "x" + (int)ht);
            Seq<String> hits = new Seq<>();
            Element root = ui.hudGroup.find("vesper-basin");
            for(Element e : ui.hudGroup.getChildren()){
                if(e != root) collect(e, p, e.name == null ? e.getClass().getSimpleName() : e.name, hits);
            }
            h.check("ui " + tag + " " + name + ": panel covers no vanilla HUD element", hits.isEmpty(), hits.isEmpty() ? "panel " + fmt(p) : hits.toString());
        });
        h.shot("ui-" + System.getProperty("vesper.harness.window", "default") + "-" + language() + "-" + name, 30, 28, 2f, true);
    }

    /** English is the root bundle, whose locale has no language. */
    static String language(){
        String language = Core.bundle.getLocale().getLanguage();
        return language.isEmpty() ? "en" : language;
    }

    /** The visible panel inside the HUD fill table. */
    static Element panel(){
        Element root = ui.hudGroup.find("vesper-basin");
        return root instanceof Table t && t.getChildren().size > 0 ? t.getChildren().first() : null;
    }

    static Rect bounds(Element e){
        Vec2 v = e.localToStageCoordinates(new Vec2(0f, 0f));
        return new Rect(v.x, v.y, e.getWidth(), e.getHeight());
    }

    /** Visible leaf elements of a vanilla HUD group that intersect the panel. */
    static void collect(Element e, Rect panel, String owner, Seq<String> hits){
        if(!e.visible) return;
        if(e instanceof Group g && g.getChildren().size > 0){
            for(Element c : g.getChildren()) collect(c, panel, e.name != null ? e.name : owner, hits);
        }else if(!(e instanceof Group) && e.getWidth() > 1f && e.getHeight() > 1f && e.color.a > 0.01f){
            Rect r = bounds(e);
            if(r.overlaps(panel)) hits.add(owner + "/" + (e.name == null ? e.getClass().getSimpleName() : e.name) + " " + fmt(r));
        }
    }

    static String fmt(Rect r){
        return "(" + (int)r.x + "," + (int)r.y + " " + (int)r.width + "x" + (int)r.height + ")";
    }
}
