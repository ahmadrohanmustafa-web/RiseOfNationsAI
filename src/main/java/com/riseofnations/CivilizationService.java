package com.riseofnations;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * First playable civilization layer for Rise of Nations.
 * Country state is stored on player persistent scoreboard tags so it survives relogs.
 * Distant nations are represented as lightweight data, not permanently ticking entities.
 */
public final class CivilizationService {
    private CivilizationService() {}

    private static long tickCounter = 0;
    private static final Map<UUID, Long> LAST_AUTOCONQUEST_TICK = new HashMap<>();

    private record Nation(String id, String name, String motto, String terrain, int x, int z) {}

    private static final List<Nation> NATIONS = List.of(
        n("northreach","Northreach","The crown of the frozen north","snow",0),
        n("sunvale","Sunvale","The breadbasket of the realm","plains",1),
        n("ironhold","Ironhold","Stone, steel, and unbroken oaths","stone",2),
        n("stormhaven","Stormhaven","Where every sea-road meets","coast",3),
        n("westermarch","Westermarch","The frontier never sleeps","forest",4),
        n("eastmere","Eastmere","Fields beside the silver lakes","plains",5),
        n("highcrown","Highcrown","The old royal seat","stone",6),
        n("redwyn","Redwyn","The vineyards of the west","plains",7),
        n("blackfen","Blackfen","A hard people in a hard land","swamp",8),
        n("goldcoast","Goldcoast","Fortunes ride the tide","coast",9),
        n("emberfall","Emberfall","Forged in fire","stone",10),
        n("greenmarch","Greenmarch","The deep green frontier","forest",11),
        n("frostholm","Frostholm","Winter is our shield","snow",12),
        n("dawnreach","Dawnreach","First light, first hope","plains",13),
        n("duskmere","Duskmere","Quiet waters, hidden power","swamp",14),
        n("silverkeep","Silverkeep","Honor above all treasure","stone",15),
        n("oakheart","Oakheart","Rooted in ancient forests","forest",16),
        n("rivergate","Rivergate","Trade follows the river","plains",17),
        n("sunspire","Sunspire","A beacon across the sands","desert",18),
        n("cinderlands","Cinderlands","From ash, a new age","desert",19),
        n("bluewater","Bluewater","Masters of the open sea","coast",20),
        n("wolfpine","Wolfpine","Loyalty is our law","snow",21),
        n("ravenmoor","Ravenmoor","Knowledge behind closed doors","swamp",22),
        n("stonehaven","Stonehaven","A fortress among the peaks","stone",23),
        n("meadowfen","Meadowfen","Plenty for those who work","plains",24),
        n("amberfall","Amberfall","Gold beneath the hills","stone",25),
        n("thornvale","Thornvale","Beautiful, but never defenseless","forest",26),
        n("whitecliff","Whitecliff","Our walls face the waves","coast",27),
        n("redharbor","Redharbor","Ships bring strength","coast",28),
        n("moonvale","Moonvale","A land of scholars and stars","forest",29),
        n("brightwater","Brightwater","Peace through prosperity","plains",30),
        n("grimhold","Grimhold","Discipline wins the day","stone",31),
        n("wildmere","Wildmere","No ruler owns the wilderness","swamp",32),
        n("copperfield","Copperfield","The mines feed the realm","stone",33),
        n("lavender","Lavender","A gentle land with a brave heart","plains",34),
        n("dragonspine","Dragonspine","High peaks, higher ambition","snow",35),
        n("seabreak","Seabreak","The ocean is our highway","coast",36),
        n("longford","Longford","The bridge between nations","plains",37),
        n("mistwood","Mistwood","The forest keeps its secrets","forest",38),
        n("sandreach","Sandreach","Life blooms against the odds","desert",39),
        n("redmesa","Redmesa","The desert's red fortress","desert",40),
        n("goldensteppe","Goldensteppe","Horse-lords of the open land","plains",41),
        n("nightfall","Nightfall","A quiet power in the east","forest",42),
        n("starfall","Starfall","Built for a brighter tomorrow","plains",43),
        n("crownholm","Crownholm","A crown must be earned","coast",44),
        n("ashbourne","Ashbourne","We rise from every defeat","stone",45),
        n("windmere","Windmere","Freedom beneath open skies","plains",46),
        n("deepdelve","Deepdelve","The riches lie below","stone",47),
        n("hearthland","Hearthland","Home is worth defending","forest",48),
        n("worldsedge","Worldsedge","The final frontier","snow",49)
    );

