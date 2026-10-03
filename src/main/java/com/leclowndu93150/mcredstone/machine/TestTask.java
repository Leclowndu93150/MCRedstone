package com.leclowndu93150.mcredstone.machine;

import com.leclowndu93150.mcredstone.compiler.netlist.Vectors;
import com.leclowndu93150.mcredstone.compiler.sim.SequentialProtocol;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class TestTask implements Tasks.Task {
    private static final int SHOWN_FAILURES = 5;

    private final ServerLevel level;
    private final Machine machine;
    private final List<Vectors.Step> steps;
    private final CommandSourceStack source;
    private final WorldIo io;
    private final SequentialProtocol protocol;
    private final List<String> failures = new ArrayList<>();
    private int index;
    private long waitUntil;
    private boolean applied;
    private int failed;

    public TestTask(ServerLevel level, Machine machine, List<Vectors.Step> steps, CommandSourceStack source) {
        this.level = level;
        this.machine = machine;
        this.steps = steps;
        this.source = source;
        this.io = new WorldIo(level, machine);
        this.protocol = machine.sequential() ? new SequentialProtocol(io, steps, machine.inputDelay(), 2 * machine.period()) : null;
        this.waitUntil = level.getGameTime() + machine.settleTicks();
    }

    public int totalTicks() {
        return machine.sequential() ? (steps.size() + 4) * machine.period() + machine.settleTicks() : (steps.size() + 1) * machine.settleTicks();
    }

    @Override
    public boolean tick(MinecraftServer server) {
        if (protocol != null) {
            protocol.tick();
            if (!protocol.done()) {
                return false;
            }
            report(server, protocol.total() - protocol.failed(), protocol.total(), protocol.failures());
            return true;
        }
        if (level.getGameTime() < waitUntil) {
            return false;
        }
        if (!applied) {
            for (Map.Entry<String, BigInteger> entry : steps.get(index).set().entrySet()) {
                io.drive(entry.getKey(), entry.getValue());
            }
            applied = true;
            waitUntil = level.getGameTime() + machine.settleTicks();
            return false;
        }
        Vectors.Step step = steps.get(index);
        for (Map.Entry<String, BigInteger> entry : step.expect().entrySet()) {
            BigInteger actual = io.read(entry.getKey());
            if (!actual.equals(entry.getValue())) {
                failed++;
                if (failures.size() < SHOWN_FAILURES) {
                    failures.add(step.set() + ": " + entry.getKey() + " = " + actual + ", expected " + entry.getValue());
                }
                break;
            }
        }
        applied = false;
        index++;
        if (index < steps.size()) {
            return false;
        }
        report(server, steps.size() - failed, steps.size(), failures);
        return true;
    }

    private void report(MinecraftServer server, int passed, int total, List<String> shown) {
        server.tickRateManager().stopSprinting();
        source.sendSuccess(() -> Component.literal(machine.name() + ": " + passed + "/" + total + (machine.sequential() ? " cycles" : " vectors") + " passed"), true);
        for (String failure : shown) {
            source.sendFailure(Component.literal("  " + failure));
        }
    }
}
