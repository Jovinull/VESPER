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
import vesper.systems.*;
import vesper.world.*;

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.*;
import static vesper.content.VItems.*;

/**
 * The Silent Basin content. Values are balanced for First Awakening: every recipe is reachable from ferroprism,
 * conductive scrap and vitreous carbon; resonance crystal is an optional late resource (pulse turret). Resonance
 * machines (crystal extractor, experimental resonator) speed up with planetary activity; the stabilizer pylon
 * holds it down. Both only matter on maps that enable planetary activity.
 */
public class VBlocks{
    public static Block
    //environment (declaration order sets floor edge priority)
    machineBasalt, scrapSoil, vitrifiedDust, rustedPlating, machineBasaltWall,
    oreFerroprism, oreConductiveScrap, oreResonanceCrystal, oreVitreousCarbon,

    //logistics
    transitBelt, cargoRouter, cargoJunction, cargoBridge, cargoUnloader, industrialConduit, resonanceLink,

    //extraction
    surveyDrill, reinforcedDrill, scrapCollector, crystalExtractor,

    //industry and power
    structuralForge, scrapRecycler, alloyRefinery, mineralCompressor,
    combustionEngine, powerAccumulator, experimentalResonator, componentAssembler,

    //defense
    reinforcedWall, heavyWall, kineticTurret, pulseTurret, ancientPlatform,

    //special
    coreFoothold, derelictReactor, stabilizerPylon, dataTerminal, recoverableRuin,

    //industrial archaeology
    surveyRig, ancientRecoverer, archiveTerminal,

    //resonance industry: recurring uses for resonance cores, and the relay of the Resonance Works map
    resonanceDynamo, containmentField, worksRelay;

    /**
     * Visibility of every buildable Vesper block: everywhere but the campaigns of other planets, where the tech tree
     * would otherwise show what it unlocks on VESPER. Custom games are unchanged.
     */
    public static final BuildVisibility vesperOnly = new BuildVisibility(() -> state == null || !state.isCampaign() || state.getPlanet() == VPlanets.vesper);

