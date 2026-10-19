package com.riseofnations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RegionSelectionScreen extends Screen {
    public RegionSelectionScreen() {
        super(Component.literal("Rise of Nations — Choose Your Homeland"));
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(230, this.width - 32);
        int x = (this.width - buttonWidth) / 2;
        int top = Math.max(90, (this.height - 170) / 2);

        addRegionButton(x, top, buttonWidth, "NORTHREACH  |  Highland Realm", "northreach");
        addRegionButton(x, top + 38, buttonWidth, "SUNVALE  |  Farming Heartland", "sunvale");
        addRegionButton(x, top + 76, buttonWidth, "IRONHOLD  |  Mountain Stronghold", "ironhold");
        addRegionButton(x, top + 114, buttonWidth, "STORMHAVEN  |  Coastal Traders", "stormhaven");
    }

    private void addRegionButton(int x, int y, int width, String label, String region) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> chooseRegion(region))
            .bounds(x, y, width, 30).build());
    }

    private void chooseRegion(String region) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.sendCommand("ron region " + region);
        }
        minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw a plain background instead of Screen.renderBackground: the latter
        // triggers Minecraft's blur pipeline, which can already have run this frame.
        graphics.fill(0, 0, this.width, this.height, 0xFF202A35);
        int center = this.width / 2;
        graphics.fill(center - 180, 22, center + 180, this.height - 20, 0xE91B202B);
        graphics.fill(center - 176, 26, center + 176, this.height - 24, 0xE9D8C49A);
        graphics.drawCenteredString(this.font, Component.literal("RISE OF NATIONS"), center, 38, 0xFF4A2D16);
        graphics.drawCenteredString(this.font, Component.literal("THE CHRONICLES  •  CHAPTER I: THE OATH"), center, 56, 0xFF67431F);
        graphics.drawCenteredString(this.font, Component.literal("Choose the homeland where your story begins"), center, 72, 0xFF382719);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, Component.literal("Your starting region is permanent."), center, this.height - 42, 0xFF4A2D16);
    }
}
