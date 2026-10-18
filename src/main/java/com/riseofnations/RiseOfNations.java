
package com.riseofnations;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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
                                    context.getSource().getPlayerOrException(),
                                    "farmer"
                                ))
                            )
                            .then(Commands.literal("blacksmith")
                                .executes(context -> chooseJob(
                                    context.getSource().getPlayerOrException(),
                                    "blacksmith"
                                ))
                            )
                            .then(Commands.literal("merchant")
                                .executes(context -> chooseJob(
                                    context.getSource().getPlayerOrException(),
                                    "merchant"
                                ))
                            )
                            .then(Commands.literal("soldier")
                                .executes(context -> chooseJob(
                                    context.getSource().getPlayerOrException(),
                                    "soldier"
                                ))
                            )
                        )
                        .then(Commands.literal("work")
                            .executes(context -> work(
                                context.getSource().getPlayerOrException()
                            ))
                        )
                );
            }
        );
    }

    private static int showStatus(ServerPlayer player) {
        String job = getJob(player);
        int money = getMoney(player);

        player.sendSystemMessage(Component.literal(
            "=== RISE OF NATIONS ==="
        ));
        player.sendSystemMessage(Component.literal(
            "Life: Ordinary Citizen"
        ));
        player.sendSystemMessage(Component.literal(
            "Profession: " + formatJob(job)
        ));
        player.sendSystemMessage(Component.literal(
            "Money: " + money + " coins"
        ));
        player.sendSystemMessage(Component.literal(
            "Use /ron work to earn money when you have a job."
        ));

        return Command.SINGLE_SUCCESS;
    }

    private static int chooseJob(ServerPlayer player, String job) {
        List<String> oldJobs = new ArrayList<>();

        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_job_")) {
                oldJobs.add(tag);
            }
        }

        for (String tag : oldJobs) {
            player.removeTag(tag);
        }

        player.addTag("ron_job_" + job);
        int money = getMoney(player);

        player.sendSystemMessage(Component.literal(
            "Career selected: " + formatJob(job) + "!"
        ));
        player.sendSystemMessage(Component.literal(
            "Your life as a citizen has begun. Money: "
                + money + " coins."
        ));

        return Command.SINGLE_SUCCESS;
    }

    private static int work(ServerPlayer player) {
        String job = getJob(player);

        if (job.equals("citizen")) {
            player.sendSystemMessage(Component.literal(
                "You have no profession yet! Press O to open the career menu."
            ));
            return 0;
        }

        int reward;

        switch (job) {
            case "farmer" -> reward = 10;
            case "blacksmith" -> reward = 20;
            case "merchant" -> reward = 15;
            case "soldier" -> reward = 8;
            default -> reward = 0;
        }

        int total = getMoney(player) + reward;
        setMoney(player, total);

        player.sendSystemMessage(Component.literal(
            "Work completed! You earned " + reward
                + " coins. Total money: " + total + " coins."
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
                    return Integer.parseInt(
                        tag.substring("ron_money_".length())
                    );
                } catch (NumberFormatException ignored) {
                    // Repair an invalid money tag below.
                }
            }
        }

        setMoney(player, 100);
        return 100;
    }

    private static void setMoney(ServerPlayer player, int amount) {
        List<String> oldMoneyTags = new ArrayList<>();

        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_money_")) {
                oldMoneyTags.add(tag);
            }
        }

        for (String tag : oldMoneyTags) {
            player.removeTag(tag);
        }

        player.addTag("ron_money_" + Math.max(0, amount));
    }
}
