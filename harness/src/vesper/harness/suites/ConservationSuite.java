package vesper.harness.suites;

import arc.files.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.io.*;
import mindustry.world.*;
import mindustry.world.blocks.production.GenericCrafter.*;
import vesper.harness.*;
import vesper.world.ContainmentLock.*;
import vesper.world.ProtocolController.*;

import static mindustry.Vars.*;
import static vesper.content.VBlocks.*;
import static vesper.content.VItems.*;

/**
 * Resonance cores are conserved and the {@link Ledger} proves it, in a purpose-built world: a resonator (its crystal
 * and circuits fed by the test, powered by a dynamo beside it, which also burns some of what it makes) sends cores
 * down a belt to a router and a container. The 0.8 audit reported one core too many in a playthrough: production is
 * seen a frame after it happens while the stock is read live, so a report read right after a craft was one core
 * behind. The first check reads on every frame before the ledger's own pass, as the scripts do: the raw balance must
 * catch that case (or this test proves nothing) and the settled balance must never be off. Then the other ways out:
 * a belt destroyed with cores on it, a unit killed carrying cores, save and load, and a lock that cannot pay.
 * <p>
 * Test fixtures: the resonator's inputs and the dynamo's first cores are added by the test, and the controller's cores too.
 */
public class ConservationSuite implements VesperHarness.Suite{
    static final Team team = Team.sharded;
    static final int width = 40, height = 24;
    static final int resonatorX = 10, resonatorY = 10, dynamoX = 7, dynamoY = 10, routerX = 20, y = 11, containerX = 21;
    final Ledger ledger = new Ledger();
    int rawOff, settledOff, reads, added, heldBefore;

    GenericCrafterBuild resonator(){
        return (GenericCrafterBuild)Worlds.at(resonatorX, resonatorY, 3);
    }

    /** Every core the world has been given: at the start, added since, or made. */
    int accounted(){
        return Ledger.held(resonanceCore) + Ledger.coresBurned() + ledger.coresLost;
    }

