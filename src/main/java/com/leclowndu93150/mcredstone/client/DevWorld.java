package com.leclowndu93150.mcredstone.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class DevWorld {
    private static final String LEVEL = "mcredstone";
    private static boolean opened;

    private DevWorld() {
    }

    public static void register() {
        if (!Boolean.getBoolean("mcredstone.devWorld")) {
            return;
        }
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!opened && screen instanceof TitleScreen) {
                opened = true;
                client.execute(() -> open(client, screen));
            }
        });
    }

    private static void open(Minecraft client, Screen parent) {
        if (client.getLevelSource().levelExists(LEVEL)) {
            client.createWorldOpenFlows().openWorld(LEVEL, () -> client.gui.setScreen(parent));
            return;
        }
        LevelSettings settings = new LevelSettings("MCRedstone", GameType.CREATIVE,
            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel(LEVEL, settings, new WorldOptions(0L, false, false),
            registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), parent);
    }
}
