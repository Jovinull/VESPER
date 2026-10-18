package vesper.harness.suites;

import arc.*;
import arc.func.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import vesper.*;
import vesper.harness.*;
import vesper.harness.maps.*;
import vesper.mission.*;
import vesper.systems.PlanetaryActivity.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.DerelictReactor.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * HUD inspection, not gameplay: the mission state is set directly (reactor filled, wave counter moved, victory
 * forced; on Resonance Fault a stabilizer and extractors placed and the activity surge held) to show each HUD state
 * quickly, including the tallest panel (a patrol and a pulse announced at once); on The Buried Foundry a buried
 * machine's panel is opened in each state and its choice dialog used. Run it per window size and language (-PharnessWindow=1280x720
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

        //The Buried Foundry: the mission, a buried machine's panel in each state (states set as test setup), the choice dialog, the hold
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.foundryName), Gamemode.survival));
        //switching maps in the same frame skips the menu, where the game drops a showing announcement: let the last one fade
        s.wait(5f);
        s.check("ui: The Buried Foundry shows its mission", () -> Vesper.scenario() == Vesper.foundry, () -> "");
        state(h, s, "8-foundry-start");
        site(h, s, "9-site-unexplored", FoundrySuite::west, false);
        s.run(() -> {
            FoundrySuite.west().investigation = 1f;
            FoundrySuite.west().siteState = SiteState.investigated;
        });
        site(h, s, "10-site-investigated", FoundrySuite::west, true);
        //the restore button asks first, with the cost; cancelling changes nothing
        s.run(() -> choiceButton(0).fireClick());
        s.wait(0.5f);
        s.check("ui: restoring asks for confirmation, with its cost", () -> Core.scene.getDialog() != null
            && dialogText().contains(ArchaeologySuite.restorationCost().first() + " " + structuralAlloy.localizedName), UiSuite::dialogText);
        h.shot("ui-" + System.getProperty("vesper.harness.window", "default") + "-" + language() + "-11-restore-dialog", 31, 51, 3f, true);
        s.run(() -> Core.scene.getDialog().hide());
        s.wait(0.5f);
        s.check("ui: cancelling the dialog changes nothing", () -> FoundrySuite.west().siteState == SiteState.investigated, () -> "" + FoundrySuite.west().siteState);
        //confirming applies the choice, and the open panel drops the buttons
        s.run(() -> choiceButton(1).fireClick());
        s.wait(0.5f);
        s.run(() -> Core.scene.getDialog().buttons.getChildren().peek().fireClick());
        s.wait(1f);
        s.check("ui: confirming dismantles; the open panel shows the dismantling without the choice", () -> FoundrySuite.west().siteState == SiteState.dismantling
            && control.input.config.isShown() && Core.scene.find("vesper-site-choices") == null, () -> "" + FoundrySuite.west().siteState);
        site(h, s, "12-site-dismantling", FoundrySuite::west, false);
        s.run(() -> {
            control.input.config.hideConfig();
            FoundrySuite.east().investigation = 1f;
            FoundrySuite.east().siteState = SiteState.investigated;
            FoundrySuite.east().configure(AncientSite.chooseRestore);
            ArchaeologySuite.give(FoundrySuite.east(), structuralAlloy, 60);
        });
        site(h, s, "13-site-restoring", FoundrySuite::east, false);
        s.run(() -> {
            for(ItemStack stack : ((AncientSite)ancientRecoverer).restoration) ArchaeologySuite.give(FoundrySuite.east(), stack.item, stack.amount);
        });
        s.wait(0.5f);
        site(h, s, "14-site-restored", FoundrySuite::east, false);
        s.run(() -> {
            control.input.config.hideConfig();
            state.wave = BuriedFoundry.holdWave;
        });
        s.waitFor(() -> Vesper.foundry.holding(), 10f);
        s.check("ui: The Buried Foundry holds its fourth wave until the archive is read", () -> Vesper.foundry.holding(), () -> "");
        state(h, s, "15-foundry-hold");
    }

    /** Opens a buried machine's panel as a tap does: it must be on screen and translated, with the two choices only when it offers them. */
    void site(VesperHarness h, Script s, String name, Prov<SiteBuild> site, boolean choices){
        boolean west = name.contains("unexplored") || name.contains("investigated") || name.contains("dismantling");
        int cx = west ? BuriedFoundryMap.westX + 1 : BuriedFoundryMap.eastX + 1, cy = BuriedFoundryMap.westY + 1;
        //the panel opens under its building, so the camera goes there first (as a player taps what is on screen)
        s.run(() -> h.look(cx, cy, 3f));
        s.wait(0.3f);
        s.run(() -> control.input.config.showConfig(site.get()));
        s.wait(0.6f);
        s.run(() -> {
            SiteBuild b = site.get();
            h.check("ui " + tag + " " + name + ": the machine's panel opens", control.input.config.isShown() && control.input.config.getSelected() == b, "");
            Element panel = configTable();
            if(panel == null || find(panel, b.infoText()) == null){
                h.fail("ui " + tag + " " + name + ": the machine's panel is on screen", "the panel or its text was not found");
            }else{
                Rect p = bounds(panel);
                float w = Core.graphics.getWidth(), ht = Core.graphics.getHeight();
                h.check("ui " + tag + " " + name + ": the machine's panel is on screen", p.x >= 0 && p.y >= 0 && p.x + p.width <= w + 0.5f && p.y + p.height <= ht + 0.5f,
                    "panel " + fmt(p) + " in " + (int)w + "x" + (int)ht);
            }
            boolean offers = Core.scene.find("vesper-site-choices") != null;
            h.check("ui " + tag + " " + name + ": " + (choices ? "it offers restore and dismantle" : "it offers no choice"), offers == choices
                && (!choices || (choiceButton(0).getText().toString().equals(Core.bundle.get("vesper.site.restore"))
                && choiceButton(1).getText().toString().equals(Core.bundle.get("vesper.site.dismantle")))), "");
            h.check("ui " + tag + " " + name + ": the text is translated", !b.infoText().contains("vesper.") && !b.infoText().contains("???"), b.infoText());
            h.line("ui " + tag + " " + name + ": " + b.infoText().replace('\n', ' '));
        });
        h.shot("ui-" + System.getProperty("vesper.harness.window", "default") + "-" + language() + "-" + name, cx, cy, 3f, true);
    }

    /** The table the game shows a building's configuration in (package-private in the game). */
    static @Nullable Element configTable(){
        try{
            var field = mindustry.ui.fragments.BlockConfigFragment.class.getDeclaredField("table");
            field.setAccessible(true);
            return (Element)field.get(control.input.config);
        }catch(Exception e){
            return null;
        }
    }

    /** The label showing exactly this text, anywhere under the element. */
    static @Nullable Element find(Element e, String text){
        if(e instanceof Label l && l.getText().toString().equals(text)) return l;
        if(e instanceof Group g){
            for(Element c : g.getChildren()){
                Element found = find(c, text);
                if(found != null) return found;
            }
        }
        return null;
    }

    static TextButton choiceButton(int index){
        return Core.scene.find("vesper-site-choices") instanceof Table t ? (TextButton)t.getChildren().get(index) : null;
    }

    static String dialogText(){
        StringBuilder out = new StringBuilder();
        if(Core.scene.getDialog() != null) text(Core.scene.getDialog(), out);
        return out.toString();
    }

    static void text(Element e, StringBuilder out){
        if(e instanceof Label l) out.append(l.getText()).append(' ');
        if(e instanceof Group g) for(Element c : g.getChildren()) text(c, out);
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
