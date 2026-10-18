package vesper.harness.suites;

import arc.files.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.type.*;
import mindustry.world.*;
import vesper.*;
import vesper.harness.*;
import vesper.systems.*;
import vesper.world.*;
import vesper.world.AncientSite.*;
import vesper.world.SurveyRig.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Industrial archaeology in a purpose-built world: two Ancient Recoverer sites, each with a survey rig, power and a
 * belt to the core. Site A is surveyed (cost measured; interrupted, saved, its rig destroyed and replaced) and then
 * dismantled (saved in the middle; every salvage item counted into the core, and never again). Site B is surveyed and
 * restored (partial delivery saved), then works as the machine (measured output, activity). Choices outside their
 * state are refused, and without the map tag nothing happens. Test setup only puts circuits in the rigs and the
 * restoration materials in site B (what a belt or a unit would bring); the rest runs through the real blocks.
 */
public class ArchaeologySuite implements VesperHarness.Suite{
    static final int width = 48, height = 34;
    static final Team team = Team.sharded;
    //bottom-left tiles: site A beside the core with a belt to it; site B above, its output belt down into the core
    static final int ax = 14, ay = 4, bx = 14, by = 20, rigAx = 10, rigAy = 9, rigBx = 10, rigBy = 24;
    //the archive terminal and its rig, to the east
    static final int tx = 32, ty = 6, rigTx = 28, rigTy = 6;

    float progressBefore, levelBefore;
    int circuitsBefore, alloyStart, circuitStart, scrapStart, coresStart;
    String before = "";

    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.canGameOver = false;
            r.tags.put(AncientSite.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            //a whitelist like the scenario maps': the platform is not on it until the archive is read
            r.blockWhitelist = true;
            r.bannedBlocks.addAll(surveyRig, transitBelt, kineticTurret);
            Worlds.generate(width, height, ArchaeologySuite::generate, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            link(30, 10, rigTx, rigTy, 2);
            link(13, 9, ax, ay, 3);
            link(13, 9, rigAx, rigAy, 2);
            link(13, 25, bx, by, 3);
            link(13, 25, rigBx, rigBy, 2);
        });
        s.wait(1f);
        s.check("archaeology: enabled on this map; both sites unexplored", () -> AncientSite.enabled() && site(ax, ay).siteState == SiteState.unexplored
            && site(bx, by).siteState == SiteState.unexplored, () -> "");
        s.check("survey: each rig finds the installation in range", () -> rig(rigAx, rigAy).target() == site(ax, ay) && rig(rigBx, rigBy).target() == site(bx, by), () -> "");
        s.wait(3f);
        s.check("survey: without circuits a rig does no work", () -> site(ax, ay).investigation == 0f && rig(rigAx, rigAy).efficiency == 0f, () -> "");

        //a choice before the survey is refused
        s.run(() -> site(bx, by).configure(AncientSite.chooseDismantle));
        s.wait(0.5f);
        s.check("decision: refused before the installation is investigated", () -> site(bx, by).siteState == SiteState.unexplored, () -> "" + site(bx, by).siteState);

        // --- survey of site B, measured: power and one circuit per sample
        s.run(() -> {
            give(rig(rigBx, rigBy), recoveredCircuit, 10);
            levelBefore = Vesper.activity.level + Vesper.activity.shock;
        });
        s.until("survey: the rig reveals site B", () -> site(bx, by).siteState == SiteState.investigated, 60f, () -> "survey " + (int)(site(bx, by).investigation * 100) + "%");
        s.check("survey: it cost the circuits it announced (one per 4 s of work, 30 s)", () -> 10 - rig(rigBx, rigBy).items.get(recoveredCircuit) == SurveyRig.circuitsFor(30f * 60f),
            () -> (10 - rig(rigBx, rigBy).items.get(recoveredCircuit)) + " circuits, announced " + SurveyRig.circuitsFor(30f * 60f));
        s.check("survey: revealing it disturbs the basin by the announced 6", () -> Vesper.activity.level + Vesper.activity.shock - levelBefore >= 5.5f,
            () -> "level+surge " + Strings.fixed(levelBefore, 1) + " -> " + Strings.fixed(Vesper.activity.level + Vesper.activity.shock, 1));
        s.check("survey: the rig goes idle once nothing in range is left to survey", () -> rig(rigBx, rigBy).target() == null && !rig(rigBx, rigBy).shouldConsume(), () -> "");

