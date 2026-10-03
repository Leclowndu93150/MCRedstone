package com.leclowndu93150.mcredstone.machine;

import com.leclowndu93150.mcredstone.Designs;
import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.layout.IoPin;
import com.leclowndu93150.mcredstone.compiler.layout.Layout;
import com.leclowndu93150.mcredstone.compiler.voxel.Pos;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

public final class AutoBuild {
    private static final int VIEW_DISTANCE = 16;
    private static final int GAP = 12;
    private static final long PROGRESS_INTERVAL_MS = 5000;
    private static final Set<String> PENDING = new HashSet<>();
    private static final List<Machine> RESERVED = new ArrayList<>();

    private AutoBuild() {
    }

    public static void start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (String name : Designs.autobuild()) {
            Machine existing = MachineRegistry.get(server).get(name);
            if (existing != null && existing.version().equals(Compiler.VERSION) || !PENDING.add(name)) {
                continue;
            }
            long[] lastReport = {System.currentTimeMillis()};
            CompletableFuture.supplyAsync(() -> Designs.layout(name, message -> {
                long now = System.currentTimeMillis();
                if (now - lastReport[0] >= PROGRESS_INTERVAL_MS) {
                    lastReport[0] = now;
                    server.execute(() -> server.getPlayerList().broadcastSystemMessage(Component.literal("Compiling " + name + ": " + message), false));
                }
            })).whenComplete((layout, error) -> server.execute(() -> {
                PENDING.remove(name);
                if (error != null) {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    server.getPlayerList().broadcastSystemMessage(Component.literal("Auto-build of " + name + " failed: " + cause.getMessage()), false);
                    return;
                }
                BlockPos origin;
                if (existing != null) {
                    BuildTask.demolish(level, existing);
                    MachineRegistry.get(server).remove(name);
                    int[] bounds = layout.grid().bounds();
                    origin = existing.min().offset(-bounds[0], -bounds[1], -bounds[2]);
                } else {
                    origin = origin(level, layout);
                }
                BuildTask task = new BuildTask(level, layout, origin, name,
                    message -> server.getPlayerList().broadcastSystemMessage(message, false),
                    machine -> {
                        RESERVED.removeIf(reserved -> reserved.name().equals(machine.name()));
                        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                            welcome(player, machine);
                        }
                    });
                RESERVED.add(task.machine());
                Tasks.add(task);
            }));
        }
    }

    public static void join(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        for (String name : Designs.autobuild()) {
            Machine machine = MachineRegistry.get(server).get(name);
            if (machine != null && !PENDING.contains(name)) {
                welcome(player, machine);
                return;
            }
            if (PENDING.contains(name)) {
                player.sendSystemMessage(Component.literal("Building " + name + ", one moment..."));
                return;
            }
        }
    }

    private static BlockPos origin(ServerLevel level, Layout layout) {
        BlockPos spawn = level.getRespawnData().pos();
        int[] bounds = layout.grid().bounds();
        int centerX = 0;
        for (IoPin pin : layout.outputs()) {
            centerX += Pos.x(pin.pos());
        }
        centerX = layout.outputs().isEmpty() ? (bounds[0] + bounds[3]) / 2 : centerX / layout.outputs().size();
        int frontZ = spawn.getZ() + VIEW_DISTANCE;
        int x = spawn.getX() - centerX;
        List<Machine> neighbors = new ArrayList<>(MachineRegistry.get(level.getServer()).all());
        neighbors.addAll(RESERVED);
        for (Machine machine : neighbors) {
            if (machine.dimension().equals(level.dimension())) {
                x = Math.max(x, machine.max().getX() + GAP - bounds[0]);
            }
        }
        int groundX = x + (bounds[0] + bounds[3]) / 2;
        level.getChunk(SectionPos.blockToSectionCoord(groundX), SectionPos.blockToSectionCoord(frontZ));
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, groundX, frontZ);
        return new BlockPos(x, ground - 1 - bounds[1], frontZ - bounds[2]);
    }

    private static void welcome(ServerPlayer player, Machine machine) {
        double x = 0;
        double y = 0;
        int frontZ = Integer.MAX_VALUE;
        for (IoBinding binding : machine.outputs()) {
            x += binding.pos().getX();
            y += binding.pos().getY();
            frontZ = Math.min(frontZ, binding.pos().getZ());
        }
        int count = Math.max(1, machine.outputs().size());
        BlockPos feet = BlockPos.containing(x / count, y / count - 1, frontZ - VIEW_DISTANCE);
        ServerLevel level = player.level().getServer().getLevel(machine.dimension());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos floor = feet.offset(dx, -1, dz);
                if (level.getBlockState(floor).isAir()) {
                    level.setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
        }
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), 0f, 0f, true);
        player.sendSystemMessage(Component.literal(machine.name() + " is ready."));
        player.sendSystemMessage(Component.literal("  /mcr play " + machine.name() + "   start playing (" + PlaySessions.controls(machine) + "), run it again to stop"));
        player.sendSystemMessage(Component.literal("  /mcr reset " + machine.name() + "   put it back to its starting state"));
        player.sendSystemMessage(Component.literal("  Fly in from the sides to explore: blue floors are logic gates, green are flip-flops, red is the clock, yellow are inputs."));
    }
}
