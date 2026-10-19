package vesper.harness.suites;

import arc.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import vesper.*;
import vesper.harness.*;
import vesper.systems.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Captures of the resonance industry as a player sees it, in the window size and language the client runs with
 * (-PharnessWindow, -PharnessLocale): the dynamo working with its native bars, the containment field ready, holding a
 * pulse back and overloaded, the two blocks' stats in the database, and the Resonance Works map with its objectives.
 * The game's info panel shows the building under the mouse, so the suite pins the desktop mouse over it (test
 * fixture: the SDL backend's own fields). Checks only that every text shown is translated; the rest is evidence.
 */
public class IndustryUiSuite implements VesperHarness.Suite{
    boolean listening;
    float holdShock = -1f;
    /** World position the mouse is pinned over, or null. */
    @Nullable Vec2 hover;
    String tag = "";

    @Override
    public void define(VesperHarness h, Script s){
        if(headless) return;
        if(!listening){
            listening = true;
            Events.run(Trigger.update, () -> {
                if(holdShock >= 0f && Vesper.activity.active()) Vesper.activity.shock = holdShock;
                if(hover != null) pin(hover);
            });
        }
        var a = Vesper.activity;
        s.run(() -> {
            String[] wh = System.getProperty("vesper.harness.window", "").split("x");
            if(wh.length == 2) UiSuite.resize(h, Strings.parseInt(wh[0], 1280), Strings.parseInt(wh[1], 720));
        });
        s.wait(1f);
        s.run(() -> {
            tag = Core.graphics.getWidth() + "x" + Core.graphics.getHeight() + "-" + UiSuite.language();
            h.line("industry-ui: window " + tag);
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            Worlds.generate(IndustrySuite.width, IndustrySuite.height, IndustrySuite::generatePower, r);
        });
        s.wait(0.5f);
        s.run(() -> {
            IndustrySuite.link(15, 15, 10, 14, 3);
            for(int[] l : IndustrySuite.loads) IndustrySuite.link(15, 15, l[0], l[1], 2);
            for(int i = 0; i < IndustrySuite.loads.length; i++) IndustrySuite.load(i).configureAny(i < 3);
            world.build(5, 15).configureAny(resonanceCore);
        });
        s.wait(8f);
        shot(h, s, "01-dynamo", 11f, 15f, 4f);
        s.check("industry-ui: the dynamo's texts are translated", () -> translated(Core.bundle.format("vesper.dynamo.load", 60, "7.2"))
            && translated(resonanceDynamo.localizedName) && translated(resonanceDynamo.description), () -> resonanceDynamo.localizedName);

        // --- the field world: a pulse announced, landed, and a field without power
        s.run(() -> {
            holdShock = IndustrySuite.pulseShock;
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.teams.get(r.waveTeam).unitDamageMultiplier = 0f;
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagResponse, "true");
            r.tags.put(PlanetaryActivity.tagGates, IndustrySuite.gateX + "," + IndustrySuite.gateY);
            Worlds.generate(IndustrySuite.fieldWidth, IndustrySuite.fieldHeight, IndustrySuite::generateField, r);
        });
        s.wait(0.5f);
        s.run(() -> {
            for(int[] t : new int[][]{{27, 25}, {24, 14}}) world.build(t[0], t[1]).configureAny(resonanceCore);
            a.cooldown = 1e9f;
            a.pulseCooldown = 1e9f;
            //the second field has no power: it will be overloaded
            world.tile(26, 15).setNet(Blocks.air);
        });
        s.waitFor(() -> a.level >= 51f, 60f);
        s.run(() -> a.pulseCooldown = 0f);
        s.waitFor(() -> a.pulsePending > 0f, 20f);
        s.wait(2f);
        shot(h, s, "02-field-ready", 27.5f, 23.5f, 3.5f);
        s.waitFor(() -> a.pulseActive > 0f, 25f);
        s.wait(1.5f);
        shot(h, s, "03-field-absorbing", 27.5f, 23.5f, 2.2f);
        shot(h, s, "04-field-overloaded", 24.5f, 15.5f, 3.5f);
        s.check("industry-ui: the field's texts are translated", () -> {
            for(String state : new String[]{"off", "ready", "absorbing", "overloaded", "unpowered", "starved", "standby"}){
                if(!translated(Core.bundle.get("vesper.field.state." + state))) return false;
            }
            return translated(containmentField.localizedName) && translated(containmentField.description);
        }, () -> containmentField.localizedName);
        s.run(() -> {
            hover = null;
            holdShock = -1f;
        });

        // --- the database pages
        s.run(() -> ui.content.show(resonanceDynamo));
        h.shot("industry-" + window() + "-" + UiSuite.language() + "-05-dynamo-stats", 0f, 0f, 2f, true);
        s.run(() -> ui.content.hide());
        s.run(() -> ui.content.show(containmentField));
        h.shot("industry-" + window() + "-" + UiSuite.language() + "-06-field-stats", 0f, 0f, 2f, true);
        s.run(() -> ui.content.hide());

        // --- the Resonance Works map as it starts: the relay and the objectives in the HUD
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.worksName), Gamemode.survival));
        s.wait(3f);
        shot(h, s, "07-works-relay", 45f, 51f, 3f);
        s.check("industry-ui: the scenario's texts are translated", () -> translated(Core.bundle.get("vesper.works.title"))
            && translated(Vesper.works.objectives.first().text()) && translated(worksRelay.localizedName), () -> Core.bundle.get("vesper.works.title"));
        s.run(() -> {
            hover = null;
            if(ui.restart.isShown()) ui.restart.hide();
        });
    }

    /** Points the camera at a building, pins the mouse over it (the info panel shows its bars) and captures the screen. */
    void shot(VesperHarness h, Script s, String name, float tileX, float tileY, float scale){
        s.run(() -> hover = new Vec2(tileX * tilesize, tileY * tilesize));
        h.shot("industry-" + window() + "-" + UiSuite.language() + "-" + name, tileX, tileY, scale, true);
    }

    static String window(){
        return System.getProperty("vesper.harness.window", "default");
    }

    static boolean translated(String text){
        return text != null && !text.isEmpty() && !text.contains("???") && !text.startsWith("vesper.");
    }

    /** Sets the desktop backend's mouse position to the screen point over this world position. */
    static void pin(Vec2 world){
        try{
            Vec2 screen = Core.camera.project(Tmp.v1.set(world));
            var input = Core.input;
            var fx = input.getClass().getDeclaredField("mouseX");
            var fy = input.getClass().getDeclaredField("mouseY");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setInt(input, (int)screen.x);
            fy.setInt(input, (int)screen.y);
        }catch(Throwable ignored){
        }
    }
}
