package vesper.harness.suites;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.content.TechTree.*;
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.Objectives.*;
import mindustry.io.*;
import mindustry.maps.Map;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;
import vesper.campaign.*;
import vesper.content.*;
import vesper.harness.*;
import vesper.mission.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * The campaign as content, without playing it (also on the dedicated server): the planet and its fixed sectors,
 * the shipped maps behind them, and the tech tree's rules: what each sector needs is available when it opens,
 * research costs are not circular, and Serpulo and Erekir are untouched.
 */
public class TreeSuite implements VesperHarness.Suite{

    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> {
            Planet p = VPlanets.vesper;
            h.check("planet: VESPER is listed (always unlocked) and orbits the sun", p != null && p.alwaysUnlocked && p.accessible && p.visible && p.parent == Planets.sun
                && p.hasGrid(), p == null ? "missing" : p.name);
            h.check("planet: only its preset sectors can be landed on", !p.allowLaunchToNumbered && p.sectors.count(sec -> sec.preset != null) == 4
                && p.sectors.size == 92, p.sectors.count(sec -> sec.preset != null) + " presets of " + p.sectors.size + " sectors");
            h.check("planet: maps keep their own buildings, lighting and cores (no prebuild, no day cycle, fresh map after a loss)", !p.prebuildBase && !p.updateLighting
                && p.clearSectorOnLose && p.defaultCore == coreFoothold && !p.allowLaunchLoadout && !p.allowLaunchSchematics, "");
            //blocks get every planet their items are on (Block.postInit, native); the tree must not narrow that to VESPER
            h.check("planet: its tree does not restrict Vesper content to it (custom maps say Serpulo)", !p.autoAssignPlanet && surveyDrill.isOnPlanet(Planets.serpulo)
                && surveyDrill.isOnPlanet(p) && ancientPlatform.isOnPlanet(Planets.serpulo) && ferroprism.isOnPlanet(Planets.serpulo), "shown on " + surveyDrill.shownPlanets);

            //sectors: fixed ids, the shipped maps, their rules left alone
            Object[][] sectors = {{VSectors.firstAwakening, 12, "first-awakening", FirstAwakening.scenarioId}, {VSectors.resonanceFault, 77, "resonance-fault", ResonanceFault.scenarioId},
                {VSectors.buriedFoundry, 27, "buried-foundry", BuriedFoundry.scenarioId}};
            for(Object[] row : sectors){
                SectorPreset preset = (SectorPreset)row[0];
                String file = (String)row[2];
                Map map = preset.generator.map;
                Rules rules = new Rules();
                rules.winWave = 0;
                preset.rules.get(rules);
                Map real = shipped(file);
                h.check("sector " + preset.name + ": fixed id, the shipped map, rules untouched", preset.sector.id == (int)row[1] && preset.planet == p && map != null
                    && map.file.name().equals(file + "." + mapExtension) && row[3].equals(map.rules().tags.get(FirstAwakening.tagScenario)) && rules.winWave == 0
                    && preset.captureWave == -1 && Chapter.sectors.contains(preset), "id " + preset.sector.id + ", map " + (map == null ? "-" : map.file.name()));
                h.check("sector " + preset.name + ": the launch charges nothing, the map still lands with its loadout", map != null && map.rules().loadout.isEmpty()
                    && real != null && real.rules().loadout.size > 0, real == null ? "" : "loadout " + real.rules().loadout);
                h.check("sector " + preset.name + ": not shown as an enemy base", !preset.sector.hasEnemyBase(), "");
            }
            h.check("sectors: First Awakening, Resonance Fault and The Buried Foundry are neighbours in that order", VSectors.firstAwakening.sector.isNear(VSectors.resonanceFault.sector)
                && VSectors.resonanceFault.sector.isNear(VSectors.buriedFoundry.sector) && VSectors.buriedFoundry.sector.isNear(VSectors.deepSignal.sector)
                && !VSectors.firstAwakening.sector.isNear(VSectors.buriedFoundry.sector), "");
            h.check("sectors: the deep region is a locked marker, never playable", VSectors.deepSignal.sector.id == 42 && VSectors.deepSignal.requireUnlock
                && !Chapter.sectors.contains(VSectors.deepSignal) && VSectors.deepSignal.techNode.objectives.contains(o -> o instanceof ChapterObjective c && !c.complete()), "");

            //the tree
            TechNode root = VTechTree.root;
            Seq<TechNode> nodes = new Seq<>();
            root.each(nodes::add);
            h.check("tree: VESPER's own root, on the planet", p.techTree == root && TechTree.roots.contains(root) && nodes.allMatch(n -> n.planet == p), nodes.size + " nodes");
            h.check("tree: only Vesper content in it", nodes.allMatch(n -> n.content.minfo.mod != null && n.content.minfo.mod.name.equals("vesper")), "");
            h.check("tree: Serpulo's and Erekir's trees hold no Vesper content", !hasVesper(Planets.serpulo.techTree) && !hasVesper(Planets.erekir.techTree), "");
            h.check("tree: every buildable Vesper block of the three maps is in it", missingFromTree().isEmpty(), "missing " + missingFromTree());

            //what First Awakening is built with: free, no objective, unlocked from the start
            Block[] start = {transitBelt, cargoRouter, cargoJunction, resonanceLink, surveyDrill, scrapCollector, structuralForge, componentAssembler, combustionEngine,
                kineticTurret, reinforcedWall};
            h.check("tree: First Awakening's blocks are free from the start (no cost, no objective)", Seq.with(start).allMatch(b ->
                b.techNode.requirements.length == 0 && b.techNode.objectives.isEmpty()), "");
            //what Resonance Fault and The Buried Foundry need opens with them, for free
            h.check("tree: Resonance Fault's blocks open with it (free once First Awakening is secured)", Seq.with(crystalExtractor, stabilizerPylon, pulseTurret, experimentalResonator)
                .allMatch(b -> b.techNode.requirements.length == 0 && waitsOn(b.techNode, VSectors.firstAwakening)), "");
            h.check("tree: the Survey Rig opens with The Buried Foundry (free once Resonance Fault is secured)", surveyRig.techNode.requirements.length == 0
                && waitsOn(surveyRig.techNode, VSectors.resonanceFault) && waitsOn(VSectors.buriedFoundry.techNode, VSectors.resonanceFault)
                && waitsOn(VSectors.resonanceFault.techNode, VSectors.firstAwakening), "");
            h.check("tree: the Ancient Platform waits on the archive only (not on a choice, not on resonance cores)", ancientPlatform.techNode.requirements.length == 0
                && ancientPlatform.techNode.objectives.contains(o -> o instanceof ChapterObjective) && !ancientPlatform.techNode.objectives.contains(o -> o instanceof Research r && r.content == resonanceCore), "");
            h.check("tree: the resonance industry opens once The Buried Foundry is secured (free, not waiting on a first resonance core)", Seq.with(resonanceDynamo, containmentField)
                .allMatch(b -> b.techNode != null && b.techNode.requirements.length == 0 && waitsOn(b.techNode, VSectors.buriedFoundry)
                && !b.techNode.objectives.contains(o -> o instanceof Research r && r.content == resonanceCore)) && containmentField.techNode.parent == resonanceDynamo.techNode, "");
            h.check("tree: the three Chapter One maps do not allow the resonance industry (their block lists are unchanged)", Seq.with("first-awakening", "resonance-fault", "buried-foundry")
                .allMatch(f -> shipped(f) != null && !allows(shipped(f), resonanceDynamo) && !allows(shipped(f), containmentField)), "");
            //research costs: only what First Awakening's own blocks produce, and nothing a node needs comes after it
            ObjectSet<Item> early = ObjectSet.with(ferroprism, conductiveScrap, vitreousCarbon, structuralAlloy, recoveredCircuit);
            Seq<TechNode> paid = nodes.select(n -> n.requirements.length > 0);
            h.check("tree: research costs use only items First Awakening produces", paid.size == 6 && paid.allMatch(n -> Structs.contains(n.requirements, st -> early.contains(st.item))
                && !Structs.contains(n.requirements, st -> !early.contains(st.item))), paid.size + " researched nodes: " + paid.map(n -> n.content.name));
            h.check("tree: no mission needs a researched node", paid.allMatch(n -> !Seq.with(start).contains((Block)n.content) && n.content != crystalExtractor && n.content != surveyRig), "");
            h.check("tree: no node waits on something below itself (no circular requirement)", nodes.allMatch(n -> !n.objectives.contains(o -> o instanceof Research r
                && r.content.techNode != null && below(r.content.techNode, n))), "");

            //the blocks stay out of other planets' campaigns, never out of custom games
            h.check("visibility: buildable Vesper blocks use the Vesper-only visibility", Seq.with(start).allMatch(b -> b.buildVisibility == VBlocks.vesperOnly)
                && surveyRig.buildVisibility == VBlocks.vesperOnly && ancientPlatform.buildVisibility == VBlocks.vesperOnly && coreFoothold.buildVisibility == BuildVisibility.editorOnly
                && resonanceDynamo.buildVisibility == VBlocks.vesperOnly && containmentField.buildVisibility == VBlocks.vesperOnly, "");

            //texts
            Fi bundles = mods.getMod("vesper").root.child("bundles");
            ObjectSet<String> en = ContentSuite.keys(bundles.child("bundle.properties"));
            Seq<String> used = Seq.with("planet.vesper-planet.name", "planet.vesper-planet.description", "techtree.vesper", "vesper.deep.locked", "vesper.record.announce",
                "vesper.secured.title", "vesper.secured.available", "vesper.secured.next", "vesper.chapter.title", "vesper.chapter.subtitle", "vesper.chapter.text",
                "vesper.chapter.next", "vesper.archive", "vesper.archive.title", "vesper.archive.unknown", "vesper.intro.title", "vesper.intro.text", "vesper.intro.go",
                "vesper.intro.later", "vesper.requirement.archive", "vesper.requirement.unavailable");
            for(SectorPreset preset : new SectorPreset[]{VSectors.firstAwakening, VSectors.resonanceFault, VSectors.buriedFoundry, VSectors.deepSignal}){
                used.addAll("sector." + preset.name + ".name", "sector." + preset.name + ".description");
            }
            for(var r : Chapter.Record.all) used.addAll("vesper.record." + r.id + ".title", "vesper.record." + r.id + ".text");
            Seq<String> missing = used.select(k -> !en.contains(k));
            h.check("texts: every campaign text exists", missing.isEmpty(), used.size + " keys, missing: " + missing);
        });
    }

    /** The map file as shipped in the mod (a fresh read of its own rules, not the copy a preset keeps). */
    static Map shipped(String file){
        try{
            return MapIO.createMap(mods.getMod("vesper").root.child("maps").child(file + "." + mapExtension), false);
        }catch(Exception e){
            return null;
        }
    }

    /** Whether a map's rules let a block be built (its list is a whitelist or a blacklist). */
    static boolean allows(Map map, Block block){
        var rules = map.rules();
        return rules.blockWhitelist == rules.bannedBlocks.contains(block);
    }

    static boolean hasVesper(TechNode root){
        if(root == null) return false;
        boolean[] found = {false};
        root.each(n -> {
            if(n.content.minfo.mod != null && n.content.minfo.mod.name.equals("vesper")) found[0] = true;
        });
        return found[0];
    }

    static boolean waitsOn(TechNode node, SectorPreset preset){
        return node.objectives.contains(o -> o instanceof SectorComplete c && c.preset == preset);
    }

    /** Whether node is a descendant of parent (or parent itself). */
    static boolean below(TechNode node, TechNode parent){
        for(TechNode n = node; n != null; n = n.parent) if(n == parent) return true;
        return false;
    }

    /** Blocks allowed by one of the three maps (whitelists) without a node in the tree. */
    static Seq<String> missingFromTree(){
        Seq<String> out = new Seq<>();
        for(String file : new String[]{"first-awakening", "resonance-fault", "buried-foundry"}){
            Map map = shipped(file);
            if(map == null) continue;
            for(Block b : map.rules().bannedBlocks){
                if(b.techNode == null && !out.contains(b.name)) out.add(b.name);
            }
        }
        //added on the map once its archive is read
        if(ancientPlatform.techNode == null) out.add(ancientPlatform.name);
        return out;
    }
}
