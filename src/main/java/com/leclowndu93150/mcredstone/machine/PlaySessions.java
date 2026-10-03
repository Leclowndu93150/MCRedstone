package com.leclowndu93150.mcredstone.machine;

import com.leclowndu93150.mcredstone.net.PlayModePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class PlaySessions {
    private static final int FRAMES_PER_SECOND = 3;
    private static final int MAX_TPS = 1000;
    private static final Map<UUID, String> PLAYING = new HashMap<>();
    private static final Map<String, String> KEY_LABELS = Map.of("up", "↑", "down", "↓", "left", "←", "right", "→", "a", "Z", "b", "X");

    private PlaySessions() {
    }

    public static boolean playing(ServerPlayer player, String machine) {
        return machine.equals(PLAYING.get(player.getUUID()));
    }

    public static void start(ServerPlayer player, Machine machine, int tps) {
        MinecraftServer server = player.level().getServer();
        PLAYING.put(player.getUUID(), machine.name());
        ServerPlayNetworking.send(player, new PlayModePayload(machine.name()));
        String speed = "";
        if (machine.sequential()) {
            int rate = tps > 0 ? tps : Math.min(MAX_TPS, Math.max(20, machine.period() * FRAMES_PER_SECOND));
            server.tickRateManager().setTickRate(rate);
            new WorldIo(server.getLevel(machine.dimension()), machine).run(true);
            speed = String.format(" at %d TPS (%.1f frames/s)", rate, rate / (float) machine.period());
        }
        player.sendSystemMessage(Component.literal("Playing " + machine.name() + speed + ": " + controls(machine) + ". /mcr play " + machine.name() + " again to stop."));
    }

    public static void stop(ServerPlayer player, Machine machine) {
        MinecraftServer server = player.level().getServer();
        PLAYING.remove(player.getUUID());
        ServerPlayNetworking.send(player, new PlayModePayload(""));
        halt(server, machine);
        player.sendSystemMessage(Component.literal(machine.name() + " stopped"));
    }

    public static void halt(MinecraftServer server, Machine machine) {
        if (machine.sequential()) {
            new WorldIo(server.getLevel(machine.dimension()), machine).run(false);
        }
        if (server.tickRateManager().tickrate() != 20f) {
            server.tickRateManager().setTickRate(20f);
        }
    }

    public static String controls(Machine machine) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (IoBinding binding : machine.inputs()) {
            binding.key().ifPresent(key -> keys.putIfAbsent(key, binding.port()));
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : keys.entrySet()) {
            parts.add(KEY_LABELS.getOrDefault(entry.getKey(), entry.getKey()) + " " + entry.getValue());
        }
        return parts.isEmpty() ? "no key bindings" : String.join(", ", parts);
    }
}
