package com.leclowndu93150.mcredstone.gametest;

import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.netlist.Vectors;
import com.leclowndu93150.mcredstone.machine.BuildTask;
import com.leclowndu93150.mcredstone.machine.IoBinding;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class VectorRunner {
    private final GameTestHelper helper;
    private final ServerLevel level;
    private final List<IoBinding> inputs;
    private final List<IoBinding> outputs;
    private final List<Vectors.Step> steps;
    private final int settle;
    private int index;
    private int wait;
    private boolean applied;

    private VectorRunner(GameTestHelper helper, Layout layout, List<Vectors.Step> steps) {
        this.helper = helper;
        this.level = helper.getLevel();
        int[] bounds = layout.grid().bounds();
        BlockPos origin = helper.absolutePos(new BlockPos(2 - bounds[0], 1 - bounds[1], 2 - bounds[2]));
        BuildTask.placeAll(level, layout, origin);
        this.inputs = BuildTask.bindings(layout.inputs(), origin);
        this.outputs = BuildTask.bindings(layout.outputs(), origin);
        this.steps = steps;
        this.settle = layout.settleTicks();
        this.wait = settle;
    }

    static void run(GameTestHelper helper, Layout layout, List<Vectors.Step> steps) {
        VectorRunner runner = new VectorRunner(helper, layout, steps);
        helper.getLevel().getServer().tickRateManager().requestGameToSprint((steps.size() + 2) * layout.settleTicks());
        helper.onEachTick(runner::tick);
    }

    private void tick() {
        if (index >= steps.size()) {
            return;
        }
        if (--wait > 0) {
            return;
        }
        Vectors.Step step = steps.get(index);
        if (!applied) {
            for (Map.Entry<String, BigInteger> entry : step.set().entrySet()) {
                drive(entry.getKey(), entry.getValue());
            }
            applied = true;
            wait = settle;
            return;
        }
        for (Map.Entry<String, BigInteger> entry : step.expect().entrySet()) {
            BigInteger actual = read(entry.getKey());
            if (!actual.equals(entry.getValue())) {
                helper.fail("vector " + index + " " + step.set() + ": " + entry.getKey() + " = " + actual + ", expected " + entry.getValue());
            }
        }
        applied = false;
        index++;
        wait = 1;
        if (index == steps.size()) {
            helper.getLevel().getServer().tickRateManager().stopSprinting();
            helper.succeed();
        }
    }

    private void drive(String port, BigInteger value) {
        for (IoBinding binding : inputs) {
            if (binding.port().equals(port)) {
                boolean desired = value.testBit(binding.bit());
                BlockState state = level.getBlockState(binding.pos());
                if (state.getBlock() instanceof LeverBlock lever && state.getValue(BlockStateProperties.POWERED) != desired) {
                    lever.pull(state, level, binding.pos(), null);
                }
            }
        }
    }

    private BigInteger read(String port) {
        BigInteger value = BigInteger.ZERO;
        for (IoBinding binding : outputs) {
            if (binding.port().equals(port)) {
                BlockState state = level.getBlockState(binding.pos());
                if (state.getValue(BlockStateProperties.LIT)) {
                    value = value.setBit(binding.bit());
                }
            }
        }
        return value;
    }
}