    public static void load(){
        machineBasalt = new Floor("machine-basalt", 3);
        scrapSoil = new Floor("scrap-soil", 3);
        vitrifiedDust = new Floor("vitrified-dust", 3);
        rustedPlating = new Floor("rusted-plating", 3);
        machineBasaltWall = new StaticWall("machine-basalt-wall"){{
            variants = 2;
        }};
        ((Floor)machineBasalt).wall = machineBasaltWall;

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

        //takes items out of the core, a container or a machine next to it (one item, or any) and passes them on: the
        //vanilla unloader's logic (same team only, balanced between stores, filter saved), drawn and priced for Vesper
        cargoUnloader = new Unloader("cargo-unloader"){{
            requirements(Category.distribution, with(ferroprism, 25, structuralAlloy, 20, recoveredCircuit, 20));
            speed = 60f / 11f;
            group = BlockGroup.transportation;
        }};

        industrialConduit = new Conduit("industrial-conduit"){{
            requirements(Category.liquid, with(ferroprism, 1, vitreousCarbon, 1));
            health = 60;
        }};

        resonanceLink = new PowerNode("resonance-link"){{
            requirements(Category.power, with(ferroprism, 3, conductiveScrap, 3));
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
            //no booster: without this, a drill with no optional consumer gets the 1.6x liquid boost twice (speed and warmup)
            liquidBoostIntensity = 1f;
        }};

        reinforcedDrill = new Drill("reinforced-drill"){{
            requirements(Category.production, with(ferroprism, 40, structuralAlloy, 20));
            size = 3;
            tier = 3;
            drillTime = 400f;
            liquidBoostIntensity = 1f;
            consumePower(0.6f);
        }};

        scrapCollector = new Drill("scrap-collector"){{
            requirements(Category.production, with(ferroprism, 10));
            size = 2;
            tier = 1;
            drillTime = 480f;
            liquidBoostIntensity = 1f;
            rotateSpeed = 1.6f;
        }};

        crystalExtractor = new ResonantDrill("crystal-extractor"){{
            requirements(Category.production, with(ferroprism, 30, recoveredCircuit, 10));
            size = 2;
            tier = 3;
            drillTime = 720f;
            liquidBoostIntensity = 1f;
            rotateSpeed = 0.8f;
            drawRim = true;
            heatColor = VPal.resonance;
            consumePower(0.5f);
        }};

        structuralForge = new GenericCrafter("structural-forge"){{
            requirements(Category.crafting, with(ferroprism, 30, conductiveScrap, 15));
            size = 2;
            craftTime = 90f;
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
            craftTime = 120f;
            craftEffect = Fx.smeltsmoke;
            outputItem = new ItemStack(structuralAlloy, 2);
            consumeItems(with(ferroprism, 3, vitreousCarbon, 1));
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
            itemDuration = 150f;
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

        experimentalResonator = new ResonantCrafter("experimental-resonator"){{
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
            requirements(Category.crafting, with(ferroprism, 50, structuralAlloy, 30));
            size = 3;
            craftTime = 120f;
            outputItem = new ItemStack(recoveredCircuit, 1);
            consumeItems(with(structuralAlloy, 1, conductiveScrap, 2));
            consumePower(0.75f);
            drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-top"));
        }};

        int wallHealth = 120 * 4;
        reinforcedWall = new Wall("reinforced-wall"){{
            requirements(Category.defense, with(structuralAlloy, 6));
            health = wallHealth;
        }};

        heavyWall = new Wall("heavy-wall"){{
            requirements(Category.defense, with(structuralAlloy, 24));
            size = 2;
            health = wallHealth * 4;
        }};

        kineticTurret = new ItemTurret("kinetic-turret"){{
            requirements(Category.turret, with(ferroprism, 40, conductiveScrap, 20));
            size = 2;
            health = 900;
            range = 180f;
            reload = 20f;
            recoil = 2f;
            shootY = 8f;
            rotateSpeed = 8f;
            shootCone = 12f;
            ammo(
                ferroprism, new BasicBulletType(3.2f, 16f){{
                    width = 8f;
                    height = 10f;
                    lifetime = 60f;
                    ammoMultiplier = 3f;
                    frontColor = VPal.industrialLight;
                    backColor = VPal.industrial;
                }},
                structuralAlloy, new BasicBulletType(3.6f, 28f){{
                    width = 9f;
                    height = 12f;
                    lifetime = 55f;
                    ammoMultiplier = 4f;
                    pierce = true;
                    pierceCap = 2;
                    frontColor = Color.white;
                    backColor = VPal.steelLight;
                }}
            );
        }};

        pulseTurret = new PowerTurret("pulse-turret"){{
            requirements(Category.turret, with(structuralAlloy, 30, resonanceCrystal, 15));
            size = 2;
            health = 820;
            range = 175f;
            reload = 24f;
            recoil = 1.5f;
            shootY = 10f;
            heatColor = VPal.resonance;
            shootSound = Sounds.shootLaser;
            consumePower(2.5f);
            shootType = new LaserBoltBulletType(5.2f, 20f){{
                lifetime = 34f;
                healPercent = 0f;
                frontColor = Color.white;
                backColor = VPal.resonance;
            }};
        }};

        ancientPlatform = new PowerTurret("ancient-platform"){{
            requirements(Category.turret, with(structuralAlloy, 120, recoveredCircuit, 40, resonanceCore, 20));
            size = 3;
            health = 2400;
            range = 205f;
            reload = 90f;
            recoil = 3f;
            shootY = 22f;
            shake = 2f;
            heatColor = VPal.resonance;
            shootSound = Sounds.shootLancer;
            consumePower(6f);
            shootType = new LaserBulletType(60f){{
                colors = new Color[]{VPal.resonanceDark.cpy().a(0.4f), VPal.resonance, Color.white};
                length = 210f;
                width = 16f;
                hitEffect = Fx.hitLancer;
            }};
        }};

        coreFoothold = new CoreBlock("core-foothold"){{
            requirements(Category.effect, BuildVisibility.editorOnly, with(ferroprism, 1500, conductiveScrap, 800));
            alwaysUnlocked = true;
            isFirstTier = true;
            unitType = VUnits.maintenanceDrone;
            health = 2800;
            armor = 2f;
            itemCapacity = 4000;
            size = 3;
            unitCapModifier = 8;
        }};

        derelictReactor = new DerelictReactor("derelict-reactor"){{
            requirements(Category.effect, BuildVisibility.sandboxOnly, with(structuralAlloy, 200));
            size = 4;
            health = 8000;
            restoration = with(structuralAlloy, 300, recoveredCircuit, 100);
            powerProduction = 5f;
            ignitionEffect = VFx.reactorIgnition;
        }};

        stabilizerPylon = new Stabilizer("stabilizer-pylon"){{
            requirements(Category.effect, with(ferroprism, 40, structuralAlloy, 30, recoveredCircuit, 20));
            size = 2;
            health = 1600;
            consumePower(1f);
        }};

        dataTerminal = new AncientStructure("data-terminal"){{
            requirements(Category.effect, BuildVisibility.sandboxOnly, with(structuralAlloy, 40));
            size = 2;
            health = 1200;
            pulseMag = 0.15f;
        }};

        recoverableRuin = new AncientStructure("recoverable-ruin"){{
            requirements(Category.effect, BuildVisibility.sandboxOnly, with(structuralAlloy, 80));
            size = 3;
            health = 3000;
            glowColor = VPal.industrial;
            pulseScale = 18f;
            pulseMag = 0.5f;
        }};

        //industrial archaeology (maps tagged vesper.archaeology): the rig a player builds and the installations a map places
        surveyRig = new SurveyRig("survey-rig"){{
            requirements(Category.effect, with(ferroprism, 30, structuralAlloy, 20, recoveredCircuit, 10));
            size = 2;
            health = 400;
            consumePower(1f);
            consumeItem(recoveredCircuit);
        }};

        ancientRecoverer = new AncientSite("ancient-recoverer"){{
            requirements(Category.effect, BuildVisibility.sandboxOnly, with(structuralAlloy, 200));
            size = 3;
            health = 3000;
            investigateTime = 30f * 60f;
            investigateSurge = 6f;
            salvage = with(structuralAlloy, 140, recoveredCircuit, 50, conductiveScrap, 120);
            dismantleTime = 45f * 60f;
            restoration = with(structuralAlloy, 100, recoveredCircuit, 40, resonanceCrystal, 30);
            restoreSurge = 12f;
            recipe = with(conductiveScrap, 4, resonanceCrystal, 1);
            output = new ItemStack(resonanceCore, 1);
            craftTime = 180f;
            runningActivity = 8f;
            consumePower(1f);
        }};

        archiveTerminal = new AncientSite("archive-terminal"){{
            requirements(Category.effect, BuildVisibility.sandboxOnly, with(structuralAlloy, 40));
            size = 2;
            health = 1200;
            investigateTime = 60f * 60f;
            investigateSurge = 15f;
            unlocks = ancientPlatform;
        }};

        //resonance industry: a generator that burns cores on demand, and a field that keeps pulses off the industry around it
        resonanceDynamo = new ResonanceDynamo("resonance-dynamo"){{
            requirements(Category.power, with(structuralAlloy, 80, recoveredCircuit, 40, resonanceCore, 10));
            size = 3;
            health = 1400;
            powerProduction = 5f;
            itemDuration = 300f;
            itemCapacity = 10;
            generateEffect = Fx.generatespark;
            effectChance = 0.06f;
            consumeItem(resonanceCore);
            drawer = new DrawMulti(new DrawDefault(), new DrawRegion("-ring"){{ rotateSpeed = 1.2f; spinSprite = true; }},
                new DrawRegion("-top"), new DrawGlowRegion(){{ color = VPal.resonance; alpha = 0.9f; }});
        }};

        containmentField = new ContainmentField("containment-field"){{
            requirements(Category.effect, with(structuralAlloy, 60, recoveredCircuit, 30, resonanceCore, 6));
            size = 2;
            health = 900;
            consumePower(1f);
            consumeItem(resonanceCore);
        }};

        worksRelay = new WorksRelay("works-relay"){{
            requirements(Category.effect, BuildVisibility.sandboxOnly, with(structuralAlloy, 200));
            size = 3;
            health = 6000;
            consumePower(8f);
        }};

        //planetary activity: how much a working building of each kind disturbs the basin
        var weights = PlanetaryActivity.weights;
        weights.put(structuralForge, 3f);
        weights.put(scrapRecycler, 2f);
        weights.put(alloyRefinery, 5f);
        weights.put(mineralCompressor, 3f);
        weights.put(combustionEngine, 3f);
        weights.put(experimentalResonator, 6f);
        weights.put(componentAssembler, 5f);
        weights.put(reinforcedDrill, 2f);
        weights.put(crystalExtractor, 4f);
        weights.put(resonanceDynamo, 7f);
        PlanetaryActivity.resonant.addAll(crystalExtractor, experimentalResonator, ancientRecoverer, resonanceDynamo);

        for(Block block : new Block[]{transitBelt, cargoRouter, cargoJunction, cargoBridge, cargoUnloader, industrialConduit, resonanceLink, surveyDrill, reinforcedDrill,
            scrapCollector, crystalExtractor, structuralForge, scrapRecycler, alloyRefinery, mineralCompressor, combustionEngine, powerAccumulator,
            experimentalResonator, componentAssembler, reinforcedWall, heavyWall, kineticTurret, pulseTurret, ancientPlatform, stabilizerPylon, surveyRig,
            resonanceDynamo, containmentField}){
            if(block.buildVisibility == BuildVisibility.shown) block.buildVisibility = vesperOnly;
        }
    }
}
