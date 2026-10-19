package com.riseofnations;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class KitPvPHud {
    private KitPvPHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("riseofnations", "kit_pvp_hud"),
            KitPvPHud::render
        );
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) return;

        int panelWidth = 190;
        int x = client.getWindow().getGuiScaledWidth() - panelWidth - 8;
        int y = 8;
        graphics.fill(x, y, x + panelWidth, y + 20, 0xD91A2333);
        graphics.drawString(client.font, Component.literal("⚔ KIT PVP"), x + 8, y + 6, 0xFFFFD36A, false);
        y += 24;

        int totalPlayers = client.level.players().size();
        graphics.fill(x, y, x + panelWidth, y + 18, 0xC91A2333);
        graphics.drawString(client.font, Component.literal("Players in world: " + totalPlayers), x + 7, y + 5, 0xFFFFFFFF, false);
        y += 21;

        int shown = 0;
        for (Player other : client.level.players()) {
            if (other == client.player) continue;
            if (shown >= 6) break;
            String line = other.getGameProfile().name() + "  " + Math.round(other.getHealth()) + "❤";
            graphics.fill(x, y, x + panelWidth, y + 16, 0xA8222C3C);
            graphics.drawString(client.font, Component.literal(line), x + 7, y + 4, 0xFF8FE8FF, false);
            y += 18;
            shown++;
        }

        int aiShown = 0;
        for (LivingEntity entity : client.level.getEntitiesOfClass(
            LivingEntity.class,
            client.player.getBoundingBox().inflate(24.0),
            living -> living != client.player && living.isAlive() && living instanceof Enemy
        )) {
            if (aiShown >= 3 || y > client.getWindow().getGuiScaledHeight() - 50) break;
            String line = "AI: " + entity.getName().getString() + "  " + Math.round(entity.getHealth()) + "❤";
            graphics.fill(x, y, x + panelWidth, y + 16, 0xA83A2020);
            graphics.drawString(client.font, Component.literal(line), x + 7, y + 4, 0xFFFF9A8A, false);
            y += 18;
            aiShown++;
        }

        if (shown == 0 && aiShown == 0) {
            graphics.fill(x, y, x + panelWidth, y + 18, 0xA8222C3C);
            graphics.drawString(client.font, Component.literal("Waiting for PvP opponents..."), x + 7, y + 5, 0xFFD8E0EA, false);
            y += 20;
        }

        graphics.drawString(client.font, Component.literal("K: Kits  |  F6-F11: Quick switch"), x + 3, y + 3, 0xFFE3E8F0, false);
    }
}
