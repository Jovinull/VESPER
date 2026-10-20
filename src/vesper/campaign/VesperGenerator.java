package vesper.campaign;

import arc.*;
import arc.graphics.*;
import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.noise.*;
import mindustry.game.*;
import mindustry.maps.generators.*;
import mindustry.type.*;
import vesper.content.*;

/**
 * The VESPER planet: no procedural sectors, only the look of a world assembled from panels (dark hull, plating,
 * oxidized plates, exposed basalt). Resonance runs teal along part of the seams between them, lit on the night side
 * like wiring; the player's bases light their seams amber, and once Chapter One is complete the deep region glows.
 * Sectors are the chapter's presets; numbered ones are never landable.
 */
public class VesperGenerator extends PlanetGenerator{
    static final int plateCount = 60;
    static final float seamWidth = 0.005f;

    static final Color
    hull = Color.valueOf("30363e"), hullLight = Color.valueOf("3c434c"),
    plating = Color.valueOf("4b5760"), platingLight = Color.valueOf("63737b"),
    rust = Color.valueOf("6d5443"), rustLight = Color.valueOf("846a53"),
    basalt = Color.valueOf("262b31"), seam = Color.valueOf("14171b"), channel = Color.valueOf("167272"),
    veinGlow = Color.valueOf("1f9c97"), signal = Color.valueOf("7ce8d8"),
    amber = Color.valueOf("e9b55b"), amberDark = Color.valueOf("a5692c");

    final Vec3[] plates = new Vec3[plateCount];
    final int[] kind = new int[plateCount];
    final float[] level = new float[plateCount];

    public VesperGenerator(){
        Rand rand = new Rand(1931);
        for(int i = 0; i < plateCount; i++){
            plates[i] = new Vec3().setToRandomDirection(rand);
            float r = rand.random(1f);
            //mostly ancient hull; plating, oxidized plates and exposed basalt between them
            kind[i] = r < 0.4f ? 0 : r < 0.68f ? 1 : r < 0.9f ? 2 : 3;
            level[i] = rand.random(1f);
        }
    }

    final int[] near = new int[2];

    /** The two plates closest to a position (near[0] is the one under it); returns how far it is from the seam between them. */
    float plate(Vec3 p){
        float best = -2f, second = -2f;
        for(int i = 0; i < plateCount; i++){
            float d = plates[i].dot(p);
            if(d > best){
                second = best;
                near[1] = near[0];
                best = d;
                near[0] = i;
            }else if(d > second){
                second = d;
                near[1] = i;
            }
        }
        return best - second;
    }

    /** Seams that carry resonance: about half of them, chosen by the pair of plates they separate. */
    boolean channel(){
        int a = Math.min(near[0], near[1]), b = Math.max(near[0], near[1]);
        return ((a * 31 + b * 17) % 7) < 3;
    }

    @Override
    public float getHeight(Vec3 p){
        float edge = plate(p);
        //plates sit at stepped levels like panels; the seams are cut into them
        float h = 0.25f + Mathf.round(level[near[0]] * 3f) / 3f * 0.5f;
        if(edge < seamWidth) h -= 0.25f;
        return h;
    }

    @Override
    public void getColor(Vec3 p, Color out){
        float edge = plate(p);
        float n = Simplex.noise3d(seed, 3, 0.5f, 2.5f, p.x, p.y, p.z);
        if(edge < seamWidth){
            out.set(channel() ? channel : seam).a(1f);
            return;
        }
        switch(kind[near[0]]){
            case 0 -> out.set(hull).lerp(hullLight, n).a(0.7f);
            case 1 -> out.set(plating).lerp(platingLight, n).a(0.55f);
            case 2 -> out.set(rust).lerp(rustLight, n).a(0.95f);
            default -> out.set(basalt).a(1f);
        }
    }

    @Override
    public boolean isEmissive(){
        return true;
    }

    @Override
    public void getEmissiveColor(Vec3 p, Color out){
        float edge = plate(p);
        boolean onSeam = edge < seamWidth;
        //the player's bases: their seams burn amber, a few plates around them carry lights
        float base = 999f;
        for(Sector s : VPlanets.vesper.sectors){
            if(s.hasBase()) base = Math.min(base, p.dst(s.tile.v));
        }
        if(base < 0.14f && (onSeam || Simplex.noise3d(seed + 9, 2, 0.5f, 18f, p.x, p.y, p.z) > 0.66f)){
            out.set(amberDark).lerp(amber, 1f - base / 0.14f);
            return;
        }
        //the deep region answers once Chapter One is complete
        float deep = VSectors.deepSignal == null ? 999f : p.dst(VSectors.deepSignal.sector.tile.v);
        if(Chapter.complete() && deep < 0.1f){
            out.set(veinGlow).lerp(signal, 1f - deep / 0.1f);
            return;
        }
        if(onSeam && channel()){
            out.set(veinGlow).mul(0.85f);
        }
    }

    @Override
    public void addWeather(Sector sector, Rules rules){
        //each map keeps the weather it was authored with
    }

    @Override
    public boolean allowLanding(Sector sector){
        return false;
    }

    @Override
    public boolean allowAcceleratorLanding(Sector sector){
        return false;
    }

    /**
     * Chapter sectors start with their own supplies (the map's loadout), so a launch costs nothing: any of the
     * player's bases here can send the expedition. Prefers the sector being played.
     */
    @Override
    public @Nullable Sector findLaunchCandidate(Sector destination, @Nullable Sector selected){
        if(selected != null && selected.planet == destination.planet && selected.hasBase()) return selected;
        return destination.planet.sectors.min(Sector::hasBase, s -> s.tile.v.dst(destination.tile.v));
    }

    @Override
    public void getLockedText(Sector hovered, StringBuilder out){
        if(hovered.preset != null && hovered.preset == VSectors.deepSignal && hovered.preset.locked()){
            out.append("[#").append(signal).append("]").append(hovered.preset.localizedName).append("\n[lightgray]").append(Core.bundle.get("vesper.deep.locked"));
        }else{
            super.getLockedText(hovered, out);
        }
    }

    @Override
    public void onSectorCaptured(Sector sector){
        sector.planet.reloadMeshAsync();
    }

    @Override
    public void onSectorLost(Sector sector){
        sector.planet.reloadMeshAsync();
    }
}