        // --- survey of site A: interrupted, saved, its rig destroyed and replaced
        s.run(() -> give(rig(rigAx, rigAy), recoveredCircuit, 10));
        s.waitFor(() -> site(ax, ay).investigation >= 0.4f, 30f);
        s.run(() -> {
            unlink(13, 9, rigAx, rigAy, 2);
            progressBefore = site(ax, ay).investigation;
        });
        s.wait(3f);
        s.check("survey: interrupted (no power), the progress stays", () -> Math.abs(site(ax, ay).investigation - progressBefore) < 0.02f && site(ax, ay).siteState == SiteState.unexplored,
            () -> Strings.fixed(progressBefore * 100, 0) + "% -> " + Strings.fixed(site(ax, ay).investigation * 100, 0) + "%");
        saveLoad(h, s, "interrupted survey");
        s.check("persistence: the interrupted survey survives save and load", () -> Math.abs(site(ax, ay).investigation - progressBefore) < 0.02f, () -> Strings.fixed(site(ax, ay).investigation * 100, 0) + "%");
        s.run(() -> rig(rigAx, rigAy).kill());
        s.wait(1f);
        s.check("survey: losing the rig loses no progress (the site keeps it)", () -> Math.abs(site(ax, ay).investigation - progressBefore) < 0.02f, () -> "");
        s.run(() -> {
            world.tile(rigAx, rigAy).setBlock(surveyRig, team, 0);
            link(13, 9, rigAx, rigAy, 2);
            give(rig(rigAx, rigAy), recoveredCircuit, 10);
        });
        s.until("survey: a new rig resumes and reveals site A", () -> site(ax, ay).siteState == SiteState.investigated, 40f, () -> (int)(site(ax, ay).investigation * 100) + "%");
        saveLoad(h, s, "choice pending");
        s.check("persistence: an unconfirmed choice stays open after save and load", () -> site(ax, ay).siteState == SiteState.investigated, () -> "");

        // --- site A dismantled: finite salvage through the belt, saved in the middle, exact totals
        s.run(() -> {
            alloyStart = state.stats.coreItemCount.get(structuralAlloy);
            circuitStart = state.stats.coreItemCount.get(recoveredCircuit);
            scrapStart = state.stats.coreItemCount.get(conductiveScrap);
            site(ax, ay).configure(AncientSite.chooseDismantle);
        });
        s.wait(0.5f);
        s.check("decision: dismantling chosen", () -> site(ax, ay).siteState == SiteState.dismantling, () -> "");
        s.run(() -> site(ax, ay).configure(AncientSite.chooseRestore));
        s.wait(0.5f);
        s.check("decision: a second, different choice is refused", () -> site(ax, ay).siteState == SiteState.dismantling, () -> "");
        s.waitFor(() -> site(ax, ay).dismantle >= 0.5f, 60f);
        saveLoad(h, s, "half dismantled");
        s.check("persistence: dismantling resumes where it was", () -> site(ax, ay).siteState == SiteState.dismantling && site(ax, ay).dismantle >= 0.45f
            && site(ax, ay).dismantle < 0.6f, () -> Strings.fixed(site(ax, ay).dismantle * 100, 0) + "%");
        s.until("dismantle: the salvage is out and the husk stays", () -> site(ax, ay).siteState == SiteState.dismantled, 60f,
            () -> Strings.fixed(site(ax, ay).dismantle * 100, 0) + "%, " + site(ax, ay).salvageRemaining() + " left, store " + site(ax, ay).items.total());
        s.wait(3f);
        s.check("dismantle: exactly the announced salvage reached the core by belt (140 alloy, 50 circuits, 120 scrap)", () -> salvageDelivered(140, 50, 120),
            () -> salvageText());
        s.run(() -> {
            site(ax, ay).configure(AncientSite.chooseDismantle);
            site(ax, ay).configure(AncientSite.chooseRestore);
        });
        s.wait(5f);
        saveLoad(h, s, "dismantled");
        s.wait(3f);
        s.check("dismantle: once only: repeated choices, waiting and save/load give nothing more", () -> site(ax, ay).siteState == SiteState.dismantled
            && salvageDelivered(140, 50, 120) && !site(ax, ay).shouldConsume(), () -> salvageText());

