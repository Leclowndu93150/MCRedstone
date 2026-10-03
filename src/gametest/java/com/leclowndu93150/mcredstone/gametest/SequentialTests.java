package com.leclowndu93150.mcredstone.gametest;

import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.netlist.Design;
import com.leclowndu93150.mcredstone.compiler.netlist.Vectors;
import com.leclowndu93150.mcredstone.compiler.sim.SequentialProtocol;
import com.leclowndu93150.mcredstone.machine.BuildTask;
import com.leclowndu93150.mcredstone.machine.Machine;
import com.leclowndu93150.mcredstone.machine.MachineRegistry;
import com.leclowndu93150.mcredstone.machine.PlayInputs;
import com.leclowndu93150.mcredstone.net.InputPayload;
import com.leclowndu93150.mcredstone.machine.WorldIo;
import java.math.BigInteger;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class SequentialTests {
    private static final String STRUCTURE = "mcredstone-gametest:empty96";
    private static final int CYCLES = 64;

    @GameTest(structure = STRUCTURE, maxTicks = 20000)
    public void counter8(GameTestHelper helper) throws Exception {
        run(helper, "counter8");
    }

    @GameTest(structure = STRUCTURE, maxTicks = 20000)
    public void lfsr16(GameTestHelper helper) throws Exception {
        run(helper, "lfsr16");
    }

    @GameTest(structure = STRUCTURE, maxTicks = 20000)
    public void dot(GameTestHelper helper) throws Exception {
        run(helper, "dot");
    }

    @GameTest(structure = "mcredstone-gametest:empty160", maxTicks = 40000)
    public void flappy(GameTestHelper helper) throws Exception {
        run(helper, "flappy");
    }

    @GameTest(structure = "mcredstone-gametest:empty160", maxTicks = 60000)
    public void pong(GameTestHelper helper) throws Exception {
        run(helper, "pong");
    }

    @GameTest(structure = STRUCTURE, maxTicks = 4000)
    public void playModeMovesDot(GameTestHelper helper) throws Exception {
        Design design = DesignTests.loadDesign("dot");
        Layout layout = Compiler.compile(design);
        int[] bounds = layout.grid().bounds();
        BlockPos origin = helper.absolutePos(new BlockPos(2 - bounds[0], 1 - bounds[1], 2 - bounds[2]));
        ServerLevel level = helper.getLevel();
        BuildTask.placeAll(level, layout, origin);
        Machine machine = BuildTask.machine("dot", level, layout, origin);
        MachineRegistry.get(level.getServer()).put(machine);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(origin.getX(), origin.getY() + 2, origin.getZ() - 8);
        WorldIo io = new WorldIo(level, machine);
        BigInteger before = io.read("screen");
        io.run(true);
        PlayInputs.handle(level.getServer(), player, new InputPayload("dot", "right", true));
        helper.runAtTickTime(machine.period() * 4L, () -> PlayInputs.handle(level.getServer(), player, new InputPayload("dot", "right", false)));
        helper.runAtTickTime(machine.period() * 8L, () -> {
            BigInteger after = io.read("screen");
            helper.assertTrue(!after.equals(before) && after.testBit(7), "dot did not move right: " + before.toString(2) + " -> " + after.toString(2));
            helper.succeed();
        });
    }

    private static void run(GameTestHelper helper, String name) throws Exception {
        Design design = DesignTests.loadDesign(name);
        Layout layout = Compiler.compile(design);
        int[] bounds = layout.grid().bounds();
        BlockPos origin = helper.absolutePos(new BlockPos(2 - bounds[0], 1 - bounds[1], 2 - bounds[2]));
        BuildTask.placeAll(helper.getLevel(), layout, origin);
        Machine machine = BuildTask.machine(name, helper.getLevel(), layout, origin);
        List<Vectors.Step> steps = design.vectors().steps().subList(0, CYCLES);
        SequentialProtocol protocol = new SequentialProtocol(new WorldIo(helper.getLevel(), machine), steps, machine.inputDelay(), 2 * machine.period());
        helper.getLevel().getServer().tickRateManager().requestGameToSprint((CYCLES + 6) * machine.period());
        helper.onEachTick(() -> {
            if (protocol.done()) {
                return;
            }
            protocol.tick();
            if (protocol.done()) {
                helper.getLevel().getServer().tickRateManager().stopSprinting();
                if (protocol.failed() > 0) {
                    helper.fail(name + ": " + protocol.failed() + "/" + protocol.total() + " cycles failed: " + String.join("; ", protocol.failures()));
                } else {
                    helper.succeed();
                }
            }
        });
    }
}
