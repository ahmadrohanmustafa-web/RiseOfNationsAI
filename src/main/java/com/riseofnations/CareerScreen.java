
package com.riseofnations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CareerScreen extends Screen {

    public CareerScreen() {
        super(Component.literal("Rise of Nations: Careers"));
    }

    @Override
    protected void init() {
        int buttonWidth = 240;
        int buttonHeight = 24;
        int x = (this.width - buttonWidth) / 2;
        int y = (this.height - 130) / 2;

        addRenderableWidget(
            Button.builder(
                Component.literal("Farmer - Earn 10 coins per work"),
                button -> chooseCareer("farmer")
            ).bounds(x, y, buttonWidth, buttonHeight).build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal("Blacksmith - Earn 20 coins per work"),
                button -> chooseCareer("blacksmith")
            ).bounds(x, y + 30, buttonWidth, buttonHeight).build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal("Merchant - Earn 15 coins per work"),
                button -> chooseCareer("merchant")
            ).bounds(x, y + 60, buttonWidth, buttonHeight).build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal("Soldier - Earn 8 coins per work"),
                button -> chooseCareer("soldier")
            ).bounds(x, y + 90, buttonWidth, buttonHeight).build()
        );

        addRenderableWidget(
            Button.builder(
                Component.literal("Close"),
                button -> Minecraft.getInstance().setScreen(null)
            ).bounds(x, y + 120, buttonWidth, buttonHeight).build()
        );
    }

    private void chooseCareer(String career) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand("ron job " + career);
        }

        minecraft.setScreen(null);
    }
}
