package com.riseofnations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ShopScreen extends Screen {

    public ShopScreen() {
        super(Component.literal("Rise of Nations: Village Shop"));
    }

    @Override
    protected void init() {
        int width = 240;
        int height = 24;
        int x = (this.width - width) / 2;
        int y = (this.height - 110) / 2;

        addRenderableWidget(Button.builder(
            Component.literal("Bread - 10 coins"),
            button -> buy("bread")
        ).bounds(x, y, width, height).build());

        addRenderableWidget(Button.builder(
            Component.literal("Iron Ingot - 30 coins"),
            button -> buy("iron")
        ).bounds(x, y + 30, width, height).build());

        addRenderableWidget(Button.builder(
            Component.literal("Golden Apple - 100 coins"),
            button -> buy("golden_apple")
        ).bounds(x, y + 60, width, height).build());

        addRenderableWidget(Button.builder(
            Component.literal("Close"),
            button -> Minecraft.getInstance().setScreen(null)
        ).bounds(x, y + 90, width, height).build());
    }

    private void buy(String item) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand("ron buy " + item);
        }
        minecraft.setScreen(null);
    }
}
