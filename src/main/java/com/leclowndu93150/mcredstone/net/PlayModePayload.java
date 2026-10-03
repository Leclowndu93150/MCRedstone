package com.leclowndu93150.mcredstone.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PlayModePayload(String machine) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayModePayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mcredstone", "play_mode"));
    public static final StreamCodec<ByteBuf, PlayModePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, PlayModePayload::machine,
        PlayModePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
