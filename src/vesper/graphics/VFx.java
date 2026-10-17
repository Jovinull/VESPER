package vesper.graphics;

import arc.graphics.g2d.*;
import arc.math.*;
import mindustry.entities.*;

/** Vesper effects. */
public class VFx{
    public static final Effect

    /** A dead reactor catching: two resonance rings sweep outwards and shed sparks. e.rotation is the footprint size. */
    reactorIgnition = new Effect(100f, 400f, e -> {
        float reach = Math.max(e.rotation, 32f);
        Draw.color(VPal.resonanceLight, VPal.resonance, e.fin());
        Lines.stroke(5f * e.fout());
        Lines.circle(e.x, e.y, reach * 0.5f + e.finpow() * reach * 4f);
        Lines.stroke(2.5f * e.fout());
        Lines.circle(e.x, e.y, reach * 0.3f + e.finpow() * reach * 2.4f);
        Angles.randLenVectors(e.id, 28, reach * 0.4f + e.finpow() * reach * 3.4f, (x, y) -> Fill.square(e.x + x, e.y + y, 3f * e.fout(), 45f));
        Draw.reset();
    }),

    /** Pulsing danger ring where ancient machines are about to emerge. */
    gateWarning = new Effect(60f, 120f, e -> {
        Draw.color(VPal.dangerLight, VPal.danger, e.fin());
        Lines.stroke(3f * e.fout());
        Lines.poly(e.x, e.y, 6, 10f + e.fin() * 26f, 30f);
        Draw.reset();
    });
}
