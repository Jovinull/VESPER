package vesper.harness;

import arc.*;
import arc.files.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.server.*;
import vesper.harness.suites.*;

import java.nio.*;

import static mindustry.Vars.*;

/**
 * Development harness, inert unless the client runs with -Dvesper.harness=SUITES (comma separated, or "all").
 * Suites append steps to a {@link Script}; checks are recorded as PASS/FAIL lines in report.txt and the process
 * exits with status 1 if any check failed, so Gradle fails the run. -Dvesper.harness.speed=N runs game time N
 * times faster (capped by the game's own maximum frame delta). Output goes to <data dir>/vesper-harness/.
 */
public class VesperHarness extends Mod{
    public static final String[] allSuites = {"content", "maps", "economy", "mission", "defeat", "gallery"};
    /** What a dedicated server (no renderer, no UI) can run: content runs its non-graphical checks only. */
    public static final String[] headlessSuites = {"content", "maps", "economy", "mission", "defeat"};

    final @Nullable String suites = System.getProperty("vesper.harness");
    final float speed = Strings.parseFloat(System.getProperty("vesper.harness.speed", "1"), 1f);
    final StringBuilder report = new StringBuilder();
    public final Script script = new Script(this);
    public Fi out;
    int passed, failed, pauses;
    boolean finished;

    //camera and capture
    @Nullable Vec2 camTarget;
    float camScale = 4f;
    @Nullable String pendingShot;
    boolean pendingUi;
    int pendingFrames;

    /** Winner of the last game over (null when none happened since the last reset). */
    public @Nullable Team lastWinner;

    public VesperHarness(){
        if(suites == null) return;

        //a dedicated server has no client load and does not advance Time.run while no world is loaded
        if(headless) Events.on(ServerLoadEvent.class, e -> Core.app.post(this::start));
        else Events.on(ClientLoadEvent.class, e -> Time.run(90f, this::start));
        Events.on(ResetEvent.class, e -> lastWinner = null);
        Events.on(GameOverEvent.class, e -> lastWinner = e.winner);

        Events.run(Trigger.update, () -> {
            if(out == null || finished) return;
            //a stray key press on the test window (space, escape) must not freeze a playthrough
            if(!headless && (state.isPaused() || (ui.paused != null && ui.paused.isShown()))){
                if(ui.paused != null && ui.paused.isShown()) ui.paused.hide();
                if(state.isPaused()) state.set(State.playing);
                pauses++;
                if(pauses <= 5) line("note: the game was paused (input on the test window?); resumed");
            }
            script.update(Time.delta);
            if(script.empty()) finish();
        });

        //runs before camera.update(), so the target wins over player-follow camera logic
        Events.run(Trigger.preDraw, () -> {
            if(camTarget != null){
                Core.camera.position.set(camTarget);
                renderer.targetscale = camScale;
                renderer.camerascale = camScale;
            }
        });

        //world fully drawn, UI not yet: world-only captures
        Events.run(Trigger.postDraw, () -> {
            if(pendingShot != null && !pendingUi && --pendingFrames <= 0) capture();
        });
        Events.run(Trigger.uiDrawEnd, () -> {
            if(pendingShot != null && pendingUi && --pendingFrames <= 0) capture();
        });
    }

    void start(){
        //the test client must keep running while its window is in the background (the game pauses it by default)
        Core.settings.put("backgroundpause", false);
        //a dedicated server announces game over and then rotates to its next map, replacing the next suite's world
        if(headless) ServerSide.keepWorld();
        out = Core.settings.getDataDirectory().child("vesper-harness");
        out.deleteDirectory();
        out.mkdirs();
        if(speed != 1f){
            Time.setDeltaProvider(() -> {
                float result = Core.graphics.getDeltaTime() * 60f * speed;
                return Float.isNaN(result) || Float.isInfinite(result) ? speed : Mathf.clamp(result, 0.0001f, maxDeltaClient);
            });
        }
        line("Vesper harness on Mindustry " + Version.buildString() + ", suites: " + suites + ", speed x" + speed);

        Seq<String> list = suites.equals("true") || suites.equals("all") ? Seq.with(headless ? headlessSuites : allSuites) : Seq.with(suites.split(","));
        for(String name : list){
            Suite suite = switch(name.trim()){
                case "content" -> new ContentSuite();
                case "maps" -> new MapsSuite();
                case "economy" -> new EconomySuite();
                case "mission" -> new MissionSuite();
                case "defeat" -> new DefeatSuite();
                case "gallery" -> new GallerySuite();
                case "ui" -> new UiSuite();
                case "mp-host" -> new MultiplayerHost();
                case "mp-client" -> new MultiplayerClient();
                default -> null;
            };
            if(suite == null){
                fail("unknown suite", name);
                continue;
            }
            script.suite(name.trim());
            script.run(() -> line("=== suite " + name.trim()));
            suite.define(this, script);
        }
    }