    private static Nation n(String id, String name, String motto, String terrain, int index) {
        // Every capital is separated by at least 1,600 blocks in X.
        return new Nation(id, name, motto, terrain, index * 1600, 0);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) tickStoryProgress(player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("ron")
                .then(Commands.literal("nations").executes(ctx -> listNations(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("nation")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Nation nation : NATIONS) builder.suggest(nation.id());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> chooseNation(ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("story").executes(ctx -> story(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("advance").executes(ctx -> advanceStory(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("crown").executes(ctx -> claimCrown(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("war")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Nation nation : NATIONS) builder.suggest(nation.id());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> declareNationWar(ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("conquer")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Nation nation : NATIONS) builder.suggest(nation.id());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> conquerNation(ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, "id")))))
                .then(Commands.literal("empire").executes(ctx -> empireStatus(ctx.getSource().getPlayerOrException())))
            )
        );
    }

    private static int listNations(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("=== THE 50 NATIONS ==="));
        player.sendSystemMessage(Component.literal("Choose a homeland with /ron nation <id>. Capitals are 1,600 blocks apart."));
        for (int i = 0; i < NATIONS.size(); i += 5) {
            StringBuilder line = new StringBuilder();
            for (int j = i; j < Math.min(i + 5, NATIONS.size()); j++) {
                if (!line.isEmpty()) line.append("  |  ");
                line.append(NATIONS.get(j).id());
            }
            player.sendSystemMessage(Component.literal(line.toString()));
        }
        player.sendSystemMessage(Component.literal("Your starting homeland is permanent. Use /ron story to see your objective."));
        return 1;
    }

    private static int chooseNation(ServerPlayer player, String id) {
        Nation nation = find(id);
        if (nation == null) {
            player.sendSystemMessage(Component.literal("Unknown nation. Run /ron nations to see the 50 available countries."));
            return 0;
        }
        String existing = tagValue(player, "ron_country_");
        if (existing != null) {
            player.sendSystemMessage(Component.literal("Your homeland is already " + find(existing).name() + ". Starting homeland choices are permanent."));
            return 0;
        }
        player.addTag("ron_country_" + nation.id());
        player.addTag("ron_story_stage_1");
        ServerLevel level = (ServerLevel) player.level();
        buildCapital(level, nation);
        player.teleportTo(level, nation.x() + 0.5, 101, nation.z() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        player.sendSystemMessage(Component.literal("CHAPTER I — THE ORDINARY LIFE"));
        player.sendSystemMessage(Component.literal("Welcome to " + nation.name() + ": " + nation.motto() + "."));
        player.sendSystemMessage(Component.literal("You begin as an ordinary citizen with 100 coins. No title, no army, no land."));
        player.sendSystemMessage(Component.literal("OBJECTIVE: Choose a profession with /ron job farmer|blacksmith|merchant|soldier."));
        player.sendSystemMessage(Component.literal("Your story advances automatically as you work and earn money. Use /ron story only to check your chapter."));
        return 1;
    }

    private static void buildCapital(ServerLevel level, Nation nation) {
        BlockPos marker = new BlockPos(nation.x(), 95, nation.z());
        if (level.getBlockState(marker).is(Blocks.GOLD_BLOCK)) return;
        var ground = switch (nation.terrain()) {
            case "snow" -> Blocks.SNOW_BLOCK;
            case "stone" -> Blocks.STONE_BRICKS;
            case "coast" -> Blocks.SANDSTONE;
            case "swamp" -> Blocks.MOSS_BLOCK;
            case "desert" -> Blocks.SANDSTONE;
            case "forest" -> Blocks.MOSS_BLOCK;
            default -> Blocks.GRASS_BLOCK;
        };
        var path = switch (nation.terrain()) {
            case "snow" -> Blocks.PACKED_ICE;
            case "stone" -> Blocks.COBBLESTONE;
            case "coast", "desert" -> Blocks.SANDSTONE;
            default -> Blocks.DIRT_PATH;
        };
        for (int dx = -20; dx <= 20; dx++) {
            for (int dz = -20; dz <= 20; dz++) {
                for (int dy = 96; dy <= 108; dy++) level.setBlock(new BlockPos(nation.x()+dx, dy, nation.z()+dz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(nation.x()+dx, 99, nation.z()+dz), ground.defaultBlockState(), 3);
            }
        }
        for (int i = -18; i <= 18; i++) for (int w = -2; w <= 2; w++) {
            level.setBlock(new BlockPos(nation.x()+i,100,nation.z()+w), path.defaultBlockState(),3);
            level.setBlock(new BlockPos(nation.x()+w,100,nation.z()+i), path.defaultBlockState(),3);
        }
        buildHouse(level, nation.x()-10, nation.z()+5, Blocks.SPRUCE_PLANKS, Blocks.BRICKS);
        buildHouse(level, nation.x()+5, nation.z()+5, Blocks.OAK_PLANKS, Blocks.STONE_BRICKS);
        buildHouse(level, nation.x()-3, nation.z()-13, Blocks.STONE_BRICKS, Blocks.DEEPSLATE_BRICKS);
        level.setBlock(new BlockPos(nation.x()-10,101,nation.z()+4), Blocks.LECTERN.defaultBlockState(),3);
        level.setBlock(new BlockPos(nation.x()+7,101,nation.z()+5), Blocks.BARREL.defaultBlockState(),3);
        level.setBlock(new BlockPos(nation.x(),100,nation.z()), Blocks.GOLD_BLOCK.defaultBlockState(),3);
        level.setBlock(marker, Blocks.GOLD_BLOCK.defaultBlockState(),3);
    }

    private static void buildHouse(ServerLevel level, int x, int z, net.minecraft.world.level.block.Block wall, net.minecraft.world.level.block.Block roof) {
        for (int dx=0; dx<7; dx++) for (int dz=0; dz<7; dz++) {
            level.setBlock(new BlockPos(x+dx,100,z+dz), Blocks.OAK_PLANKS.defaultBlockState(),3);
            for (int dy=1; dy<=4; dy++) {
                boolean edge=dx==0||dx==6||dz==0||dz==6;
                net.minecraft.world.level.block.Block block=edge?wall:Blocks.AIR;
                if (edge && dz==0 && dx==3 && dy<=2) block=Blocks.AIR;
                level.setBlock(new BlockPos(x+dx,100+dy,z+dz),block.defaultBlockState(),3);
            }
        }
        for(int dx=-1;dx<=7;dx++) for(int dz=-1;dz<=7;dz++)
            level.setBlock(new BlockPos(x+dx,105+Math.max(Math.abs(dx-3),Math.abs(dz-3))/2,z+dz),roof.defaultBlockState(),3);
    }

    private static int story(ServerPlayer player) {
        String country = tagValue(player,"ron_country_");
        if (country == null) {
            player.sendSystemMessage(Component.literal("PROLOGUE — You are a stranger. Choose a homeland: /ron nations, then /ron nation <id>."));
            return 1;
        }
        Nation nation=find(country);
        int stage=storyStage(player);
        player.sendSystemMessage(Component.literal("=== THE CHRONICLES OF " + nation.name().toUpperCase(Locale.ROOT) + " ==="));
        player.sendSystemMessage(Component.literal("Your rank: " + (has(player,"ron_world_king") ? "Sovereign of the Known World" : has(player,"ron_king_" + country) ? "King of " + nation.name() : stage>=4 ? "Noble / Influential Citizen" : "Ordinary Citizen")));
        switch(stage) {
            case 1 -> player.sendSystemMessage(Component.literal("CHAPTER I: Find your place in society. Choose a profession with /ron job farmer, blacksmith, merchant, or soldier."));
            case 2 -> player.sendSystemMessage(Component.literal("CHAPTER II: Build your fortune. Earn at least 250 coins through your profession; the story advances automatically."));
            case 3 -> player.sendSystemMessage(Component.literal("CHAPTER III: Become a power in the realm. Reach 1,000 coins; the story advances automatically."));
            case 4 -> player.sendSystemMessage(Component.literal("CHAPTER IV: Build your savings to 2,000 coins. Your coronation happens automatically."));
            case 5 -> player.sendSystemMessage(Component.literal("CHAPTER V: Expand your empire. Each 1,500 coins funds a campaign that resolves automatically about once per minute."));
            default -> player.sendSystemMessage(Component.literal("EPILOGUE: Your legacy is written. Use /ron empire to track your conquests."));
        }
        player.sendSystemMessage(Component.literal("Homeland: " + nation.name() + " | Coins: " + money(player) + " | Conquered nations: " + conqueredCount(player) + "/" + NATIONS.size()));
        return 1;
    }


    private static void tickStoryProgress(ServerPlayer player) {
        String country = tagValue(player, "ron_country_");
        if (country == null) return;
        int stage = storyStage(player);
        int balance = money(player);

        if (stage == 1 && !"citizen".equals(job(player))) {
            setStage(player, 2);
            player.sendSystemMessage(Component.literal("CHAPTER II — YOUR WORKING LIFE BEGINS"));
            player.sendSystemMessage(Component.literal("Your profession is recorded. Earn 250 coins to build your reputation; the story continues on its own."));
            return;
        }
        if (stage == 2 && balance >= 250) {
            setStage(player, 3);
            player.sendSystemMessage(Component.literal("CHAPTER III — A NAME IN THE REALM"));
            player.sendSystemMessage(Component.literal("Reach 1,000 coins and your rise to power continues automatically."));
            return;
        }
        if (stage == 3 && balance >= 1000) {
            setStage(player, 4);
            player.sendSystemMessage(Component.literal("CHAPTER IV — THE THRONE AWAITS"));
            player.sendSystemMessage(Component.literal("Save 2,000 coins for an automatic coronation."));
            return;
        }
        if (stage == 4 && balance >= 2000 && !has(player, "ron_king_" + country)) {
            setMoney(player, balance - 1000);
            player.addTag("ron_king_" + country);
            player.addTag("ron_war_unlocked");
            setStage(player, 5);
            player.sendSystemMessage(Component.literal("THE CORONATION — You are now ruler of " + find(country).name() + "!"));
            player.sendSystemMessage(Component.literal("Your reign begins. Campaigns against other nations resolve automatically when your treasury can fund them."));
            LAST_AUTOCONQUEST_TICK.put(player.getUUID(), tickCounter);
            return;
        }
        if (stage >= 5 && !has(player, "ron_world_king") && balance >= 1500) {
            long last = LAST_AUTOCONQUEST_TICK.getOrDefault(player.getUUID(), tickCounter);
            if (tickCounter - last < 1200) return;
            for (Nation target : NATIONS) {
                if (target.id().equals(country) || has(player, "ron_conquered_" + target.id())) continue;
                setMoney(player, money(player) - 1500);
                player.addTag("ron_conquered_" + target.id());
                LAST_AUTOCONQUEST_TICK.put(player.getUUID(), tickCounter);
                int count = conqueredCount(player);
                player.sendSystemMessage(Component.literal("ROYAL CAMPAIGN VICTORY — " + target.name() + " has joined your empire. " + count + "/49 foreign nations united."));
                if (count >= NATIONS.size() - 1) {
                    player.addTag("ron_world_king");
                    player.sendSystemMessage(Component.literal("FINAL CHAPTER — You have united all 50 nations. You are the Sovereign of the Known World!"));
                }
                return;
            }
        }
    }

    private static int advanceStory(ServerPlayer player) {
        if (tagValue(player,"ron_country_")==null) {
            player.sendSystemMessage(Component.literal("Choose your homeland first: /ron nations, then /ron nation <id>."));
            return 0;
        }
        int stage=storyStage(player);
        if (stage==1) {
            if ("citizen".equals(job(player))) {
                player.sendSystemMessage(Component.literal("Choose a profession first using /ron job farmer, blacksmith, merchant, or soldier."));
                return 0;
            }
            setStage(player,2);
            player.sendSystemMessage(Component.literal("CHAPTER II UNLOCKED — Your working life begins. Earn 250 coins."));
        } else if (stage==2) {
            if(money(player)<250) {
                player.sendSystemMessage(Component.literal("You need 250 coins to advance. Current balance: "+money(player)+"."));
                return 0;
            }
            setStage(player,3);
            player.sendSystemMessage(Component.literal("CHAPTER III UNLOCKED — Your name is spreading. Reach 1,000 coins."));
        } else if(stage==3) {
            if(money(player)<1000) {
                player.sendSystemMessage(Component.literal("You need 1,000 coins to prove your influence. Current balance: "+money(player)+"."));
                return 0;
            }
            setStage(player,4);
            player.sendSystemMessage(Component.literal("CHAPTER IV UNLOCKED — The throne is within reach. Use /ron crown."));
        } else {
            player.sendSystemMessage(Component.literal("No new chapter is ready. Read /ron story for your current objective."));
            return 0;
        }
        return 1;
    }

    private static int claimCrown(ServerPlayer player) {
        String country=tagValue(player,"ron_country_");
        if(country==null) {
            player.sendSystemMessage(Component.literal("Choose a homeland first."));
            return 0;
        }
        if(storyStage(player)<4) {
            player.sendSystemMessage(Component.literal("Your story has not reached the throne yet. Complete the objectives in /ron story."));
            return 0;
        }
        if(has(player,"ron_king_"+country)) {
            player.sendSystemMessage(Component.literal("You already wear the crown of "+find(country).name()+"."));
            return 0;
        }
        if(money(player)<1000) {
            player.sendSystemMessage(Component.literal("The coronation costs 1,000 coins. Current balance: "+money(player)+"."));
            return 0;
        }
        setMoney(player,money(player)-1000);
        player.addTag("ron_king_"+country);
        player.addTag("ron_war_unlocked");
        setStage(player,5);
        player.sendSystemMessage(Component.literal("THE CORONATION — You are now the ruler of "+find(country).name()+"!"));
        player.sendSystemMessage(Component.literal("Your next goal: expand your influence. Use /ron war <country-id> and /ron conquer <country-id>."));
        return 1;
    }

    private static int declareNationWar(ServerPlayer player, String id) {
        Nation target=find(id);
        String own=tagValue(player,"ron_country_");
        if(target==null) { player.sendSystemMessage(Component.literal("Unknown nation. Use /ron nations.")); return 0; }
        if(own==null || !has(player,"ron_king_"+own)) { player.sendSystemMessage(Component.literal("Only a crowned ruler can declare national war. Earn your crown with /ron story.")); return 0; }
        if(own.equals(target.id())) { player.sendSystemMessage(Component.literal("You cannot declare war on your own country.")); return 0; }
        String warTag="ron_nation_war_"+target.id();
        if(has(player,warTag)) { player.sendSystemMessage(Component.literal("You are already at war with "+target.name()+". Use /ron conquer "+target.id()+" after preparing 1,500 coins.")); return 1; }
        player.addTag(warTag);
        player.sendSystemMessage(Component.literal("ROYAL DECREE: "+find(own).name()+" declares war on "+target.name()+"! This prototype records your campaign; full troop battles are planned for later development."));
        return 1;
    }

    private static int conquerNation(ServerPlayer player, String id) {
        Nation target=find(id);
        String own=tagValue(player,"ron_country_");
        if(target==null) { player.sendSystemMessage(Component.literal("Unknown nation. Use /ron nations.")); return 0; }
        if(own==null || !has(player,"ron_king_"+own)) { player.sendSystemMessage(Component.literal("You must become a king first. Use /ron story.")); return 0; }
        if(own.equals(target.id())) { player.sendSystemMessage(Component.literal("That is your homeland already.")); return 0; }
        if(has(player,"ron_conquered_"+target.id())) { player.sendSystemMessage(Component.literal(target.name()+" is already in your empire.")); return 0; }
        if(!has(player,"ron_nation_war_"+target.id())) { player.sendSystemMessage(Component.literal("Declare war first with /ron war "+target.id()+".")); return 0; }
        if(money(player)<1500) { player.sendSystemMessage(Component.literal("Campaign costs 1,500 coins. Current balance: "+money(player)+".")); return 0; }
        setMoney(player,money(player)-1500);
        player.removeTag("ron_nation_war_"+target.id());
        player.addTag("ron_conquered_"+target.id());
        int count=conqueredCount(player);
        player.sendSystemMessage(Component.literal("VICTORY — "+target.name()+" has joined your empire. Nations under your rule: "+count+"/50."));
        if(count>=NATIONS.size()-1) {
            player.addTag("ron_world_king");
            player.sendSystemMessage(Component.literal("THE FINAL CHAPTER — You have united all 50 countries. You are the Sovereign of the Known World!"));
        }
        return 1;
    }

    private static int empireStatus(ServerPlayer player) {
        String own=tagValue(player,"ron_country_");
        player.sendSystemMessage(Component.literal("=== EMPIRE LEDGER ==="));
        player.sendSystemMessage(Component.literal("Homeland: "+(own==null?"None":find(own).name())));
        player.sendSystemMessage(Component.literal("Crown: "+(own!=null&&has(player,"ron_king_"+own)?"Claimed":"Not yet earned")));
        player.sendSystemMessage(Component.literal("Conquered countries: "+conqueredCount(player)+"/"+NATIONS.size()));
        player.sendSystemMessage(Component.literal("Treasury carried by you: "+money(player)+" coins"));
        player.sendSystemMessage(Component.literal("Commands: /ron nations, /ron nation <id>, /ron story, /ron advance, /ron crown, /ron war <id>, /ron conquer <id>."));
        return 1;
    }

    private static Nation find(String id) {
        if(id==null) return null;
        for(Nation nation:NATIONS) if(nation.id().equalsIgnoreCase(id)) return nation;
        return null;
    }
    private static boolean has(ServerPlayer player,String tag) { return player.getTags().contains(tag); }
    private static String tagValue(ServerPlayer player,String prefix) {
        for(String tag:player.getTags()) if(tag.startsWith(prefix)) return tag.substring(prefix.length());
        return null;
    }
    private static int storyStage(ServerPlayer player) {
        int stage=1;
        for(String tag:player.getTags()) if(tag.startsWith("ron_story_stage_")) {
            try { stage=Math.max(stage,Integer.parseInt(tag.substring("ron_story_stage_".length()))); }
            catch(NumberFormatException ignored) {}
        }
        return stage;
    }
    private static void setStage(ServerPlayer player,int stage) {
        for(String tag:new ArrayList<>(player.getTags())) if(tag.startsWith("ron_story_stage_")) player.removeTag(tag);
        player.addTag("ron_story_stage_"+stage);
    }
    private static String job(ServerPlayer player) {
        for(String tag:player.getTags()) if(tag.startsWith("ron_job_")) return tag.substring("ron_job_".length());
        return "citizen";
    }
    private static int money(ServerPlayer player) {
        for(String tag:player.getTags()) if(tag.startsWith("ron_money_")) {
            try { return Integer.parseInt(tag.substring("ron_money_".length())); } catch(NumberFormatException ignored) {}
        }
        return 100;
    }
    private static void setMoney(ServerPlayer player,int amount) {
        for(String tag:new ArrayList<>(player.getTags())) if(tag.startsWith("ron_money_")) player.removeTag(tag);
        player.addTag("ron_money_"+Math.max(0,amount));
    }
    private static int conqueredCount(ServerPlayer player) {
        int count=0;
        for(String tag:player.getTags()) if(tag.startsWith("ron_conquered_")) count++;
        return count;
    }
}
