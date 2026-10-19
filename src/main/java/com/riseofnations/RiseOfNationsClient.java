package com.riseofnations;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class RiseOfNationsClient implements ClientModInitializer {

    private static KeyMapping openShopMenu;

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> client.setScreen(new RegionSelectionScreen()));
        });

        openShopMenu = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.riseofnations.open_shop_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                KeyMapping.Category.MISC
            )
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openShopMenu.consumeClick()) {
                if (client.player != null) {
                    client.setScreen(new ShopScreen());
                }
            }
        });
    }
}