        // --- site B restored: partial delivery saved, then the machine works
        s.run(() -> site(bx, by).configure(AncientSite.chooseRestore));
        s.wait(0.5f);
        s.check("decision: restoration chosen", () -> site(bx, by).siteState == SiteState.restoring, () -> "");
        s.run(() -> deliver(site(bx, by), structuralAlloy, 60));
        s.wait(0.5f);
        saveLoad(h, s, "half restored");
        s.check("persistence: a partial restoration keeps what was delivered", () -> site(bx, by).siteState == SiteState.restoring && site(bx, by).delivered[0] == 60,
            () -> "delivered " + new IntSeq(site(bx, by).delivered));
        s.run(() -> {
            levelBefore = Vesper.activity.level + Vesper.activity.shock;
            //more than it needs of each: the surplus must be refused
            for(ItemStack stack : ((AncientSite)ancientRecoverer).restoration) deliver(site(bx, by), stack.item, stack.amount + 20);
        });
        s.wait(0.5f);
        s.check("restore: complete with exactly the restoration cost (surplus refused)", () -> site(bx, by).siteState == SiteState.restored
            && new IntSeq(site(bx, by).delivered).equals(restorationCost()), () -> site(bx, by).siteState + " delivered " + new IntSeq(site(bx, by).delivered));
        s.check("restore: waking it disturbs the basin by the announced 12", () -> Vesper.activity.level + Vesper.activity.shock - levelBefore >= 11.5f,
            () -> Strings.fixed(levelBefore, 1) + " -> " + Strings.fixed(Vesper.activity.level + Vesper.activity.shock, 1));
        s.run(() -> {
            world.build(17, 20).configureAny(conductiveScrap);
            world.build(17, 22).configureAny(resonanceCrystal);
            coresStart = state.stats.coreItemCount.get(resonanceCore);
        });
        s.wait(60f);
        s.check("machine: the restored recoverer makes resonance cores from scrap and crystal (about 1 per 3 s at low activity)",
            () -> near(state.stats.coreItemCount.get(resonanceCore) - coresStart, 20f * Vesper.activity.resonanceMultiplier(), 0.2f),
            () -> (state.stats.coreItemCount.get(resonanceCore) - coresStart) + " cores in 60 s, multiplier x" + Strings.fixed(Vesper.activity.resonanceMultiplier(), 2));
        s.check("machine: it adds its activity while working", () -> Vesper.activity.ancient >= 7.5f, () -> "ancient " + Strings.fixed(Vesper.activity.ancient, 1));
        s.run(() -> site(bx, by).configure(AncientSite.chooseDismantle));
        s.wait(1f);
        s.check("decision: a restored machine cannot be dismantled afterwards", () -> site(bx, by).siteState == SiteState.restored, () -> "");
        saveLoad(h, s, "restored");
        s.run(() -> coresStart = state.stats.coreItemCount.get(resonanceCore));
        s.wait(10f);
        s.check("persistence: the restored machine works on after save and load", () -> site(bx, by).siteState == SiteState.restored
            && state.stats.coreItemCount.get(resonanceCore) - coresStart >= 2, () -> (state.stats.coreItemCount.get(resonanceCore) - coresStart) + " cores in 10 s");

