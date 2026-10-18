package vesper.graphics;

import arc.*;
import arc.graphics.*;
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

    /** Where an announced resonance pulse will land: the shock ring closing onto the epicentre. e.rotation is the radius. */
    pulseWarning = new Effect(60f, 500f, e -> {
        TextureRegion ring = Core.atlas.find("vesper-energy-pulse");
        float r = e.rotation * 2f * (1.15f - e.fin() * 0.35f);
        Draw.color(VPal.resonanceLight, VPal.resonance, e.fin());
        Draw.alpha(0.75f * e.fslope());
        Draw.rect(ring, e.x, e.y, r, r);
        Lines.stroke(1.5f * e.fslope());
        Lines.circle(e.x, e.y, e.rotation);
        Draw.reset();
    }),

    /** A resonance pulse landing: the ring sweeps out to its radius and sheds crystal sparks. e.rotation is the radius. */
    pulseImpact = new Effect(90f, 600f, e -> {
        TextureRegion ring = Core.atlas.find("vesper-energy-pulse");
        float r = e.rotation * 2f * e.finpow();
        Draw.color(Color.white, VPal.resonance, e.fin());
        Draw.alpha(e.fout());
        Draw.rect(ring, e.x, e.y, r, r);
        Angles.randLenVectors(e.id, 24, e.rotation * e.finpow(), (x, y) -> Fill.square(e.x + x, e.y + y, 2.5f * e.fout(), 45f));
        Draw.reset();
    }),

    /** The area of a pulse in progress, breathing faintly. e.rotation is the radius. */
    pulseField = new Effect(60f, 500f, e -> {
        Draw.color(VPal.resonance);
        Draw.alpha(0.35f * e.fslope());
        Lines.stroke(2f);
        Lines.circle(e.x, e.y, e.rotation);
        Draw.reset();
    }),

    /** An ancient machine coming back: rings fold in and flash out, sparks thrown off. e.rotation is the footprint size. */
    restorationComplete = new Effect(120f, 400f, e -> {
        TextureRegion ring = Core.atlas.find("vesper-energy-pulse");
        float reach = Math.max(e.rotation, 24f);
        Draw.color(Color.white, VPal.resonance, e.fin());
        Draw.alpha(e.fout());
        float r = reach * (e.fin() < 0.3f ? 2.4f - e.fin() / 0.3f * 1.4f : 1f + (e.fin() - 0.3f) * 3f);
        Draw.rect(ring, e.x, e.y, r * 2f, r * 2f);
        Lines.stroke(3f * e.fout());
        Lines.square(e.x, e.y, reach * 0.55f + e.finpow() * reach * 0.6f, 45f);
        Angles.randLenVectors(e.id, 20, reach * 0.3f + e.finpow() * reach * 1.6f, (x, y) -> Fill.square(e.x + x, e.y + y, 2.5f * e.fout(), 45f));
        Draw.reset();
    }),

    /** A restored ancient machine finishing a product: a short resonance flicker. */
    recovererCraft = new Effect(40f, e -> {
        Draw.color(VPal.resonanceLight, VPal.resonance, e.fin());
        Lines.stroke(1.5f * e.fout());
        Lines.circle(e.x, e.y, 4f + e.fin() * 10f);
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
