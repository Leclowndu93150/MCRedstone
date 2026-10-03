package com.leclowndu93150.mcredstone.machine;

import com.leclowndu93150.mcredstone.net.InputPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PlayInputs {
    private static final double MAX_DISTANCE = 128;
    private static final Map<String, Long> PRESSED_AT = new HashMap<>();

    private PlayInputs() {
    }

    public static void handle(MinecraftServer server, ServerPlayer player, InputPayload payload) {
        Machine machine = MachineRegistry.get(server).get(payload.machine());
        if (machine == null || !player.level().dimension().equals(machine.dimension())) {
            return;
        }
        if (player.blockPosition().distSqr(machine.min()) > MAX_DISTANCE * MAX_DISTANCE && player.blockPosition().distSqr(machine.max()) > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        ServerLevel level = server.getLevel(machine.dimension());
        String id = machine.name() + "/" + payload.key();
        long now = level.getGameTime();
        if (payload.down()) {
            PRESSED_AT.put(id, now);
            set(level, machine, payload.key(), true);
            return;
        }
        long release = PRESSED_AT.getOrDefault(id, now) + minimumHold(machine);
        if (now >= release) {
            set(level, machine, payload.key(), false);
            return;
        }
        Tasks.add(s -> {
            if (level.getGameTime() < release) {
                return false;
            }
            if (PRESSED_AT.getOrDefault(id, 0L) + minimumHold(machine) <= level.getGameTime()) {
                set(level, machine, payload.key(), false);
            }
            return true;
        });
    }

    private static int minimumHold(Machine machine) {
        return machine.sequential() ? machine.period() + machine.inputDelay() + 4 : machine.settleTicks();
    }

    private static void set(ServerLevel level, Machine machine, String key, boolean on) {
        for (IoBinding binding : machine.inputs()) {
            if (binding.key().filter(key::equals).isPresent()) {
                WorldIo.setLever(level, binding.pos(), on);
            }
        }
    }
}
