package vesper.content;

import arc.*;
import arc.func.*;
import arc.util.*;
import mindustry.ui.*;
import mindustry.world.meta.*;
import vesper.*;
import vesper.graphics.*;

/** Stats and bars shared by the blocks that take part in planetary activity. */
public class VStats{
    public static final Stat
    resonance = new Stat("vesper-resonance", StatCat.function),
    stabilization = new Stat("vesper-stabilization", StatCat.function),
    coreEnergy = new Stat("vesper-core-energy", StatCat.power),
    containment = new Stat("vesper-containment", StatCat.function);

    public static void addResonance(Stats stats){
        var a = Vesper.activity;
        stats.add(resonance, Core.bundle.format("vesper.stat.resonance", (int)a.resonanceFloor, Strings.autoFixed(a.resonanceMax, 1),
            Strings.autoFixed(a.pulseBoost, 1)));
    }

    /** Current speed multiplier of a resonance machine; the bar fills up to the highest a pulse can give. */
    public static Bar resonanceBar(Floatp multiplier){
        return new Bar(() -> Core.bundle.format("vesper.resonance.bar", Strings.autoFixed(multiplier.get(), 2)), () -> VPal.resonanceLight,
            () -> (multiplier.get() - 1f) / (Vesper.activity.resonanceMax * Vesper.activity.pulseBoost - 1f));
    }
}
