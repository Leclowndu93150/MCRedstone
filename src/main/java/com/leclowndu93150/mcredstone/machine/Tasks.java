package com.leclowndu93150.mcredstone.machine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

public final class Tasks {
    public interface Task {
        boolean tick(MinecraftServer server);
    }

    private static final List<Task> ACTIVE = new ArrayList<>();

    private Tasks() {
    }

    public static void add(Task task) {
        ACTIVE.add(task);
    }

    public static void tick(MinecraftServer server) {
        if (!ACTIVE.isEmpty()) {
            List<Task> snapshot = new ArrayList<>(ACTIVE);
            for (Task task : snapshot) {
                if (task.tick(server)) {
                    ACTIVE.remove(task);
                }
            }
        }
    }
}
