package com.leclowndu93150.mcredstone.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record IoBinding(String port, int bit, BlockPos pos, String kind, Optional<String> key) {
    public static final Codec<IoBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("port").forGetter(IoBinding::port),
        Codec.INT.fieldOf("bit").forGetter(IoBinding::bit),
        BlockPos.CODEC.fieldOf("pos").forGetter(IoBinding::pos),
        Codec.STRING.fieldOf("kind").forGetter(IoBinding::kind),
        Codec.STRING.optionalFieldOf("key").forGetter(IoBinding::key)
    ).apply(instance, IoBinding::new));
}
