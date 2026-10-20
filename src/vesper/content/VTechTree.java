package vesper.content;

import arc.struct.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.game.Objectives.*;
import mindustry.type.*;
import vesper.campaign.*;

import static mindustry.content.TechTree.*;
import static mindustry.type.ItemStack.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * VESPER's own tech tree, apart from Serpulo's and Erekir's (their trees are not touched). Its branches follow the
 * chapter's sectors:
 * <ul>
 * <li>industrial foundations, what First Awakening is built with: available from the start;</li>
 * <li>resonance (crystal, stabilizers, resonator, pulse turret): securing First Awakening makes it available, so
 * Resonance Fault opens with everything it is designed around;</li>
 * <li>recovered technology: the Survey Rig from securing Resonance Fault, so The Buried Foundry opens with it, and
 * the Ancient Platform once its archive is read in the campaign (the decision to restore or dismantle the
 * buried machines does not matter for it); the resonance industry (Resonance Dynamo, Containment Field) once The
 * Buried Foundry is secured, with no research cost and no wait for a first resonance core (building one still
 * takes them);</li>
 * <li>upgrades researched with materials (paid from the player's sectors, the ones already secured first), none
 * needed by a mission.</li>
 * </ul>
 * The sectors are nodes too: each one waits on the capture of the one before, and the deep region on a
 * requirement this version never meets.
 * Item dependencies stay where they read as discoveries (the pulse turret waits for the first crystal); research
 * costs only use items the foundations already produce, so nothing is circular.
 */
public class VTechTree{
    public static TechNode root;

    public static void load(){
        root = nodeRoot("vesper", coreFoothold, () -> {
            node(VSectors.firstAwakening, () -> node(VSectors.resonanceFault, () -> node(VSectors.buriedFoundry, () -> {
                //the region below opens once Chapter One is complete (in a new profile, or one that finished it in an older version:
                //the native unlock check runs at every start and after every capture)
                node(VSectors.deepSignal, Seq.with(new ChapterObjective("chapter-one", Chapter::complete)), () -> {});
            })));

            //industrial foundations: everything First Awakening is built with; the upgrades are researched
            start(transitBelt, () -> start(cargoRouter, () -> {
                start(cargoJunction, () -> node(cargoBridge, with(ferroprism, 120, conductiveScrap, 60), () -> {}));
                //the unloader feeds the resonance industry from stored cores: it opens with it, once The Buried Foundry is secured
                //(no Chapter One map allows it, so it changes none of their missions)
                TechNode unloader = secured(cargoUnloader, VSectors.buriedFoundry, () -> {});
                unloader.objectives.removeAll(o -> o instanceof Research r && r.content == resonanceCore);
            }));
            start(surveyDrill, () -> {
                start(scrapCollector, () -> {});
                node(reinforcedDrill, with(ferroprism, 200, structuralAlloy, 50), () -> {});
            });
            start(structuralForge, () -> {
                start(componentAssembler, () -> {});
                node(scrapRecycler, with(ferroprism, 150, conductiveScrap, 100), () -> {});
                node(alloyRefinery, with(ferroprism, 250, structuralAlloy, 60, recoveredCircuit, 20), () -> {});
            });
            start(combustionEngine, () -> start(resonanceLink, () -> node(powerAccumulator, with(ferroprism, 100, conductiveScrap, 150), () -> {})));
            start(kineticTurret, () -> start(reinforcedWall, () -> node(heavyWall, with(structuralAlloy, 80), () -> {})));

            //resonance: what Resonance Fault is designed around, from securing First Awakening
            secured(crystalExtractor, VSectors.firstAwakening, () -> {
                secured(stabilizerPylon, VSectors.firstAwakening, () -> {});
                secured(pulseTurret, VSectors.firstAwakening, () -> {});
                secured(experimentalResonator, VSectors.firstAwakening, () -> {});
            });

            //recovered technology: the rig The Buried Foundry is surveyed with, and what its archive gives back
            secured(surveyRig, VSectors.resonanceFault, () -> {
                TechNode platform = node(ancientPlatform, empty, Seq.with(new ChapterObjective("archive", Chapter::archiveRead)), () -> {});
                //recovered once the archive is read, like on the map itself (building one still takes resonance cores)
                platform.objectives.removeAll(o -> o instanceof Research r && r.content == resonanceCore);

                //resonance industry: what the recovered foundry teaches about burning and containing cores, once it is secured
                TechNode dynamo = secured(resonanceDynamo, VSectors.buriedFoundry, () -> {
                    TechNode field = secured(containmentField, VSectors.buriedFoundry, () -> {});
                    field.objectives.removeAll(o -> o instanceof Research r && r.content == resonanceCore);
                });
                dynamo.objectives.removeAll(o -> o instanceof Research r && r.content == resonanceCore);
            });

            nodeProduce(ferroprism, () -> {
                nodeProduce(conductiveScrap, () -> {});
                nodeProduce(vitreousCarbon, () -> nodeProduce(structuralAlloy, () -> nodeProduce(recoveredCircuit, () -> {})));
            });
            nodeProduce(resonanceCrystal, () -> nodeProduce(resonanceCore, () -> {}));
        });

        //the foothold core is the tree's root, never researched (it would otherwise cost what building one costs)
        root.setupRequirements(empty);
        root.objectives.clear();
        root.each(node -> node.planet = VPlanets.vesper);
        root.addDatabaseTab(VPlanets.vesper);
        VPlanets.vesper.techTree = root;
    }

    /**
     * Available from the start: no research cost and no objective, so the native check unlocks it at once. Item
     * dependencies are dropped too: the forge would otherwise wait for the first carbon while the mission asks for it.
     */
    static TechNode start(UnlockableContent content, Runnable children){
        TechNode node = node(content, empty, children);
        node.objectives.clear();
        return node;
    }

    /** Free once a sector is secured (the native capture; the native check unlocks it then). */
    static TechNode secured(UnlockableContent content, SectorPreset sector, Runnable children){
        return node(content, empty, Seq.with(new SectorComplete(sector)), children);
    }
}
