package com.riseofnations;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class RiseOfNations implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> {
                dispatcher.register(
                    Commands.literal("ron")
                        .executes(context -> showStatus(
                            context.getSource().getPlayerOrException()
                        ))
                        .then(Commands.literal("status")
                            .executes(context -> showStatus(
                                context.getSource().getPlayerOrException()
                            ))
                        )
                        .then(Commands.literal("job")
                            .then(Commands.literal("farmer")
                                .executes(context -> chooseJob(
                                    context.getSource().getPlayerOrException(), "farmer"
                                )))
                            .then(Commands.literal("blacksmith")
                                .executes(context -> chooseJob(
                                    context.getSource().getPlayerOrException(), "blacksmith"
                                )))
                            .then(Commands.literal("merchant")
                                .executes(context -> chooseJob(
                                    context.getSource().getPlayerOrException(), "merchant"
                                )))
                            .then(Commands.literal("soldier")
                                .executes(context -> chooseJob(
                                    context.getSource().getPlayerOrException(), "soldier"
                                )))
                        )
                        .then(Commands.literal("work")
                            .executes(context -> work(
                                context.getSource().getPlayerOrException()
                            ))
                        )
                        .then(Commands.literal("buy")
                            .then(Commands.literal("bread")
                                .executes(context -> buy(
                                    context.getSource().getPlayerOrException(),
                                    Items.BREAD, 10, "Bread"
                                )))
                            .then(Commands.literal("iron")
                                .executes(context -> buy(
                                    context.getSource().getPlayerOrException(),
                                    Items.IRON_INGOT, 30, "Iron Ingot"
                                )))
                            .then(Commands.literal("golden_apple")
                                .executes(context -> buy(
                                    context.getSource().getPlayerOrException(),
                                    Items.GOLDEN_APPLE, 100, "Golden Apple"
                                )))
                        )
                );
            }
        );
    }

    private static int showStatus(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("=== RISE OF NATIONS ==="));
        player.sendSystemMessage(Component.literal("Life: Ordinary Citizen"));
        player.sendSystemMessage(Component.literal("Profession: " + formatJob(getJob(player))));
        player.sendSystemMessage(Component.literal("Money: " + getMoney(player) + " coins"));
        player.sendSystemMessage(Component.literal("Press O for careers, P for the village shop."));
        player.sendSystemMessage(Component.literal("Use /ron work to earn coins."));
        return Command.SINGLE_SUCCESS;
    }

    private static int chooseJob(ServerPlayer player, String job) {
        String currentJob = getJob(player);

        if (!currentJob.equals("citizen")) {
            player.sendSystemMessage(Component.literal(
                "Your profession is permanent! You are already a "
                    + formatJob(currentJob) + ". You cannot change careers."
            ));
            return 0;
        }

        player.addTag("ron_job_" + job);
        player.sendSystemMessage(Component.literal("Career selected: " + formatJob(job) + "!"));
        player.sendSystemMessage(Component.literal("Your profession is now permanent. Choose wisely!"));
        player.sendSystemMessage(Component.literal("Starting money: " + getMoney(player) + " coins."));
        return Command.SINGLE_SUCCESS;
    }

    private static int work(ServerPlayer player) {
        String job = getJob(player);

        if (job.equals("citizen")) {
            player.sendSystemMessage(Component.literal(
                "Choose a profession before working. Press O to open careers."
            ));
            return 0;
        }

        int reward = switch (job) {
            case "farmer" -> 10;
            case "blacksmith" -> 20;
            case "merchant" -> 15;
            case "soldier" -> 8;
            default -> 0;
        };

        int total = getMoney(player) + reward;
        setMoney(player, total);
        player.sendSystemMessage(Component.literal(
            "Work completed! You earned " + reward + " coins. Total money: " + total + " coins."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int buy(ServerPlayer player, Item item, int price, String itemName) {
        int balance = getMoney(player);

        if (balance < price) {
            player.sendSystemMessage(Component.literal(
                "Not enough money! " + itemName + " costs " + price
                    + " coins. You have " + balance + " coins."
            ));
            return 0;
        }

        ItemStack stack = new ItemStack(item, 1);
        if (!player.getInventory().add(stack)) {
            player.sendSystemMessage(Component.literal(
                "Your inventory is full! Make space before buying."
            ));
            return 0;
        }

        setMoney(player, balance - price);
        player.sendSystemMessage(Component.literal(
            "Purchased " + itemName + " for " + price + " coins. Remaining balance: "
                + (balance - price) + " coins."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static String getJob(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_job_")) {
                return tag.substring("ron_job_".length());
            }
        }
        return "citizen";
    }

    private static String formatJob(String job) {
        return switch (job) {
            case "farmer" -> "Farmer";
            case "blacksmith" -> "Blacksmith";
            case "merchant" -> "Merchant";
            case "soldier" -> "Soldier";
            default -> "Ordinary Citizen (No Profession)";
        };
    }

    private static int getMoney(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_money_")) {
                try {
                    return Integer.parseInt(tag.substring("ron_money_".length()));
                } catch (NumberFormatException ignored) {
                    // Repair an invalid balance below.
                }
            }
        }
        setMoney(player, 100);
        return 100;
    }

    private static void setMoney(ServerPlayer player, int amount) {
        List<String> oldTags = new ArrayList<>();
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_money_")) {
                oldTags.add(tag);
            }
        }
        for (String tag : oldTags) {
            player.removeTag(tag);
        }
        player.addTag("ron_money_" + Math.max(0, amount));
    }
}
