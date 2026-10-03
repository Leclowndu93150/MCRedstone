package com.leclowndu93150.mcredstone.gametest;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.IoPin;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.sim.GraphSim;
import com.leclowndu93150.mcredstone.compiler.sim.Node;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import com.leclowndu93150.mcredstone.machine.BuildTask;
import java.util.Random;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ParityTests {
    private static final int TICKS = 1200;
    private static final int SETTLE = 1;
    private static final int CHANGE_EVERY = 7;

    @GameTest(structure = "mcredstone-gametest:empty64", maxTicks = TICKS + 200)
    public void adderTickParity(GameTestHelper helper) throws Exception {
        Design design = DesignTests.loadDesign("adder4");
        Layout layout = Compiler.compile(design);
        int[] bounds = layout.grid().bounds();
        BlockPos origin = helper.absolutePos(new BlockPos(2 - bounds[0], 1 - bounds[1], 2 - bounds[2]));
        ServerLevel level = helper.getLevel();
        BuildTask.placeAll(level, layout, origin);
        GraphSim sim = new GraphSim(layout.grid());
        sim.load(layout.states());
        Random random = new Random(7);
        int[] elapsed = {0};
        helper.onEachTick(() -> {
            if (elapsed[0] >= TICKS) {
                return;
            }
            elapsed[0]++;
            if (elapsed[0] < SETTLE) {
                sim.step();
                return;
            }
            for (Node node : sim.nodes()) {
                BlockPos pos = origin.offset(Pos.x(node.pos()), Pos.y(node.pos()), Pos.z(node.pos()));
                BlockState state = level.getBlockState(pos);
                Boolean world = worldOn(state);
                if (world != null && world != node.on()) {
                    helper.fail("tick " + elapsed[0] + ": " + node.voxel().kind() + " at " + Pos.toString(node.pos()) + " world=" + world + " sim=" + node.on());
                }
            }
            if (elapsed[0] % CHANGE_EVERY == 0) {
                IoPin pin = layout.inputs().get(random.nextInt(layout.inputs().size()));
                BlockPos pos = origin.offset(Pos.x(pin.pos()), Pos.y(pin.pos()), Pos.z(pin.pos()));
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof LeverBlock lever) {
                    lever.pull(state, level, pos, null);
                    sim.setLever(pin.pos(), level.getBlockState(pos).getValue(BlockStateProperties.POWERED));
                }
            }
            sim.step();
            if (elapsed[0] == TICKS) {
                helper.succeed();
            }
        });
    }

    private static Boolean worldOn(BlockState state) {
        if (state.hasProperty(BlockStateProperties.LIT)) {
            return state.getValue(BlockStateProperties.LIT);
        }
        if (state.hasProperty(BlockStateProperties.POWERED)) {
            return state.getValue(BlockStateProperties.POWERED);
        }
        return null;
    }
}
