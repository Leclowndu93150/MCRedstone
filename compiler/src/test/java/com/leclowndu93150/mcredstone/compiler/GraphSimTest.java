package com.leclowndu93150.mcredstone.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.IoPin;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.netlist.Vectors;
import com.leclowndu93150.mcredstone.compiler.sim.GraphSim;
import com.leclowndu93150.mcredstone.compiler.sim.Node;
import com.leclowndu93150.mcredstone.compiler.voxel.Dir;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.compiler.voxel.Voxel;
import com.leclowndu93150.mcredstone.compiler.voxel.VoxelGrid;
import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphSimTest {
    private static VoxelGrid floor(int size) {
        VoxelGrid grid = new VoxelGrid();
        for (int x = -2; x < size; x++) {
            for (int z = -2; z < size; z++) {
                grid.set(x, 0, z, Voxel.GLASS);
            }
        }
        return grid;
    }

    @Test
    void repeaterChainDelay() {
        VoxelGrid grid = floor(12);
        grid.set(0, 1, 0, Voxel.SOLID);
        grid.set(0, 1, -1, Voxel.wallLever(Dir.NORTH));
        grid.set(0, 1, 1, Voxel.repeater(Dir.SOUTH, 1));
        grid.set(0, 1, 2, Voxel.WIRE);
        grid.set(0, 1, 3, Voxel.repeater(Dir.SOUTH, 3));
        grid.set(0, 1, 4, Voxel.LAMP);
        GraphSim sim = new GraphSim(grid);
        sim.kickAll();
        assertTrue(sim.settle(100));
        Node lamp = sim.node(Pos.of(0, 1, 4));
        assertFalse(lamp.on());
        sim.setLever(Pos.of(0, 1, -1), true);
        int ticks = 0;
        while (!lamp.on()) {
            sim.step();
            ticks++;
        }
        assertEquals(2 + 6, ticks);
    }

    @Test
    void torchInvertsAfterTwoTicks() {
        VoxelGrid grid = floor(8);
        grid.set(0, 1, 0, Voxel.SOLID);
        grid.set(0, 1, -1, Voxel.wallLever(Dir.NORTH));
        grid.set(0, 1, 1, Voxel.wallTorch(Dir.SOUTH));
        grid.set(0, 1, 2, Voxel.WIRE);
        grid.set(0, 1, 3, Voxel.LAMP);
        GraphSim sim = new GraphSim(grid);
        sim.kickAll();
        assertTrue(sim.settle(100));
        Node lamp = sim.node(Pos.of(0, 1, 3));
        assertTrue(lamp.on());
        sim.setLever(Pos.of(0, 1, -1), true);
        sim.run(2);
        assertTrue(lamp.on());
        assertFalse(sim.node(Pos.of(0, 1, 1)).on());
        sim.run(4);
        assertFalse(lamp.on());
    }

    @Test
    void lockedRepeaterHolds() {
        VoxelGrid grid = floor(8);
        grid.set(0, 1, 0, Voxel.SOLID);
        grid.set(0, 1, -1, Voxel.wallLever(Dir.NORTH));
        grid.set(0, 1, 1, Voxel.WIRE);
        grid.set(0, 1, 2, Voxel.repeater(Dir.SOUTH, 1));
        grid.set(0, 1, 3, Voxel.LAMP);
        grid.set(3, 1, 0, Voxel.SOLID);
        grid.set(3, 1, -1, Voxel.wallLever(Dir.NORTH));
        grid.set(3, 1, 1, Voxel.repeater(Dir.SOUTH, 1));
        grid.set(3, 1, 2, Voxel.WIRE);
        grid.set(2, 1, 2, Voxel.WIRE);
        grid.set(1, 1, 2, Voxel.repeater(Dir.WEST, 1));
        GraphSim sim = new GraphSim(grid);
        sim.kickAll();
        assertTrue(sim.settle(100));
        Node latch = sim.node(Pos.of(0, 1, 2));
        sim.setLever(Pos.of(0, 1, -1), true);
        sim.run(10);
        assertTrue(latch.on());
        sim.setLever(Pos.of(3, 1, -1), true);
        sim.run(10);
        sim.setLever(Pos.of(0, 1, -1), false);
        sim.run(10);
        assertTrue(latch.on());
        sim.setLever(Pos.of(3, 1, -1), false);
        sim.run(10);
        assertFalse(latch.on());
    }

    @Test
    void adderMatchesVectorsInGraphSim() throws Exception {
        Design design = Adder4Test.load("adder4");
        Layout layout = Compiler.compile(design);
        GraphSim sim = new GraphSim(layout.grid());
        sim.kickAll();
        assertTrue(sim.settle(1000));
        Vectors vectors = design.vectors();
        int checked = 0;
        for (int i = 0; i < vectors.steps().size(); i += 7) {
            Vectors.Step step = vectors.steps().get(i);
            for (IoPin pin : layout.inputs()) {
                sim.setLever(pin.pos(), step.set().get(pin.port()).testBit(pin.bit()));
            }
            assertTrue(sim.settle(1000));
            for (Map.Entry<String, BigInteger> entry : step.expect().entrySet()) {
                BigInteger value = BigInteger.ZERO;
                for (IoPin pin : layout.outputs()) {
                    if (pin.port().equals(entry.getKey()) && sim.node(pin.pos()).on()) {
                        value = value.setBit(pin.bit());
                    }
                }
                assertEquals(entry.getValue(), value, "vector " + i + " " + step.set());
            }
            checked++;
        }
        assertTrue(checked > 50);
        assertEquals(0, sim.burnouts());
    }
}
