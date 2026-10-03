package com.leclowndu93150.mcredstone.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record InputPayload(String machine, String key, boolean down) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<InputPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("mcredstone", "input"));
    public static final StreamCodec<ByteBuf, InputPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, InputPayload::machine,
        ByteBufCodecs.STRING_UTF8, InputPayload::key,
        ByteBufCodecs.BOOL, InputPayload::down,
        InputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
