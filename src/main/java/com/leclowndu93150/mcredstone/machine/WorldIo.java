package com.leclowndu93150.mcredstone.machine;

import com.leclowndu93150.mcredstone.compiler.sim.SequentialProtocol;
import com.leclowndu93150.mcredstone.compiler.synth.ClockInsert;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class WorldIo implements SequentialProtocol.Io {
    private final ServerLevel level;
    private final Machine machine;

    public WorldIo(ServerLevel level, Machine machine) {
        this.level = level;
        this.machine = machine;
    }

    @Override
    public boolean clock() {
        BlockState state = level.getBlockState(machine.clockProbe().orElseThrow());
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
    }

    @Override
    public void drive(String port, BigInteger value) {
        for (IoBinding binding : machine.inputs()) {
            if (binding.port().equals(port)) {
                setLever(level, binding.pos(), value.testBit(binding.bit()));
            }
        }
    }

    @Override
    public BigInteger read(String port) {
        BigInteger value = BigInteger.ZERO;
        for (IoBinding binding : machine.outputs()) {
            if (binding.port().equals(port)) {
                BlockState state = level.getBlockState(binding.pos());
                if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
                    value = value.setBit(binding.bit());
                }
            }
        }
        return value;
    }

    @Override
    public void run(boolean on) {
        drive(ClockInsert.RUN, on ? BigInteger.ONE : BigInteger.ZERO);
    }

    public static void setLever(ServerLevel level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LeverBlock lever && state.getValue(BlockStateProperties.POWERED) != on) {
            lever.pull(state, level, pos, null);
        }
    }
}
