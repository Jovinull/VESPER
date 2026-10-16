package vesper.content;

import arc.graphics.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.environment.*;
import mindustry.world.blocks.liquid.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.production.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.consumers.*;
import mindustry.world.draw.*;
import mindustry.world.meta.*;
import vesper.graphics.*;
import vesper.world.*;

import static mindustry.type.ItemStack.*;
import static vesper.content.VItems.*;

/** The Silent Basin content. Behaviour is vanilla; values are prototype numbers for the art preview. */
public class VBlocks{
    public static Block
    //environment (declaration order sets floor edge priority)
    machineBasalt, scrapSoil, vitrifiedDust, rustedPlating,
    oreFerroprism, oreConductiveScrap, oreResonanceCrystal, oreVitreousCarbon;

    public static void load(){
        machineBasalt = new Floor("machine-basalt", 3);
        scrapSoil = new Floor("scrap-soil", 3);
        vitrifiedDust = new Floor("vitrified-dust", 3);
        rustedPlating = new Floor("rusted-plating", 3);

        oreFerroprism = new OreBlock("ore-ferroprism", ferroprism){{ variants = 2; }};
        oreConductiveScrap = new OreBlock("ore-conductive-scrap", conductiveScrap){{ variants = 2; }};
        oreResonanceCrystal = new OreBlock("ore-resonance-crystal", resonanceCrystal){{ variants = 2; }};
        oreVitreousCarbon = new OreBlock("ore-vitreous-carbon", vitreousCarbon){{ variants = 2; }};
    }
}