        // --- the archive terminal: surveying it unlocks the Ancient Platform on this map only
        s.check("archive: the Ancient Platform cannot be built before the archive is read", () -> state.rules.isBanned(ancientPlatform)
            && site2(tx, ty).siteState == SiteState.unexplored, () -> "");
        s.run(() -> {
            circuitsBefore = give(rig(rigTx, rigTy), recoveredCircuit, 10);
            levelBefore = Vesper.activity.level + Vesper.activity.shock;
        });
        s.waitFor(() -> rig(rigTx, rigTy).items.get(recoveredCircuit) < 3, 60f);
        s.run(() -> circuitsBefore += give(rig(rigTx, rigTy), recoveredCircuit, 10));
        s.until("archive: the rig reads the archive (60 s of survey)", () -> site2(tx, ty).siteState == SiteState.investigated, 60f,
            () -> (int)(site2(tx, ty).investigation * 100) + "%");
        s.check("archive: it cost the announced circuits (15) and disturbed the basin by 15", () -> circuitsBefore - rig(rigTx, rigTy).items.get(recoveredCircuit) == SurveyRig.circuitsFor(60f * 60f)
            && Vesper.activity.level + Vesper.activity.shock - levelBefore >= 14.5f, () -> (circuitsBefore - rig(rigTx, rigTy).items.get(recoveredCircuit)) + " circuits");
        s.check("archive: the Ancient Platform is now buildable here", () -> !state.rules.isBanned(ancientPlatform), () -> "");
        s.check("archive: an archive offers no restore or dismantle", () -> {
            site2(tx, ty).configure(AncientSite.chooseDismantle);
            return site2(tx, ty).siteState == SiteState.investigated;
        }, () -> "");
        saveLoad(h, s, "archive read");
        s.check("persistence: the unlock is saved with the map", () -> !state.rules.isBanned(ancientPlatform) && site2(tx, ty).siteState == SiteState.investigated, () -> "");
        s.run(() -> Worlds.play(Worlds.bundled(ContentSuite.basinName), Gamemode.survival));
        s.wait(1f);
        s.check("archive: other maps keep their own whitelist (First Awakening still has no platform)", () -> state.rules.isBanned(ancientPlatform), () -> "");

