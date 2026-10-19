package com.riseofnations;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RiseOfNations implements ModInitializer {

    private static final int KINGDOM_FOUNDING_COST = 500;

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (getFactionSlug(player) == null) {
                player.sendSystemMessage(Component.literal("Welcome to Rise of Nations. Choose your starting region on the map screen."));
            }
        });

        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("ron")
                    .executes(context -> showStatus(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("status")
                        .executes(context -> showStatus(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("region")
                        .then(Commands.argument("name", StringArgumentType.word())
                            .executes(context -> chooseFaction(
                                context.getSource().getPlayerOrException(),
                                StringArgumentType.getString(context, "name")))))
                    .then(Commands.literal("job")
                        .then(Commands.literal("farmer")
                            .executes(context -> chooseJob(context.getSource().getPlayerOrException(), "farmer")))
                        .then(Commands.literal("blacksmith")
                            .executes(context -> chooseJob(context.getSource().getPlayerOrException(), "blacksmith")))
                        .then(Commands.literal("merchant")
                            .executes(context -> chooseJob(context.getSource().getPlayerOrException(), "merchant")))
                        .then(Commands.literal("soldier")
                            .executes(context -> chooseJob(context.getSource().getPlayerOrException(), "soldier"))))
                    .then(Commands.literal("work")
                        .executes(context -> work(context.getSource().getPlayerOrException())))
                    .then(Commands.literal("buy")
                        .then(Commands.literal("bread")
                            .executes(context -> buy(context.getSource().getPlayerOrException(), Items.BREAD, 10, "Bread")))
                        .then(Commands.literal("iron")
                            .executes(context -> buy(context.getSource().getPlayerOrException(), Items.IRON_INGOT, 30, "Iron Ingot")))
                        .then(Commands.literal("golden_apple")
                            .executes(context -> buy(context.getSource().getPlayerOrException(), Items.GOLDEN_APPLE, 100, "Golden Apple"))))
                    .then(Commands.literal("kingdom")
                        .then(Commands.literal("found")
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> foundKingdom(
                                    context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "name"),
                                    context.getSource().getServer()))))
                        .then(Commands.literal("status")
                            .executes(context -> kingdomStatus(context.getSource().getPlayerOrException(), context.getSource().getServer())))
                        .then(Commands.literal("invite")
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> invitePlayer(
                                    context.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("join")
                            .then(Commands.argument("king", EntityArgument.player())
                                .executes(context -> joinKingdom(
                                    context.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(context, "king")))))
                        .then(Commands.literal("leave")
                            .executes(context -> leaveKingdom(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("declarewar")
                            .then(Commands.argument("king", EntityArgument.player())
                                .executes(context -> setWar(
                                    context.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(context, "king"), true))))
                        .then(Commands.literal("peace")
                            .then(Commands.argument("king", EntityArgument.player())
                                .executes(context -> setWar(
                                    context.getSource().getPlayerOrException(),
                                    EntityArgument.getPlayer(context, "king"), false)))))
            )
        );
    }

    private static int chooseFaction(ServerPlayer player, String requestedFaction) {
        String faction = requestedFaction.toLowerCase(java.util.Locale.ROOT);
        if (!List.of("northreach", "sunvale", "ironhold", "stormhaven").contains(faction)) {
            player.sendSystemMessage(Component.literal("That region is not available."));
            return 0;
        }
        String current = getFactionSlug(player);
        if (current != null) {
            player.sendSystemMessage(Component.literal("Your starting region is already " + displayName(current) + ". Your choice is permanent."));
            return 0;
        }
        player.addTag("ron_faction_" + faction);
        player.addTag("ron_story_region_chosen");
        player.sendSystemMessage(Component.literal("CHAPTER 1 — THE OATH"));
        player.sendSystemMessage(Component.literal("You have entered " + displayName(faction) + ". Your new life begins here."));
        player.sendSystemMessage(Component.literal("OBJECTIVE: Find the Job Center and choose a profession."));
        player.sendSystemMessage(Component.literal("Next: visit the village market to buy supplies and meet the townspeople."));
        return Command.SINGLE_SUCCESS;
    }

    private static int showStatus(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("=== RISE OF NATIONS ==="));
        player.sendSystemMessage(Component.literal("Life: Ordinary Citizen"));
        player.sendSystemMessage(Component.literal("Profession: " + formatJob(getJob(player))));
        player.sendSystemMessage(Component.literal("Money: " + getMoney(player) + " coins"));
        player.sendSystemMessage(Component.literal("Kingdom: " + getKingdomName(player)));
        player.sendSystemMessage(Component.literal("Nation: " + getFactionName(player)));
        player.sendSystemMessage(Component.literal("Visit the Job Center to choose a profession. Press P for the current test shop."));
        player.sendSystemMessage(Component.literal("Commands: /ron work, /ron kingdom status"));
        return Command.SINGLE_SUCCESS;
    }

    private static int chooseJob(ServerPlayer player, String job) {
        String currentJob = getJob(player);
        if (!currentJob.equals("citizen")) {
            player.sendSystemMessage(Component.literal(
                "Your profession is permanent! You are already a " + formatJob(currentJob) + ". You cannot change careers."
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
            player.sendSystemMessage(Component.literal("Choose a profession at the Job Center before working."));
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
                "Not enough money! " + itemName + " costs " + price + " coins. You have " + balance + " coins."
            ));
            return 0;
        }

        ItemStack stack = new ItemStack(item, 1);
        if (!player.getInventory().add(stack)) {
            player.sendSystemMessage(Component.literal("Your inventory is full! Make space before buying."));
            return 0;
        }

        setMoney(player, balance - price);
        player.sendSystemMessage(Component.literal(
            "Purchased " + itemName + " for " + price + " coins. Remaining balance: " + (balance - price) + " coins."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int foundKingdom(ServerPlayer player, String requestedName, MinecraftServer server) {
        if (getKingdomSlug(player) != null) {
            player.sendSystemMessage(Component.literal("You already belong to a kingdom. Leave it before founding another."));
            return 0;
        }
        String slug = toSlug(requestedName);
        if (slug.isEmpty()) {
            player.sendSystemMessage(Component.literal("Choose a name containing letters or numbers."));
            return 0;
        }
        if (getMoney(player) < KINGDOM_FOUNDING_COST) {
            player.sendSystemMessage(Component.literal(
                "Founding a kingdom costs " + KINGDOM_FOUNDING_COST + " coins. You have " + getMoney(player) + "."
            ));
            return 0;
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (slug.equals(getKingdomSlug(online))) {
                player.sendSystemMessage(Component.literal("A kingdom with that name already exists."));
                return 0;
            }
        }

        setMoney(player, getMoney(player) - KINGDOM_FOUNDING_COST);
        player.addTag("ron_kingdom_" + slug);
        player.addTag("ron_rank_king");
        player.sendSystemMessage(Component.literal("Kingdom founded: " + displayName(slug) + "!"));
        player.sendSystemMessage(Component.literal("You are its King/Queen. Invite players with /ron kingdom invite <player>."));
        player.sendSystemMessage(Component.literal("Cost: " + KINGDOM_FOUNDING_COST + " coins."));
        return Command.SINGLE_SUCCESS;
    }

    private static int kingdomStatus(ServerPlayer player, MinecraftServer server) {
        String slug = getKingdomSlug(player);
        if (slug == null) {
            player.sendSystemMessage(Component.literal("You are not in a kingdom. Found one with /ron kingdom found <name> (500 coins)."));
            return Command.SINGLE_SUCCESS;
        }

        player.sendSystemMessage(Component.literal("=== KINGDOM STATUS ==="));
        player.sendSystemMessage(Component.literal("Kingdom: " + displayName(slug)));
        player.sendSystemMessage(Component.literal("Your rank: " + (player.getTags().contains("ron_rank_king") ? "King/Queen" : "Citizen")));
        int members = 0;
        int wars = 0;
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (slug.equals(getKingdomSlug(online))) members++;
        }
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_war_")) wars++;
        }
        player.sendSystemMessage(Component.literal("Online kingdom members: " + members));
        player.sendSystemMessage(Component.literal("Wars recorded on you: " + wars));
        return Command.SINGLE_SUCCESS;
    }

    private static int invitePlayer(ServerPlayer king, ServerPlayer target) {
        String slug = getKingdomSlug(king);
        if (slug == null || !king.getTags().contains("ron_rank_king") || !slug.equals(getOwnKingdomSlug(king))) {
            king.sendSystemMessage(Component.literal("Only a kingdom's founder can invite players."));
            return 0;
        }
        if (king == target) {
            king.sendSystemMessage(Component.literal("You are already the ruler of your kingdom."));
            return 0;
        }
        if (getKingdomSlug(target) != null) {
            king.sendSystemMessage(Component.literal(target.getName().getString() + " already belongs to a kingdom."));
            return 0;
        }
        target.addTag("ron_invite_" + slug);
        king.sendSystemMessage(Component.literal("Invitation sent to " + target.getName().getString() + "."));
        target.sendSystemMessage(Component.literal(
            "You were invited to " + displayName(slug) + ". Type /ron kingdom join " + king.getName().getString() + " to join."
        ));
        return Command.SINGLE_SUCCESS;
    }

    private static int joinKingdom(ServerPlayer player, ServerPlayer king) {
        String slug = getOwnKingdomSlug(king);
        if (slug == null || !king.getTags().contains("ron_rank_king")) {
            player.sendSystemMessage(Component.literal("That player is not a kingdom ruler."));
            return 0;
        }
        if (getKingdomSlug(player) != null) {
            player.sendSystemMessage(Component.literal("You already belong to a kingdom. Use /ron kingdom leave first."));
            return 0;
        }
        if (!player.getTags().contains("ron_invite_" + slug)) {
            player.sendSystemMessage(Component.literal("You need an invitation from that kingdom's ruler first."));
            return 0;
        }
        player.removeTag("ron_invite_" + slug);
        player.addTag("ron_member_" + slug);
        player.sendSystemMessage(Component.literal("You joined the kingdom of " + displayName(slug) + "!"));
        king.sendSystemMessage(Component.literal(player.getName().getString() + " joined your kingdom."));
        return Command.SINGLE_SUCCESS;
    }

    private static int leaveKingdom(ServerPlayer player) {
        String slug = getKingdomSlug(player);
        if (slug == null) {
            player.sendSystemMessage(Component.literal("You are not in a kingdom."));
            return 0;
        }
        if (player.getTags().contains("ron_rank_king")) {
            player.sendSystemMessage(Component.literal("A ruler cannot abandon their kingdom in this version. Transfer and succession are planned for a later update."));
            return 0;
        }
        player.removeTag("ron_member_" + slug);
        player.sendSystemMessage(Component.literal("You left the kingdom of " + displayName(slug) + "."));
        return Command.SINGLE_SUCCESS;
    }

    private static int setWar(ServerPlayer ruler, ServerPlayer other, boolean declareWar) {
        String ownSlug = getOwnKingdomSlug(ruler);
        String otherSlug = getOwnKingdomSlug(other);
        if (ownSlug == null || otherSlug == null
            || !ruler.getTags().contains("ron_rank_king")
            || !other.getTags().contains("ron_rank_king")) {
            ruler.sendSystemMessage(Component.literal("Both players must be kingdom rulers to manage diplomacy."));
            return 0;
        }
        if (ownSlug.equals(otherSlug)) {
            ruler.sendSystemMessage(Component.literal("You cannot declare war on your own kingdom."));
            return 0;
        }

        String ownWarTag = "ron_war_" + otherSlug;
        String otherWarTag = "ron_war_" + ownSlug;
        if (declareWar) {
            ruler.addTag(ownWarTag);
            other.addTag(otherWarTag);
            ruler.sendSystemMessage(Component.literal("WAR DECLARED against " + displayName(otherSlug) + "!"));
            other.sendSystemMessage(Component.literal("Your kingdom is now at war with " + displayName(ownSlug) + "!"));
        } else {
            ruler.removeTag(ownWarTag);
            other.removeTag(otherWarTag);
            ruler.sendSystemMessage(Component.literal("Peace declared with " + displayName(otherSlug) + "."));
            other.sendSystemMessage(Component.literal("Peace restored with " + displayName(ownSlug) + "."));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String getJob(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_job_")) return tag.substring("ron_job_".length());
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
                    // Repair invalid balances below.
                }
            }
        }
        setMoney(player, 100);
        return 100;
    }

    private static void setMoney(ServerPlayer player, int amount) {
        List<String> oldTags = new ArrayList<>();
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_money_")) oldTags.add(tag);
        }
        for (String tag : oldTags) player.removeTag(tag);
        player.addTag("ron_money_" + Math.max(0, amount));
    }

    private static String getOwnKingdomSlug(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_kingdom_")) return tag.substring("ron_kingdom_".length());
        }
        return null;
    }

    private static String getKingdomSlug(ServerPlayer player) {
        String own = getOwnKingdomSlug(player);
        if (own != null) return own;
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_member_")) return tag.substring("ron_member_".length());
        }
        return null;
    }

    private static String getFactionSlug(ServerPlayer player) {
        for (String tag : player.getTags()) {
            if (tag.startsWith("ron_faction_")) return tag.substring("ron_faction_".length());
        }
        return null;
    }

    private static String getFactionName(ServerPlayer player) {
        String faction = getFactionSlug(player);
        return faction == null ? "Unchosen" : displayName(faction);
    }

    private static String getKingdomName(ServerPlayer player) {
        String slug = getKingdomSlug(player);
        return slug == null ? "None" : displayName(slug);
    }

    private static String toSlug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        return slug.length() > 24 ? slug.substring(0, 24) : slug;
    }

    private static String displayName(String slug) {
        String[] parts = slug.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.isEmpty() ? "Unnamed Kingdom" : result.toString();
    }
}