    // --- report

    public void line(String s){
        Log.info("[vesper-harness] " + s);
        report.append(s).append('\n');
    }

    public void pass(String check, String detail){
        passed++;
        line("PASS " + check + (detail.isEmpty() ? "" : " - " + detail));
    }

    public void fail(String check, String detail){
        failed++;
        line("FAIL " + check + (detail.isEmpty() ? "" : " - " + detail));
    }

    public void check(String check, boolean ok, String detail){
        if(ok) pass(check, detail);
        else fail(check, detail);
    }

    // --- camera and screenshots

    public void look(float tileX, float tileY, float scale){
        camTarget = new Vec2(tileX * tilesize, tileY * tilesize);
        camScale = scale;
    }

    public void freeCamera(){
        camTarget = null;
    }

    /** Points the camera, lets the scene settle and captures the world (ui=false) or the whole screen. */
    public void shot(String name, float tileX, float tileY, float scale, boolean ui){
        if(headless) return;
        script.run(() -> look(tileX, tileY, scale));
        script.wait(0.5f);
        script.step(new Script.Step(){
            boolean requested;

            @Override
            public boolean update(float delta){
                if(!requested){
                    requested = true;
                    pendingShot = name;
                    pendingUi = ui;
                    pendingFrames = 2;
                    return false;
                }
                return pendingShot == null;
            }
        });
    }

    /** Captures on the next frame, outside the script (for actions requested over the network). */
    public void captureNow(String name, boolean ui){
        if(headless || pendingShot != null) return;
        pendingShot = name;
        pendingUi = ui;
        pendingFrames = 2;
    }

    void capture(){
        int w = Core.graphics.getBackBufferWidth(), h = Core.graphics.getBackBufferHeight();
        Pixmap p = ScreenUtils.getFrameBufferPixmap(0, 0, w, h, true);
        ByteBuffer px = p.pixels;
        for(int i = 3; i < px.capacity(); i += 4) px.put(i, (byte)255);
        Fi file = out.child("shots").child(pendingShot + ".png");
        PixmapIO.writePng(file, p);
        p.dispose();
        line("shot " + pendingShot + " " + w + "x" + h + " scale " + camScale + (pendingUi ? " (with UI)" : ""));
        pendingShot = null;
    }

    void finish(){
        if(finished) return;
        finished = true;
        freeCamera();
        line("SUMMARY " + passed + " passed, " + failed + " failed" + (pauses > 0 ? " (game resumed after " + pauses + " pauses)" : ""));
        out.child("report.txt").writeString(report.toString());
        Log.info("[vesper-harness] done, exiting with status " + (failed > 0 ? 1 : 0));
        int status = failed > 0 ? 1 : 0;
        Core.settings.forceSave();
        //exit from a thread of our own: System.exit on the render thread can deadlock on the backend's shutdown hooks,
        //and a backgrounded window may stop running posted tasks. The report is written already.
        Thread exit = new Thread(() -> {
            Threads.sleep(500);
            Runtime.getRuntime().halt(status);
        }, "vesper-harness-exit");
        exit.setDaemon(false);
        exit.start();
    }

    /** Server-only calls, in a class of their own: the desktop client has no server classes to load. */
    static class ServerSide{
        static void keepWorld(){
            ServerControl.instance.gameOverListener = e -> {
                state.gameOver = true;
                Call.updateGameOver(e.winner);
            };
        }
    }

    /** A group of steps testing one area. */
    public interface Suite{
        void define(VesperHarness h, Script s);
    }
}
