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
    oreFerroprism, oreConductiveScrap, oreResonanceCrystal, oreVitreousCarbon,

    //logistics
    transitBelt, cargoRouter, cargoJunction, cargoBridge, industrialConduit, resonanceLink;

    public static void load(){
        machineBasalt = new Floor("machine-basalt", 3);
        scrapSoil = new Floor("scrap-soil", 3);
        vitrifiedDust = new Floor("vitrified-dust", 3);
        rustedPlating = new Floor("rusted-plating", 3);

        oreFerroprism = new OreBlock("ore-ferroprism", ferroprism){{ variants = 2; }};
        oreConductiveScrap = new OreBlock("ore-conductive-scrap", conductiveScrap){{ variants = 2; }};
        oreResonanceCrystal = new OreBlock("ore-resonance-crystal", resonanceCrystal){{ variants = 2; }};
        oreVitreousCarbon = new OreBlock("ore-vitreous-carbon", vitreousCarbon){{ variants = 2; }};

        transitBelt = new Conveyor("transit-belt"){{
            requirements(Category.distribution, with(ferroprism, 1));
            health = 55;
            speed = 0.045f;
            displayedSpeed = 6.2f;
        }};

        cargoRouter = new Router("cargo-router"){{
            requirements(Category.distribution, with(ferroprism, 3));
        }};

        cargoJunction = new Junction("cargo-junction"){{
            requirements(Category.distribution, with(ferroprism, 3));
            speed = 26;
            capacity = 6;
        }};

        cargoBridge = new BufferedItemBridge("cargo-bridge"){{
            requirements(Category.distribution, with(ferroprism, 6, conductiveScrap, 6));
            range = 4;
            speed = 74f;
            arrowSpacing = 6f;
            bufferCapacity = 14;
        }};

        industrialConduit = new Conduit("industrial-conduit"){{
            requirements(Category.liquid, with(ferroprism, 1, vitreousCarbon, 1));
            health = 60;
        }};

        resonanceLink = new PowerNode("resonance-link"){{
            requirements(Category.power, with(ferroprism, 2, resonanceCrystal, 2));
            maxNodes = 8;
            laserRange = 7f;
            laserColor1 = VPal.resonanceLight;
            laserColor2 = VPal.danger;
        }};
    }
}
