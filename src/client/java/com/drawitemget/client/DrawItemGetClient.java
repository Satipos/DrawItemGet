package com.drawitemget.client;

import com.drawitemget.net.DrawNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * Client entry point — registers S2C receivers.
 */
public class DrawItemGetClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GrantedToast.register();
        ClientPlayNetworking.registerGlobalReceiver(DrawNetwork.OpenDrawScreenPayload.TYPE,
                (payload, ctx) -> Minecraft.getInstance().execute(() ->
                        Minecraft.getInstance().setScreen(new DrawScreen())));

        ClientPlayNetworking.registerGlobalReceiver(DrawNetwork.GrantedItemPayload.TYPE,
                (payload, ctx) -> Minecraft.getInstance().execute(() -> {
                    GrantedToast.show(payload.itemId(), payload.displayName(), payload.confidence());
                }));
    }
}
