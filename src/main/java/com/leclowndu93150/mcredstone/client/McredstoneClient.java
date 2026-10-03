package com.leclowndu93150.mcredstone.client;

import com.leclowndu93150.mcredstone.net.PlayModePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class McredstoneClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PlayMode.registerKeys();
        DevWorld.register();
        ClientPlayNetworking.registerGlobalReceiver(PlayModePayload.TYPE, (payload, context) -> PlayMode.set(payload.machine()));
        ClientTickEvents.END_CLIENT_TICK.register(PlayMode::tick);
    }
}