    @Override
    public void define(VesperHarness h, Script s){
        s.run(() -> {
            rawOff = settledOff = reads = added = 0;
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.loadout.clear();
            Worlds.generate(width, height, ConservationSuite::generate, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            ledger.begin();
            //the first cores of the world: they pay for the dynamo's first power, and count as made nowhere, so the ledger starts with them
            var dynamo = Worlds.at(dynamoX, dynamoY, 3);
            dynamo.handleStack(resonanceCore, 10, null);
            ledger.start.put(resonanceCore, ledger.start.get(resonanceCore, 0) + 10);
        });
        //every frame, before the ledger's own pass: the raw balance as a script found it, then the settled one
        s.step(d -> {
            var res = resonator();
            if(res.items.get(resonanceCrystal) < 4) res.items.add(resonanceCrystal, 8);
            if(res.items.get(recoveredCircuit) < 2) res.items.add(recoveredCircuit, 4);
            reads++;
            if(ledger.rawResidual() != 0) rawOff++;
            if(ledger.residual() != 0) settledOff++;
            return ledger.made.get(resonanceCore, 0) >= 40 || reads > 4000;
        });
        s.check("conservation: the resonator made 40 cores on the belt and the dynamo burned some", () -> ledger.made.get(resonanceCore, 0) >= 40 && Ledger.coresBurned() > 0,
            () -> ledger.report());
        s.check("conservation: reading right after a craft is the case the audit found: without settling, a frame comes up one core behind", () -> rawOff > 0,
            () -> rawOff + " of " + reads + " raw readings off");
        s.check("conservation: settled first, every reading on every frame balances", () -> settledOff == 0 && ledger.offBalance == 0, () -> settledOff + " of " + reads + " readings off; " + ledger.balance());

        //a belt destroyed with cores on it: they are lost, and the balance says so
        s.waitFor(() -> belt() != null && belt().items.get(resonanceCore) > 0, 10f);
        s.run(() -> {
            added = belt().items.get(resonanceCore);
            belt().kill();
        });
        s.wait(0.1f);
        s.check("conservation: a belt destroyed with cores on it: they are counted as lost, nothing else moves", () -> added > 0 && ledger.coresLost == added && ledger.residual() == 0,
            () -> added + " on the belt; " + ledger.report());

        //a unit carrying cores: held while it lives, lost when it is killed
        s.run(() -> {
            added = ledger.coresLost;
            Unit u = UnitTypes.poly.spawn(team, (containerX + 2) * tilesize, y * tilesize);
            int taken = container().items.get(resonanceCore);
            int stack = Math.min(taken, u.type.itemCapacity);
            container().items.remove(resonanceCore, stack);
            u.addItem(resonanceCore, stack);
            heldBefore = stack;
        });
        s.wait(0.1f);
        s.check("conservation: cores carried by a unit are still held", () -> heldBefore > 0 && ledger.residual() == 0, () -> heldBefore + " carried; " + ledger.balance());
        s.run(() -> {
            for(Unit u : Groups.unit) if(u.team == team && u.stack.amount > 0) u.kill();
        });
        //a dead flying unit falls for a while before it is gone
        s.waitFor(() -> !Groups.unit.contains(u -> u.team == team), 20f);
        s.check("conservation: a unit killed with cores: counted as lost", () -> ledger.coresLost == added + heldBefore && ledger.residual() == 0, () -> ledger.balance());

        //save and load: the machines stopped being fed, what the world holds is what it held
        s.run(() -> {
            Fi file = h.out.child("conservation.msav");
            ledger.active = false;
            heldBefore = Ledger.held(resonanceCore);
            SaveIO.save(file);
            SaveIO.load(file);
            state.set(State.playing);
            h.check("conservation: save and load keep every core where it was", Ledger.held(resonanceCore) == heldBefore, Ledger.held(resonanceCore) + " of " + heldBefore);
        });

        //a lock that cannot pay what holding a pulse costs: what it paid is spent, once, and the pulse is not held
        s.run(() -> {
            Rules r = new Rules();
            r.waves = false;
            r.waveTimer = false;
            r.canGameOver = false;
            r.loadout.clear();
            r.tags.put(vesper.world.AncientSite.tagEnabled, "true");
            Worlds.generate(ControlSuite.width, ControlSuite.height, ControlSuite::generate, r);
        });
        s.wait(0.3f);
        s.run(() -> {
            var c = ControlSuite.controller();
            c.items.add(resonanceCore, 4);
            LockBuild lock = ControlSuite.lock(0);
            lock.seal = 1f;
            int locks = c.lockCores;
            boolean held = lock.contain();
            h.check("conservation: a lock short of the 6 cores a held pulse costs pays what there is, once, and does not hold it", !held && c.items.get(resonanceCore) == 0
                && c.lockCores == locks + 4 && lock.coresSpent == 4 && lock.contained == 0, "held " + held + ", store " + c.items.get(resonanceCore) + ", lock cores " + c.lockCores);
        });
    }

    Building belt(){
        return world.build(routerX - 2, y);
    }

    Building container(){
        return Worlds.at(containerX, y - 1, 2);
    }

    static void generate(Tiles tiles){
        for(int x = 0; x < width; x++){
            for(int v = 0; v < height; v++){
                boolean border = x == 0 || v == 0 || x == width - 1 || v == height - 1;
                tiles.set(x, v, new Tile(x, v, machineBasalt, Blocks.air, border ? machineBasaltWall : Blocks.air));
            }
        }
        place(tiles, coreFoothold, 2, 2);
        place(tiles, resonanceDynamo, dynamoX, dynamoY);
        place(tiles, experimentalResonator, resonatorX, resonatorY);
        for(int x = resonatorX + 3; x < routerX; x++) tiles.getn(x, y).setBlock(transitBelt, team, 0);
        place(tiles, cargoRouter, routerX, y);
        place(tiles, Blocks.container, containerX, y - 1);
    }

    static void place(Tiles tiles, Block block, int left, int bottom){
        int off = (block.size - 1) / 2;
        tiles.getn(left + off, bottom + off).setBlock(block, team, 0);
    }
}
