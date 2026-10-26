package com.riseofnations.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.riseofnations.RiseOfNationsMod;
import com.riseofnations.core.civ.Civilization;
import com.riseofnations.core.civ.RequestThrottle;
import com.riseofnations.core.persist.CoreState;
import com.riseofnations.core.settlement.Settlement;
import com.riseofnations.core.settlement.SettlementError;
import com.riseofnations.core.settlement.SettlementService;
import com.riseofnations.core.settlement.SettlementText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * /ron settlement and /ron settlement create.
 *
 * Everything runs on the server thread. The player's identity and position are read from the server's own
 * player object; the command takes no coordinates or owner from anyone.
 */
public final class SettlementCommands {

    /** Same spacing as the civilization screen requests: at most one try per half second (10 ticks) per player. */
    private static final long MIN_REQUEST_INTERVAL_TICKS = 10;

    private static final RequestThrottle THROTTLE = new RequestThrottle();

    private SettlementCommands() { }

    /** The "settlement" branch, to be attached under /ron. */
    public static LiteralArgumentBuilder<CommandSourceStack> literal() {
        return Commands.literal("settlement")
                .executes(context -> show(context.getSource()))
                .then(Commands.literal("create")
                        .executes(context -> create(context.getSource())));
    }

    private static int show(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RonSavedData saved = RonSavedData.get(source.getServer());
        SettlementError problem = problemWith(saved);
        if (!problem.isOk()) return fail(source, problem);

        CoreState state = saved.state();
        String owner = player.getUUID().toString();
        Civilization civ = state.civilization(owner);
        if (civ == null) {
            info(source, SettlementError.NO_CIVILIZATION.message());
            return 1;
        }
        Settlement capital = SettlementService.capitalOf(state, owner);
        if (capital == null) {
            SettlementText.notFounded(civ.name()).forEach(line -> info(source, line));
            return 1;
        }
        var view = SettlementService.view(capital, SettlementWorld.markerStatus(player, state));
        SettlementText.describe(civ.name(), view, SettlementService.foundedCount(state, owner))
                .forEach(line -> info(source, line));
        return 1;
    }

    private static int create(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        RonSavedData saved = RonSavedData.get(server);
        SettlementError problem = problemWith(saved);
        if (!problem.isOk()) return fail(source, problem);

        String owner = player.getUUID().toString();
        if (!THROTTLE.allow(owner, server.getTickCount(), MIN_REQUEST_INTERVAL_TICKS)) {
            return fail(source, SettlementError.RATE_LIMITED);
        }
        if (player.isSpectator()) return fail(source, SettlementError.SPECTATOR);

        // 1. Rules that need no world access. A refused request never touches a block.
        Level level = player.level();
        String dimension = SettlementWorld.dimensionId(level);
        SettlementError pre = SettlementService.precheck(saved.state(), owner, dimension);
        if (!pre.isOk()) return fail(source, pre);

        // 2. Find an empty, safe spot next to the player (never replaces a block).
        SettlementWorld.Site site = SettlementWorld.findSite(player);
        if (!site.error().isOk()) return fail(source, site.error());

        // 3. Place the marker, then record the settlement. If recording fails, take the marker back out.
        BlockPos base = site.base();
        if (!SettlementWorld.placeMarker(level, base)) return fail(source, SettlementError.PLACEMENT_FAILED);

        BlockPos here = player.blockPosition();
        SettlementService.Result result = SettlementService.foundCapital(saved.state(), owner,
                UUID.randomUUID().toString(), dimension, here.getX(), here.getY(), here.getZ(),
                base.getX(), base.getY(), base.getZ(), System.currentTimeMillis());
        if (!result.ok()) {
            SettlementWorld.removeMarker(level, base);
            return fail(source, result.error());
        }

        saved.markChanged();
        RiseOfNationsMod.LOGGER.info("Capital '{}' founded for player {} at {} {} {} in {}.",
                result.settlement().name(), owner, here.getX(), here.getY(), here.getZ(), dimension);
        info(source, SettlementText.founded(result.settlement()));
        return 1;
    }

    private static SettlementError problemWith(RonSavedData saved) {
        if (saved == null) return SettlementError.SERVER_NOT_READY;
        if (saved.refused()) return SettlementError.DATA_PROTECTED;
        return SettlementError.NONE;
    }

    private static void info(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static int fail(CommandSourceStack source, SettlementError error) {
        source.sendFailure(Component.literal(error.message()));
        return 0;
    }
}
