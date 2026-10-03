package com.leclowndu93150.mcredstone;

import com.leclowndu93150.mcredstone.command.McrCommands;
import com.leclowndu93150.mcredstone.machine.AutoBuild;
import com.leclowndu93150.mcredstone.machine.PlayInputs;
import com.leclowndu93150.mcredstone.machine.Tasks;
import com.leclowndu93150.mcredstone.net.InputPayload;
import com.leclowndu93150.mcredstone.net.PlayModePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class Mcredstone implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(InputPayload.TYPE, InputPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayModePayload.TYPE, PlayModePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(InputPayload.TYPE, (payload, context) -> PlayInputs.handle(context.server(), context.player(), payload));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> McrCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(Tasks::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(AutoBuild::start);
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> AutoBuild.join(listener.player));
    }
}
