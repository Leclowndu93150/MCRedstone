package com.leclowndu93150.mcredstone.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public record Machine(String name, String design, ResourceKey<Level> dimension, BlockPos min, BlockPos max, List<IoBinding> inputs, List<IoBinding> outputs,
                      int settleTicks, int period, int inputDelay, Optional<BlockPos> clockProbe, String version) {
    public static final Codec<Machine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("name").forGetter(Machine::name),
        Codec.STRING.fieldOf("design").forGetter(Machine::design),
        Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Machine::dimension),
        BlockPos.CODEC.fieldOf("min").forGetter(Machine::min),
        BlockPos.CODEC.fieldOf("max").forGetter(Machine::max),
        IoBinding.CODEC.listOf().fieldOf("inputs").forGetter(Machine::inputs),
        IoBinding.CODEC.listOf().fieldOf("outputs").forGetter(Machine::outputs),
        Codec.INT.fieldOf("settle_ticks").forGetter(Machine::settleTicks),
        Codec.INT.optionalFieldOf("period", 0).forGetter(Machine::period),
        Codec.INT.optionalFieldOf("input_delay", 0).forGetter(Machine::inputDelay),
        BlockPos.CODEC.optionalFieldOf("clock_probe").forGetter(Machine::clockProbe),
        Codec.STRING.optionalFieldOf("version", "").forGetter(Machine::version)
    ).apply(instance, Machine::new));

    public boolean sequential() {
        return clockProbe.isPresent();
    }

    public Optional<IoBinding> input(String port) {
        for (IoBinding binding : inputs) {
            if (binding.port().equals(port)) {
                return Optional.of(binding);
            }
        }
        return Optional.empty();
    }

    public List<ChunkPos> chunks() {
        int minX = (min.getX() - 1) >> 4;
        int maxX = (max.getX() + 1) >> 4;
        int minZ = (min.getZ() - 1) >> 4;
        int maxZ = (max.getZ() + 1) >> 4;
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }
}
