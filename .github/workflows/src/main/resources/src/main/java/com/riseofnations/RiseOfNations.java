
package com.riseofnations;

import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class RiseOfNations implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(
            (dispatcher, registryAccess, environment) -> {
                dispatcher.register(
                    Commands.literal("ron")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                () -> Component.literal(
                                    "Rise of Nations: AI Civilization is running!"
                                ),
                                false
                            );
                            return Command.SINGLE_SUCCESS;
                        })
                );
            }
        );
    }
}
