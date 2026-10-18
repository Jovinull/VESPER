package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import vesper.*;
import vesper.graphics.*;

/**
 * An installation left by the planet's builders: a heavy structure whose resonance still leaks out (its -glow
 * region pulses additively). The glow strengthens with planetary activity, so the ruins visibly react when the
 * basin stirs. Ruins cannot be dismantled for materials; salvage will be its own mechanic.
 */
public class AncientStructure extends Wall{
    public Color glowColor = VPal.resonance;
    public float glowAlpha = 0.8f, pulseScale = 40f, pulseMag = 0.35f;
    public TextureRegion glowRegion;

    public AncientStructure(String name){
        super(name);
        solid = true;
        destructible = true;
        update = true;
        breakable = false;
        rebuildable = false;
    }

    @Override
    public void load(){
        super.load();
        glowRegion = Core.atlas.find(name + "-glow");
    }

    @Override
    public boolean canBreak(Tile tile){
        //ruins, not salvage: deconstructing would refund half of a cost nobody paid (breakable = false is not enough for a block that updates)
        return false;
    }

    public class AncientStructureBuild extends WallBuild{
        @Override
        public void draw(){
            super.draw();
            if(!glowRegion.found()) return;
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.blend(Blending.additive);
            float activity = Vesper.activity.fraction();
            Draw.color(glowColor, Math.min(1f, glowAlpha * (1f + activity * 0.6f) * (1f - pulseMag + Mathf.absin(Time.time + id * 13f, pulseScale * (1f - activity * 0.5f), pulseMag))));
            Draw.rect(glowRegion, x, y);
            Draw.blend();
            Draw.color();
            Draw.z(z);
        }
    }
}
