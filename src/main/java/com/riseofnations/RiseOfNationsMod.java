package com.riseofnations;

import com.riseofnations.core.config.RonSettings;
import com.riseofnations.core.story.DefaultStory;
import com.riseofnations.core.story.StoryDefinition;
import com.riseofnations.net.CivilizationNetworking;
import com.riseofnations.server.SettlementCommands;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point. Kept deliberately small: it loads the config, checks that the built-in story is
 * valid, registers the civilization packets, and registers the /ron commands. All real game logic lives in com.riseofnations.core,
 * which has no Minecraft dependency and is covered by tests.
 *
 * Shared by client and dedicated server, so it must never reference client-only classes.
 */
public final class RiseOfNationsMod implements ModInitializer {

    public static final String MOD_ID = "riseofnations";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static RonSettings settings = new RonSettings();

    public static RonSettings settings() {
        return settings;
    }

    @Override
    public void onInitialize() {
        settings = ConfigLoader.load(FabricLoader.getInstance().getConfigDir().resolve("riseofnations.json"));

        try {
            StoryDefinition story = DefaultStory.create();
            LOGGER.info("Rise of Nations: story loaded ({} chapters, {} quests).", story.chapters().size(), story.quests().size());
        } catch (RuntimeException e) {
            LOGGER.error("Rise of Nations: the built-in story is invalid and was not loaded.", e);
        }

        CivilizationNetworking.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("ron")
                        .executes(context -> openCivilization(context.getSource()))
                        .then(Commands.literal("civilization")
                                .executes(context -> openCivilization(context.getSource())))
                        .then(SettlementCommands.literal())
                        .then(Commands.literal("about")
                                .executes(context -> {
                                    String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                                            .map(c -> c.getMetadata().getVersion().getFriendlyString())
                                            .orElse("unknown");
                                    context.getSource().sendSuccess(
                                            () -> Component.literal("Rise of Nations: AI Civilization v" + version + " (early development)"),
                                            false);
                                    return 1;
                                }))));

        LOGGER.info("Rise of Nations initialized.");
    }

    /** /ron and /ron civilization: ask the server for this player's data and open the screen on their client. */
    private static int openCivilization(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CivilizationNetworking.openFor(player);
        return 1;
    }
}
