package com.leclowndu93150.mcredstone.command;

import com.leclowndu93150.mcredstone.Designs;
import com.leclowndu93150.mcredstone.compiler.layout.Compiler;
import com.leclowndu93150.mcredstone.compiler.netlist.Vectors;
import com.leclowndu93150.mcredstone.machine.BuildTask;
import com.leclowndu93150.mcredstone.machine.Machine;
import com.leclowndu93150.mcredstone.machine.MachineRegistry;
import com.leclowndu93150.mcredstone.machine.PlaySessions;
import com.leclowndu93150.mcredstone.machine.Tasks;
import com.leclowndu93150.mcredstone.machine.TestTask;
import com.leclowndu93150.mcredstone.machine.WorldIo;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class McrCommands {
    private static final long PROGRESS_INTERVAL_MS = 5000;

    private McrCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mcr")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("build")
                .then(Commands.argument("design", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(Designs.names(), builder))
                    .executes(McrCommands::build)))
            .then(Commands.literal("remove")
                .then(Commands.argument("machine", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(machineNames(context.getSource()), builder))
                    .executes(McrCommands::remove)))
            .then(Commands.literal("test")
                .then(Commands.argument("machine", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(machineNames(context.getSource()), builder))
                    .executes(context -> test(context, 0))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1))
                        .executes(context -> test(context, IntegerArgumentType.getInteger(context, "count"))))))
            .then(Commands.literal("run")
                .then(Commands.argument("machine", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(machineNames(context.getSource()), builder))
                    .executes(context -> run(context, 0))
                    .then(Commands.argument("tps", IntegerArgumentType.integer(1, 10000))
                        .executes(context -> run(context, IntegerArgumentType.getInteger(context, "tps"))))))
            .then(Commands.literal("stop")
                .then(Commands.argument("machine", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(machineNames(context.getSource()), builder))
                    .executes(McrCommands::stop)))
            .then(Commands.literal("play")
                .then(Commands.argument("machine", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(machineNames(context.getSource()), builder))
                    .executes(context -> play(context, 0))
                    .then(Commands.argument("tps", IntegerArgumentType.integer(1, 10000))
                        .executes(context -> play(context, IntegerArgumentType.getInteger(context, "tps"))))))
            .then(Commands.literal("reset")
                .then(Commands.argument("machine", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(machineNames(context.getSource()), builder))
                    .executes(McrCommands::reset)))
            .then(Commands.literal("list").executes(McrCommands::list)));
    }

    private static List<String> machineNames(CommandSourceStack source) {
        List<String> names = new ArrayList<>();
        for (Machine machine : MachineRegistry.get(source.getServer()).all()) {
            names.add(machine.name());
        }
        return names;
    }

    private static int build(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String designName = StringArgumentType.getString(context, "design");
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        source.sendSuccess(() -> Component.literal("Compiling " + designName + "..."), false);
        long[] lastReport = {System.currentTimeMillis()};
        Consumer<String> progress = message -> {
            long now = System.currentTimeMillis();
            if (now - lastReport[0] >= PROGRESS_INTERVAL_MS) {
                lastReport[0] = now;
                server.execute(() -> source.sendSuccess(() -> Component.literal(designName + ": " + message), false));
            }
        };
        CompletableFuture.supplyAsync(() -> Designs.layout(designName, progress)).whenComplete((layout, error) -> server.execute(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                source.sendFailure(Component.literal("Compile failed: " + cause.getMessage()));
                return;
            }
            int[] bounds = layout.grid().bounds();
            int width = bounds[3] - bounds[0] + 1;
            BlockPos origin = BlockPos.containing(position).offset(-width / 2 - bounds[0], -1 - bounds[1], 3 - bounds[2]);
            String name = MachineRegistry.get(server).freeName(designName);
            Tasks.add(new BuildTask(level, layout, origin, name, message -> source.sendSuccess(() -> message, true), machine -> {
            }));
        }));
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MachineRegistry registry = MachineRegistry.get(source.getServer());
        Machine machine = registry.get(StringArgumentType.getString(context, "machine"));
        if (machine == null) {
            source.sendFailure(Component.literal("No such machine"));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(machine.dimension());
        if (level != null) {
            BuildTask.demolish(level, machine);
        }
        registry.remove(machine.name());
        source.sendSuccess(() -> Component.literal("Removed " + machine.name()), true);
        return 1;
    }

    private static int test(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();
        Machine machine = MachineRegistry.get(source.getServer()).get(StringArgumentType.getString(context, "machine"));
        if (machine == null) {
            source.sendFailure(Component.literal("No such machine"));
            return 0;
        }
        Vectors vectors;
        try {
            vectors = Vectors.read(Designs.directory().resolve(machine.design() + ".vectors.json"));
        } catch (IOException e) {
            source.sendFailure(Component.literal("No vectors for " + machine.design()));
            return 0;
        }
        List<Vectors.Step> steps = new ArrayList<>(vectors.steps());
        if (count > 0 && count < steps.size()) {
            Collections.shuffle(steps, new Random(count));
            steps = steps.subList(0, count);
        }
        ServerLevel level = source.getServer().getLevel(machine.dimension());
        TestTask task = new TestTask(level, machine, steps, source);
        Tasks.add(task);
        source.getServer().tickRateManager().requestGameToSprint(task.totalTicks() + 20);
        int total = steps.size();
        source.sendSuccess(() -> Component.literal("Testing " + machine.name() + " with " + total + " vectors (" + task.totalTicks() + " ticks, sprinting)"), true);
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> context, int tps) {
        CommandSourceStack source = context.getSource();
        Machine machine = MachineRegistry.get(source.getServer()).get(StringArgumentType.getString(context, "machine"));
        if (machine == null || !machine.sequential()) {
            source.sendFailure(Component.literal("No clocked machine with that name"));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(machine.dimension());
        new WorldIo(level, machine).run(true);
        if (tps > 0) {
            source.getServer().tickRateManager().setTickRate(tps);
        }
        float rate = source.getServer().tickRateManager().tickrate();
        source.sendSuccess(() -> Component.literal(String.format("%s running: %d gt per cycle, %.2f cycles/s at %.0f TPS", machine.name(), machine.period(), rate / machine.period(), rate)), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Machine machine = MachineRegistry.get(source.getServer()).get(StringArgumentType.getString(context, "machine"));
        if (machine == null || !machine.sequential()) {
            source.sendFailure(Component.literal("No clocked machine with that name"));
            return 0;
        }
        PlaySessions.halt(source.getServer(), machine);
        source.sendSuccess(() -> Component.literal(machine.name() + " stopped"), true);
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> context, int tps) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Machine machine = MachineRegistry.get(source.getServer()).get(StringArgumentType.getString(context, "machine"));
        if (machine == null) {
            source.sendFailure(Component.literal("No such machine"));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        if (PlaySessions.playing(player, machine.name())) {
            PlaySessions.stop(player, machine);
        } else {
            PlaySessions.start(player, machine, tps);
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        Machine machine = MachineRegistry.get(server).get(StringArgumentType.getString(context, "machine"));
        if (machine == null) {
            source.sendFailure(Component.literal("No such machine"));
            return 0;
        }
        PlaySessions.halt(server, machine);
        ServerLevel level = server.getLevel(machine.dimension());
        CompletableFuture.supplyAsync(() -> Designs.layout(machine.design(), message -> {
        })).whenComplete((layout, error) -> server.execute(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                source.sendFailure(Component.literal("Reset failed: " + cause.getMessage()));
                return;
            }
            if (!machine.version().equals(Compiler.VERSION)) {
                BuildTask.demolish(level, machine);
            }
            int[] bounds = layout.grid().bounds();
            BlockPos origin = machine.min().offset(-bounds[0], -bounds[1], -bounds[2]);
            Tasks.add(new BuildTask(level, layout, origin, machine.name(), message -> {
            }, rebuilt -> source.sendSuccess(() -> Component.literal(rebuilt.name() + " reset"), true)));
        }));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        for (Machine machine : MachineRegistry.get(source.getServer()).all()) {
            source.sendSuccess(() -> Component.literal(machine.name() + " (" + machine.design() + ") at " + machine.min().toShortString()), false);
        }
        return 1;
    }
}
