package com.riseofnations.client;

import com.riseofnations.net.CivilizationStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client entrypoint. Only runs on a real game client, never on a dedicated server. */
public final class RiseOfNationsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CivilizationStatePayload.TYPE, (payload, context) -> {
            Minecraft client = context.client();
            client.execute(() -> ClientCivilizations.onState(client, payload.data()));
        });
    }
}
