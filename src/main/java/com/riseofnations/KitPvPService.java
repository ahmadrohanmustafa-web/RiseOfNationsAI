package com.riseofnations;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Map;

public final class KitPvPService {
    private KitPvPService() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("kit")
                .executes(context -> showHelp(context.getSource().getPlayerOrException()))
                .then(Commands.literal("list")
                    .executes(context -> showHelp(context.getSource().getPlayerOrException())))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        builder.suggest("warrior");
                        builder.suggest("archer");
                        builder.suggest("tank");
                        builder.suggest("assassin");
                        builder.suggest("healer");
                        builder.suggest("mage");
                        return builder.buildFuture();
                    })
                    .executes(context -> applyKit(
                        context.getSource().getPlayerOrException(),
                        StringArgumentType.getString(context, "name")))))
        );
    }

    private static int showHelp(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§lPVP KITS §7— press F6-F11 to switch instantly."));
        player.sendSystemMessage(Component.literal("§eF6 Warrior §7| §eF7 Archer §7| §eF8 Tank"));
        player.sendSystemMessage(Component.literal("§eF9 Assassin §7| §eF10 Healer §7| §eF11 Mage"));
        player.sendSystemMessage(Component.literal("Use /kit <name>. Selecting a kit replaces your inventory and armor."));
        return 1;
    }

    private static int applyKit(ServerPlayer player, String requested) {
        String name = requested.toLowerCase(Locale.ROOT);
        if (!Map.of("warrior", 1, "archer", 1, "tank", 1, "assassin", 1, "healer", 1, "mage", 1).containsKey(name)) {
            player.sendSystemMessage(Component.literal("Unknown kit. Use /kit list."));
            return 0;
        }

        // This is a dedicated kit-PvP loadout: old carried items are replaced.
        player.getInventory().clearContent();
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

        switch (name) {
            case "warrior" -> {
                armor(player, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                give(player, Items.IRON_SWORD, 1);
                give(player, Items.SHIELD, 1);
                give(player, Items.COOKED_BEEF, 16);
                give(player, Items.GOLDEN_APPLE, 2);
            }
            case "archer" -> {
                armor(player, Items.CHAINMAIL_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
                give(player, Items.BOW, 1);
                give(player, Items.ARROW, 48);
                give(player, Items.STONE_SWORD, 1);
                give(player, Items.COOKED_BEEF, 16);
                give(player, Items.GOLDEN_APPLE, 1);
            }
            case "tank" -> {
                armor(player, Items.DIAMOND_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
                give(player, Items.IRON_AXE, 1);
                give(player, Items.SHIELD, 1);
                give(player, Items.COOKED_BEEF, 24);
                give(player, Items.GOLDEN_APPLE, 2);
            }
            case "assassin" -> {
                armor(player, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
                give(player, Items.IRON_SWORD, 1);
                give(player, Items.ENDER_PEARL, 8);
                give(player, Items.SNOWBALL, 16);
                give(player, Items.COOKED_BEEF, 16);
                give(player, Items.GOLDEN_APPLE, 2);
            }
            case "healer" -> {
                armor(player, Items.IRON_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.IRON_BOOTS);
                give(player, Items.IRON_SWORD, 1);
                give(player, Items.GOLDEN_APPLE, 6);
                give(player, Items.COOKED_BEEF, 24);
                give(player, Items.SHIELD, 1);
            }
            case "mage" -> {
                armor(player, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
                give(player, Items.IRON_SWORD, 1);
                give(player, Items.BOW, 1);
                give(player, Items.ARROW, 32);
                give(player, Items.SNOWBALL, 32);
                give(player, Items.ENDER_PEARL, 4);
                give(player, Items.GOLDEN_APPLE, 2);
            }
        }

        player.getInventory().setChanged();
        player.sendSystemMessage(Component.literal("§aPVP kit equipped: §f" + title(name) + "§a. Good luck!"));
        player.sendSystemMessage(Component.literal("§7Kit switching replaces your carried items and armor."));
        return 1;
    }

    private static void armor(ServerPlayer player, Item helmet, Item chest, Item legs, Item boots) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(boots));
    }

    private static void give(ServerPlayer player, Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static String title(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