        // --- without the map tag, nothing happens
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.canGameOver = false;
            Worlds.generate(width, height, ArchaeologySuite::generate, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            link(13, 9, ax, ay, 3);
            link(13, 9, rigAx, rigAy, 2);
            give(rig(rigAx, rigAy), recoveredCircuit, 10);
            site(ax, ay).configure(AncientSite.chooseDismantle);
        });
        s.wait(5f);
        s.check("outside archaeology maps: no survey, no choice, the rig draws nothing", () -> !AncientSite.enabled() && site(ax, ay).investigation == 0f
            && site(ax, ay).siteState == SiteState.unexplored && !rig(rigAx, rigAy).shouldConsume() && rig(rigAx, rigAy).items.get(recoveredCircuit) == 10, () -> "");

        // --- performance with many installations: a large excavation, every site and rig updated in one frame
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.canGameOver = false;
            r.tags.put(AncientSite.tagEnabled, "true");
            r.tags.put(PlanetaryActivity.tagEnabled, "true");
            //every consumer satisfied without a power grid: the machines run their full update
            r.teams.get(team).cheat = true;
            Worlds.generate(manyWidth, manyHeight, ArchaeologySuite::generateMany, r);
        });
        s.wait(0.5f);
        s.run(() -> {
            //a third surveyed by rigs, a third dismantling, a third restored and fed (test setup)
            int k = 0;
            for(Building b : team.data().buildings.copy()){
                if(!(b instanceof SiteBuild site)) continue;
                switch(k++ % 3){
                    case 1 -> {
                        site.siteState = SiteState.investigated;
                        site.choose(AncientSite.chooseDismantle);
                    }
                    case 2 -> {
                        site.siteState = SiteState.restored;
                        for(ItemStack stack : ((AncientSite)site.block).recipe) give(site, stack.item, 20);
                    }
                    default -> {}
                }
            }
            team.data().buildings.each(b -> b instanceof SurveyRigBuild, b -> give(b, recoveredCircuit, 10));
        });
        s.wait(2f);
        s.run(() -> {
            Seq<Building> machines = team.data().buildings.select(b -> b instanceof SiteBuild || b instanceof SurveyRigBuild);
            Seq<Building> rigs = machines.select(b -> b instanceof SurveyRigBuild);
            int sites = machines.size - rigs.size, buildings = team.data().buildings.size;
            float frame = millis(() -> machines.each(Building::update), 200);
            float search = -1f;
            try{
                var find = SurveyRigBuild.class.getDeclaredMethod("findTarget");
                find.setAccessible(true);
                search = millis(() -> rigs.each(r -> {
                    try{
                        find.invoke(r);
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                }), 200) / rigs.size;
            }catch(Exception e){
                Log.err(e);
            }
            h.line("info: performance: " + sites + " sites and " + rigs.size + " rigs among " + buildings + " buildings: one frame of their updates "
                + Strings.fixed(frame * 1000f, 0) + " us, a rig's search for its site " + Strings.fixed(search * 1000f, 1) + " us (once per second per rig)");
            h.check("performance: " + sites + " sites and " + rigs.size + " rigs update in under 1 ms per frame", frame >= 0f && frame < 1f, Strings.fixed(frame, 3) + " ms");
            h.check("performance: a rig finds its site in under 100 us among " + buildings + " buildings", search >= 0f && search < 0.1f, Strings.fixed(search * 1000f, 1) + " us");
            h.check("performance: the rigs found their sites", rigs.allMatch(r -> ((SurveyRigBuild)r).target() != null || site(r) != null), "");
        });
    }

    static final int manyWidth = 104, manyHeight = 74;

    /** Twelve by eight cells: an Ancient Recoverer each, a survey rig in every third, a row of walls (a crowded base). */
    static void generateMany(Tiles tiles){
        for(int x = 0; x < manyWidth; x++){
            for(int y = 0; y < manyHeight; y++){
                boolean border = x == 0 || y == 0 || x == manyWidth - 1 || y == manyHeight - 1;
                tiles.set(x, y, new Tile(x, y, machineBasalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        for(int i = 0; i < 12; i++){
            for(int j = 0; j < 8; j++){
                int cx = 4 + i * 8, cy = 4 + j * 8;
                place(tiles, ancientRecoverer, cx, cy, 0);
                if((i * 8 + j) % 3 == 0) place(tiles, surveyRig, cx + 4, cy, 0);
                for(int x = cx; x <= cx + 6; x++) place(tiles, reinforcedWall, x, cy + 5, 0);
            }
        }
    }

    /** The site a rig stands next to (its own cell), once it is no longer unexplored. */
    static @Nullable SiteBuild site(Building rig){
        return Worlds.at(rig.tile.x - 4, rig.tile.y, 3) instanceof SiteBuild b && b.siteState != SiteState.unexplored ? b : null;
    }

    /** Average time of one run, in milliseconds. */
    static float millis(Runnable run, int runs){
        for(int i = 0; i < 10; i++) run.run();
        long start = System.nanoTime();
        for(int i = 0; i < runs; i++) run.run();
        return (System.nanoTime() - start) / 1e6f / runs;
    }

    static void generate(Tiles tiles){
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++){
                boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                tiles.set(x, y, new Tile(x, y, machineBasalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        place(tiles, coreFoothold, 4, 4, 0);
        place(tiles, ancientRecoverer, ax, ay, 0);
        place(tiles, ancientRecoverer, bx, by, 0);
        place(tiles, surveyRig, rigAx, rigAy, 0);
        place(tiles, surveyRig, rigBx, rigBy, 0);
        place(tiles, Blocks.powerSource, 13, 9, 0);
        place(tiles, Blocks.powerSource, 13, 25, 0);
        place(tiles, archiveTerminal, tx, ty, 0);
        place(tiles, surveyRig, rigTx, rigTy, 0);
        place(tiles, Blocks.powerSource, 30, 10, 0);
        place(tiles, Blocks.itemSource, 17, 20, 0);
        place(tiles, Blocks.itemSource, 17, 22, 0);
        //site A's salvage belt west into the core; site B's output west, then down into the core's top
        for(int x = 7; x <= 13; x++) place(tiles, transitBelt, x, 5, 2);
        for(int x = 6; x <= 13; x++) place(tiles, transitBelt, x, 21, 2);
        for(int y = 7; y <= 21; y++) place(tiles, transitBelt, 5, y, 3);
    }

    static void place(Tiles tiles, Block block, int left, int bottom, int rotation){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, rotation);
    }

    static void link(int nodeX, int nodeY, int left, int bottom, int size){
        Building node = world.build(nodeX, nodeY), to = Worlds.at(left, bottom, size);
        if(node != null && to != null && !node.power.links.contains(to.pos())) node.configure(to.pos());
    }

    static void unlink(int nodeX, int nodeY, int left, int bottom, int size){
        Building node = world.build(nodeX, nodeY), to = Worlds.at(left, bottom, size);
        if(node != null && to != null && node.power.links.contains(to.pos())) node.configure(to.pos());
    }

    static SiteBuild site(int left, int bottom){
        return (SiteBuild)Worlds.at(left, bottom, 3);
    }

    static SiteBuild site2(int left, int bottom){
        return (SiteBuild)Worlds.at(left, bottom, 2);
    }

    static SurveyRigBuild rig(int left, int bottom){
        return Worlds.at(left, bottom, 2) instanceof SurveyRigBuild r ? r : null;
    }

    /** What a belt or a unit would bring: through the block's own acceptance. */
    static int give(Building build, Item item, int amount){
        int accepted = build.acceptStack(item, amount, null);
        build.handleStack(item, accepted, null);
        return accepted;
    }

    static void deliver(SiteBuild site, Item item, int amount){
        give(site, item, amount);
    }

    boolean salvageDelivered(int alloy, int circuits, int scrap){
        return state.stats.coreItemCount.get(structuralAlloy) - alloyStart == alloy && state.stats.coreItemCount.get(recoveredCircuit) - circuitStart == circuits
            && state.stats.coreItemCount.get(conductiveScrap) - scrapStart == scrap;
    }

    String salvageText(){
        return (state.stats.coreItemCount.get(structuralAlloy) - alloyStart) + " alloy, " + (state.stats.coreItemCount.get(recoveredCircuit) - circuitStart)
            + " circuits, " + (state.stats.coreItemCount.get(conductiveScrap) - scrapStart) + " scrap";
    }

    static boolean near(float value, float target, float fraction){
        return Math.abs(value - target) <= target * fraction;
    }

    void saveLoad(VesperHarness h, Script s, String what){
        s.run(() -> {
            before = snapshot();
            salvageAtSave.clear();
            for(int[] p : new int[][]{{ax, ay}, {bx, by}}) salvageAtSave.add(salvageHeld(site(p[0], p[1])));
            Fi file = h.out.child("archaeology-" + what.replace(' ', '-') + ".msav");
            SaveIO.save(file);
            logic.reset();
            SaveIO.load(file);
            state.set(State.playing);
            h.line("saved and loaded (" + what + "): " + before);
        });
        s.wait(0.5f);
        //states, surveys and deliveries exactly; a salvage pool may only shrink (dismantling goes on while the check waits), never grow back
        s.check("persistence: site states identical after save and load (" + what + ")", () -> snapshot().equals(before) && salvageNeverGrew(), () -> "after: " + snapshot() + " salvage " + salvageList());
    }

    String snapshot(){
        StringBuilder out = new StringBuilder();
        for(int[] p : new int[][]{{ax, ay}, {bx, by}}){
            var b = site(p[0], p[1]);
            out.append(b.siteState).append(' ').append((int)(b.investigation * 100)).append("% delivered ").append(new IntSeq(b.delivered)).append(" | ");
        }
        return out.toString();
    }

    final IntSeq salvageAtSave = new IntSeq();

    /** The Ancient Recoverer's restoration amounts, in order. */
    static IntSeq restorationCost(){
        IntSeq out = new IntSeq();
        for(ItemStack stack : ((AncientSite)ancientRecoverer).restoration) out.add(stack.amount);
        return out;
    }

    /** Salvage a site still holds: its pool, plus its store while dismantling (a restored machine's store holds its recipe, not salvage). */
    static int salvageHeld(SiteBuild b){
        return b.salvageRemaining() + (b.siteState == SiteState.dismantling ? b.items.total() : 0);
    }

    String salvageList(){
        IntSeq now = new IntSeq();
        for(int[] p : new int[][]{{ax, ay}, {bx, by}}) now.add(salvageHeld(site(p[0], p[1])));
        return now.toString();
    }

    boolean salvageNeverGrew(){
        for(int i = 0; i < salvageAtSave.size; i++){
            int[] p = i == 0 ? new int[]{ax, ay} : new int[]{bx, by};
            if(salvageHeld(site(p[0], p[1])) > salvageAtSave.get(i)) return false;
        }
        return true;
    }
}
