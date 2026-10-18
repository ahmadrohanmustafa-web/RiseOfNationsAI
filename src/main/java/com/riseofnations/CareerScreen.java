
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

        if (Minecraft.getInstance().player != null) {
            boolean hasJob = Minecraft.getInstance().player.getTags()
                .stream()
                .anyMatch(tag -> tag.startsWith("ron_job_"));

            if (hasJob) {
                addRenderableWidget(
                    Button.builder(
                        Component.literal("Your profession is permanent!"),
                        button -> {}
                    ).bounds(x, y, buttonWidth, buttonHeight).build()
                );

                addRenderableWidget(
                    Button.builder(
                        Component.literal("Close"),
                        button -> Minecraft.getInstance().setScreen(null)
                    ).bounds(x, y + 35, buttonWidth, buttonHeight).build()
                );

                return;
            }
        }

        addCareerButton(x, y, "Farmer - Earn 10 coins", "farmer");
        addCareerButton(x, y + 30, "Blacksmith - Earn 20 coins", "blacksmith");
        addCareerButton(x, y + 60, "Merchant - Earn 15 coins", "merchant");
        addCareerButton(x, y + 90, "Soldier - Earn 8 coins", "soldier");

        addRenderableWidget(
            Button.builder(
                Component.literal("Close"),
                button -> Minecraft.getInstance().setScreen(null)
            ).bounds(x, y + 120, buttonWidth, buttonHeight).build()
        );
    }

    private void addCareerButton(int x, int y, String label, String career) {
        addRenderableWidget(
            Button.builder(
                Component.literal(label),
                button -> chooseCareer(career)
            ).bounds(x, y, 240, 24).build()
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
