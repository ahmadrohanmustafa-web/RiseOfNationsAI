package com.riseofnations;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class RiseOfNationsClient implements ClientModInitializer {
    private static KeyMapping openKitMenu;
    private static KeyMapping warriorKit;
    private static KeyMapping archerKit;
    private static KeyMapping tankKit;
    private static KeyMapping assassinKit;
    private static KeyMapping healerKit;
    private static KeyMapping mageKit;

    @Override
    public void onInitializeClient() {
        KitPvPHud.register();

        openKitMenu = registerKey("key.riseofnations.open_kit_menu", GLFW.GLFW_KEY_K);
        warriorKit = registerKey("key.riseofnations.kit_warrior", GLFW.GLFW_KEY_F6);
        archerKit = registerKey("key.riseofnations.kit_archer", GLFW.GLFW_KEY_F7);
        tankKit = registerKey("key.riseofnations.kit_tank", GLFW.GLFW_KEY_F8);
        assassinKit = registerKey("key.riseofnations.kit_assassin", GLFW.GLFW_KEY_F9);
        healerKit = registerKey("key.riseofnations.kit_healer", GLFW.GLFW_KEY_F10);
        mageKit = registerKey("key.riseofnations.kit_mage", GLFW.GLFW_KEY_F11);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKitMenu.consumeClick()) {
                if (client.player != null) client.setScreen(new KitSelectionScreen());
            }
            while (warriorKit.consumeClick()) sendKit(client, "warrior");
            while (archerKit.consumeClick()) sendKit(client, "archer");
            while (tankKit.consumeClick()) sendKit(client, "tank");
            while (assassinKit.consumeClick()) sendKit(client, "assassin");
            while (healerKit.consumeClick()) sendKit(client, "healer");
            while (mageKit.consumeClick()) sendKit(client, "mage");
        });
    }

    private static KeyMapping registerKey(String translationKey, int keyCode) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
            translationKey,
            InputConstants.Type.KEYSYM,
            keyCode,
            KeyMapping.Category.MISC
        ));
    }

    private static void sendKit(Minecraft client, String kit) {
        if (client.player != null && client.player.connection != null) {
            client.player.connection.sendCommand("kit " + kit);
        }
    }
}
