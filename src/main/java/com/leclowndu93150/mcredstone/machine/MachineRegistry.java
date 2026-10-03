package com.leclowndu93150.mcredstone.machine;

import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class MachineRegistry extends SavedData {
    private static final Codec<MachineRegistry> CODEC = Machine.CODEC.listOf()
        .fieldOf("machines")
        .codec()
        .xmap(MachineRegistry::new, registry -> List.copyOf(registry.machines.values()));
    public static final SavedDataType<MachineRegistry> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("mcredstone", "machines"), MachineRegistry::new, CODEC, null
    );

    private final Map<String, Machine> machines = new LinkedHashMap<>();

    private MachineRegistry() {
    }

    private MachineRegistry(List<Machine> list) {
        for (Machine machine : list) {
            machines.put(machine.name(), machine);
        }
    }

    public static MachineRegistry get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Machine get(String name) {
        return machines.get(name);
    }

    public Collection<Machine> all() {
        return machines.values();
    }

    public String freeName(String design) {
        if (!machines.containsKey(design)) {
            return design;
        }
        int suffix = 2;
        while (machines.containsKey(design + "_" + suffix)) {
            suffix++;
        }
        return design + "_" + suffix;
    }

    public void put(Machine machine) {
        machines.put(machine.name(), machine);
        setDirty();
    }

    public void remove(String name) {
        machines.remove(name);
        setDirty();
    }
}
