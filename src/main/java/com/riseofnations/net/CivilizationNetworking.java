package com.riseofnations.net;

import com.riseofnations.RiseOfNationsMod;
import com.riseofnations.core.civ.CivError;
import com.riseofnations.core.civ.CivView;
import com.riseofnations.core.civ.CivWire;
import com.riseofnations.core.civ.CivilizationService;
import com.riseofnations.core.civ.RequestThrottle;
import com.riseofnations.server.RonSavedData;
import com.riseofnations.server.SettlementWorld;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Server side of the civilization screens. Every request is handled on the server thread, and the
 * player's identity always comes from the connection (never from the packet). The packet only carries
 * what the player typed or picked; the server decides everything else.
 */
public final class CivilizationNetworking {

    /** At most one create/rename request per half second (10 ticks) per player. */
    private static final long MIN_REQUEST_INTERVAL_TICKS = 10;

    private static final RequestThrottle THROTTLE = new RequestThrottle();

    private CivilizationNetworking() { }

    /** Registers the three packet types (both sides) and the two server receivers. */
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(CivilizationStatePayload.TYPE, CivilizationStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CreateCivilizationPayload.TYPE, CreateCivilizationPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RenameCivilizationPayload.TYPE, RenameCivilizationPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CreateCivilizationPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            onServerThread(player, server -> handleCreate(server, player, payload.data()));
        });
        ServerPlayNetworking.registerGlobalReceiver(RenameCivilizationPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            onServerThread(player, server -> handleRename(server, player, payload.data()));
        });
    }

    /** Sends the player their civilization and asks their client to open the screen (used by /ron). */
    public static void openFor(ServerPlayer player) {
        onServerThread(player, server -> {
            RonSavedData saved = RonSavedData.get(server);
            CivError problem = problemWith(saved);
            send(player, true, problem, problem == CivError.NONE ? viewOf(saved, player) : null);
        });
    }

    // ------------------------------------------------------------------------------------------

    private static void handleCreate(MinecraftServer server, ServerPlayer player, String data) {
        RonSavedData saved = RonSavedData.get(server);
        CivError problem = checkRequest(server, player, saved);
        if (problem != CivError.NONE) {
            reply(player, saved, problem);
            return;
        }
        CivWire.CreateRequest request = CivWire.decodeCreate(data);
        if (request == null) {
            reply(player, saved, CivError.INVALID_REQUEST);
            return;
        }
        String owner = player.getUUID().toString();
        CivilizationService.Result result = CivilizationService.create(saved.state(), RiseOfNationsMod.settings(),
                owner, request.name(), request.leader(), request.colorId(), request.governmentId(),
                System.currentTimeMillis());
        if (result.ok()) {
            saved.markChanged();
            RiseOfNationsMod.LOGGER.info("Civilization '{}' created for player {}.", result.civilization().name(), owner);
        }
        reply(player, saved, result.error());
    }

    private static void handleRename(MinecraftServer server, ServerPlayer player, String data) {
        RonSavedData saved = RonSavedData.get(server);
        CivError problem = checkRequest(server, player, saved);
        if (problem != CivError.NONE) {
            reply(player, saved, problem);
            return;
        }
        CivWire.RenameRequest request = CivWire.decodeRename(data);
        if (request == null) {
            reply(player, saved, CivError.INVALID_REQUEST);
            return;
        }
        String owner = player.getUUID().toString();
        CivilizationService.Result result = CivilizationService.rename(saved.state(), owner, request.name());
        if (result.ok()) {
            saved.markChanged();
            RiseOfNationsMod.LOGGER.info("Civilization of player {} renamed to '{}'.", owner, result.civilization().name());
        }
        reply(player, saved, result.error());
    }

    /** Checks that data is usable and that this player is not sending requests too fast. */
    private static CivError checkRequest(MinecraftServer server, ServerPlayer player, RonSavedData saved) {
        CivError problem = problemWith(saved);
        if (problem != CivError.NONE) return problem;
        if (!THROTTLE.allow(player.getUUID().toString(), server.getTickCount(), MIN_REQUEST_INTERVAL_TICKS)) {
            return CivError.RATE_LIMITED;
        }
        return CivError.NONE;
    }

    private static CivError problemWith(RonSavedData saved) {
        if (saved == null) return CivError.SERVER_NOT_READY;
        if (saved.refused()) return CivError.DATA_PROTECTED;
        return CivError.NONE;
    }

    private static CivView viewOf(RonSavedData saved, ServerPlayer player) {
        return CivilizationService.view(saved.state(), player.getUUID().toString(),
                SettlementWorld.markerStatus(player, saved.state()));
    }

    /** Answers a request: the player's current civilization plus the outcome. */
    private static void reply(ServerPlayer player, RonSavedData saved, CivError error) {
        boolean usable = saved != null && !saved.refused();
        send(player, false, error, usable ? viewOf(saved, player) : null);
    }

    private static void send(ServerPlayer player, boolean open, CivError error, CivView view) {
        String text = CivWire.encodeState(new CivWire.StateMessage(open, error, view));
        ServerPlayNetworking.send(player, new CivilizationStatePayload(text));
    }

    /** Runs the action on the server thread, whichever thread the packet arrived on. */
    private static void onServerThread(ServerPlayer player, Consumer<MinecraftServer> action) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        server.execute(() -> action.accept(server));
    }
}
