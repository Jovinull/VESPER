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
    transitBelt, cargoRouter, cargoJunction, cargoBridge, industrialConduit, resonanceLink,

    //extraction
    surveyDrill, reinforcedDrill, scrapCollector, crystalExtractor,

    //industry and power
    structuralForge, scrapRecycler, alloyRefinery, mineralCompressor,
    combustionEngine, powerAccumulator, experimentalResonator, componentAssembler;

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

        surveyDrill = new Drill("survey-drill"){{
            requirements(Category.production, with(ferroprism, 12));
            size = 2;
            tier = 2;
            drillTime = 600f;
        }};

        reinforcedDrill = new Drill("reinforced-drill"){{
            requirements(Category.production, with(ferroprism, 40, structuralAlloy, 20));
            size = 3;
            tier = 3;
            drillTime = 400f;
            consumePower(0.6f);
        }};

        scrapCollector = new Drill("scrap-collector"){{
            requirements(Category.production, with(ferroprism, 10));
            size = 2;
            tier = 1;
            drillTime = 480f;
            rotateSpeed = 1.6f;
        }};

        crystalExtractor = new Drill("crystal-extractor"){{
            requirements(Category.production, with(ferroprism, 30, recoveredCircuit, 10));
            size = 2;
            tier = 3;
            drillTime = 720f;
            rotateSpeed = 0.8f;
            drawRim = true;
            heatColor = VPal.resonance;
            consumePower(0.5f);
        }};

        structuralForge = new GenericCrafter("structural-forge"){{
            requirements(Category.crafting, with(ferroprism, 30, conductiveScrap, 15));
            size = 2;
            craftTime = 60f;
            craftEffect = Fx.smeltsmoke;
            outputItem = new ItemStack(structuralAlloy, 1);
            consumeItems(with(ferroprism, 2, vitreousCarbon, 1));
            drawer = new DrawMulti(new DrawDefault(), new DrawGlowRegion(){{ color = VPal.industrial; alpha = 0.7f; }});
        }};

        scrapRecycler = new GenericCrafter("scrap-recycler"){{
            requirements(Category.crafting, with(ferroprism, 25));
            size = 2;
            craftTime = 45f;
            outputItem = new ItemStack(ferroprism, 1);
            consumeItems(with(conductiveScrap, 2));
            consumePower(0.3f);
            drawer = new DrawMulti(new DrawDefault(),
                new DrawRegion("-rotator"){{ spinSprite = true; rotateSpeed = 4f; x = -3f; y = 1.75f; }},
                new DrawRegion("-rotator"){{ spinSprite = true; rotateSpeed = -4f; x = 3f; y = 1.75f; }}){{
                //the spinning rotor is a small region; the icon uses a footprint-sized still frame instead
                iconOverride = new String[]{"", "-rotors"};
            }};
        }};

        alloyRefinery = new GenericCrafter("alloy-refinery"){{
            requirements(Category.crafting, with(ferroprism, 80, structuralAlloy, 30));
            size = 3;
            craftTime = 75f;
            craftEffect = Fx.smeltsmoke;
            outputItem = new ItemStack(structuralAlloy, 3);
            consumeItems(with(ferroprism, 4, vitreousCarbon, 2));
            consumePower(1.5f);
            drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-top"), new DrawGlowRegion(){{ color = VPal.industrial; alpha = 0.8f; }});
        }};

        mineralCompressor = new GenericCrafter("mineral-compressor"){{
            requirements(Category.crafting, with(ferroprism, 40, structuralAlloy, 10));
            size = 2;
            craftTime = 90f;
            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(vitrifiedCompound, 1);
            consumeItems(with(vitreousCarbon, 2, conductiveScrap, 1));
            consumePower(0.6f);
            drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-top"));
        }};

        combustionEngine = new ConsumeGenerator("combustion-engine"){{
            requirements(Category.power, with(ferroprism, 25, conductiveScrap, 15));
            size = 2;
            powerProduction = 1.2f;
            itemDuration = 120f;
            generateEffect = Fx.generatespark;
            consume(new ConsumeItemFlammable());
            consume(new ConsumeItemExplode());
            drawer = new DrawMulti(new DrawDefault(), new DrawGlowRegion(){{ color = VPal.industrial; alpha = 0.9f; }});
        }};

        powerAccumulator = new Battery("power-accumulator"){{
            requirements(Category.power, with(ferroprism, 20, conductiveScrap, 30));
            size = 2;
            consumePowerBuffered(4000f);
            drawer = new DrawMulti(new DrawDefault(), new DrawPower(){{
                emptyLightColor = VPal.industrial;
                fullLightColor = VPal.resonanceLight;
            }}, new DrawRegion("-top"));
        }};

        experimentalResonator = new GenericCrafter("experimental-resonator"){{
            requirements(Category.crafting, with(structuralAlloy, 60, recoveredCircuit, 40, resonanceCrystal, 30));
            size = 3;
            craftTime = 120f;
            outputItem = new ItemStack(resonanceCore, 1);
            consumeItems(with(resonanceCrystal, 2, recoveredCircuit, 1));
            consumePower(3f);
            drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-rotator"){{ rotateSpeed = 0.6f; }},
                new DrawRegion("-top"), new DrawGlowRegion(){{ color = VPal.resonance; alpha = 0.85f; }});
        }};

        componentAssembler = new GenericCrafter("component-assembler"){{
            requirements(Category.crafting, with(ferroprism, 60, structuralAlloy, 40));
            size = 3;
            craftTime = 100f;
            outputItem = new ItemStack(recoveredCircuit, 2);
            consumeItems(with(structuralAlloy, 2, conductiveScrap, 2));
            consumePower(1f);
            drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-top"));
        }};
    }
}
