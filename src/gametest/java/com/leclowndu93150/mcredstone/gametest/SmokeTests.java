package com.leclowndu93150.mcredstone.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;

public class SmokeTests {
    @GameTest
    public void alternateCurrentLoaded(GameTestHelper helper) {
        helper.assertTrue(FabricLoader.getInstance().isModLoaded("alternate-current"), "Alternate Current is not loaded");
        helper.succeed();
    }
}
