package vesper.harness.suites;

import mindustry.game.*;
import vesper.harness.*;

import static mindustry.Vars.*;

/** An undefended foothold must fall: four waves sent at once destroy the core and the game declares defeat. */
public class DefeatSuite implements VesperHarness.Suite{
    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.basinName), Gamemode.survival));
        s.wait(1f);
        s.run(() -> {
            for(int i = 0; i < 4; i++) logic.runWave();
        });
        s.until("defeat: the undefended foothold falls to four waves", () -> h.lastWinner == state.rules.waveTeam, 240f,
            () -> "winner " + h.lastWinner + ", core " + (state.rules.defaultTeam.core() == null ? "destroyed" : (int)state.rules.defaultTeam.core().health + " hp"));
        s.check("defeat: game over state set", () -> state.gameOver, () -> "");
        s.run(() -> {
            if(!headless && ui.restart.isShown()) ui.restart.hide();
        });
    }
}
