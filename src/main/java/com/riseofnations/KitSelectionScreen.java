package com.riseofnations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class KitSelectionScreen extends Screen {
    public KitSelectionScreen() {
        super(Component.literal("Kit PvP Arena — Choose Your Kit"));
    }

    @Override
    protected void init() {
        int buttonWidth = 220;
        int buttonHeight = 22;
        int x = (this.width - buttonWidth) / 2;
        int y = Math.max(42, (this.height - 180) / 2);

        addKit(x, y, buttonWidth, buttonHeight, "Warrior — balanced melee", "warrior");
        addKit(x, y + 26, buttonWidth, buttonHeight, "Archer — bow and arrows", "archer");
        addKit(x, y + 52, buttonWidth, buttonHeight, "Tank — heavy armor", "tank");
        addKit(x, y + 78, buttonWidth, buttonHeight, "Assassin — fast and mobile", "assassin");
        addKit(x, y + 104, buttonWidth, buttonHeight, "Healer — extra golden apples", "healer");
        addKit(x, y + 130, buttonWidth, buttonHeight, "Mage — ranged utility", "mage");

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> Minecraft.getInstance().setScreen(null))
            .bounds(x, y + 158, buttonWidth, buttonHeight).build());
    }

    private void addKit(int x, int y, int width, int height, String label, String kit) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.connection.sendCommand("kit " + kit);
            }
            minecraft.setScreen(null);
        }).bounds(x, y, width, height).build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF111824);
        graphics.fill(this.width / 2 - 145, 16, this.width / 2 + 145, this.height - 14, 0xE9253447);
        graphics.drawCenteredString(this.font, "KIT PVP ARENA", this.width / 2, 24, 0xFFFFD36A);
        graphics.drawCenteredString(this.font, "Choose a loadout. Switching replaces carried items and armor.", this.width / 2, 36, 0xFFE3E8F0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
