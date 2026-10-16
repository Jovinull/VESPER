package vesper.world;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import mindustry.world.blocks.defense.*;
import vesper.graphics.*;

/**
 * An installation left by the planet's builders. For now it is an indestructible-looking heavy structure whose
 * resonance still leaks out (its -glow region pulses additively); recovery mechanics will build on this class.
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
    }

    @Override
    public void load(){
        super.load();
        glowRegion = Core.atlas.find(name + "-glow");
    }

    public class AncientStructureBuild extends WallBuild{
        @Override
        public void draw(){
            super.draw();
            if(!glowRegion.found()) return;
            float z = Draw.z();
            Draw.z(z + 0.01f);
            Draw.blend(Blending.additive);
            Draw.color(glowColor, glowAlpha * (1f - pulseMag + Mathf.absin(Time.time + id * 13f, pulseScale, pulseMag)));
            Draw.rect(glowRegion, x, y);
            Draw.blend();
            Draw.color();
            Draw.z(z);
        }
    }
}
