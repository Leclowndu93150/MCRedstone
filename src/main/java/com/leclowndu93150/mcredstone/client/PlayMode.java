package com.leclowndu93150.mcredstone.client;

import com.leclowndu93150.mcredstone.net.InputPayload;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class PlayMode {
    private static final Map<String, KeyMapping> KEYS = new LinkedHashMap<>();
    private static final Map<String, Boolean> DOWN = new LinkedHashMap<>();
    private static String machine;

    private PlayMode() {
    }

    public static void registerKeys() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mcredstone", "play"));
        register(category, "up", InputConstants.KEY_UP);
        register(category, "down", InputConstants.KEY_DOWN);
        register(category, "left", InputConstants.KEY_LEFT);
        register(category, "right", InputConstants.KEY_RIGHT);
        register(category, "a", InputConstants.KEY_Z);
        register(category, "b", InputConstants.KEY_X);
    }

    private static void register(KeyMapping.Category category, String key, int code) {
        KEYS.put(key, KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mcredstone." + key, code, category)));
        DOWN.put(key, false);
    }

    public static void set(String name) {
        releaseAll();
        machine = name.isEmpty() ? null : name;
    }

    public static void tick(Minecraft minecraft) {
        if (machine == null || minecraft.player == null) {
            return;
        }
        for (Map.Entry<String, KeyMapping> entry : KEYS.entrySet()) {
            boolean down = entry.getValue().isDown();
            if (down != DOWN.get(entry.getKey())) {
                DOWN.put(entry.getKey(), down);
                ClientPlayNetworking.send(new InputPayload(machine, entry.getKey(), down));
            }
        }
    }

    private static void releaseAll() {
        for (Map.Entry<String, Boolean> entry : DOWN.entrySet()) {
            if (entry.getValue() && machine != null) {
                ClientPlayNetworking.send(new InputPayload(machine, entry.getKey(), false));
            }
            entry.setValue(false);
        }
    }
}
