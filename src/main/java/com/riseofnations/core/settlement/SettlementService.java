package com.riseofnations.core.settlement;

import com.riseofnations.core.civ.Civilization;
import com.riseofnations.core.economy.Ledger;
import com.riseofnations.core.persist.CoreState;

import java.util.Set;

/**
 * All rules for founding a capital. Pure Java, no Minecraft code.
 *
 * The caller (server code) passes the REAL player id and the position it read from the server's own player
 * object. Nothing here takes ownership, counts or coordinates from a client.
 */
public final class SettlementService {

    /** Capitals may only be founded in these dimensions. */
    public static final Set<String> SUPPORTED_DIMENSIONS = Set.of("minecraft:overworld");

    /** Block id of the marker pillar's base. The game layer places exactly this block. */
    public static final String MARKER_BASE_BLOCK = "minecraft:chiseled_stone_bricks";

    public record Result(Settlement settlement, SettlementError error) {
        public boolean ok() { return error == SettlementError.NONE; }

        static Result fail(SettlementError e) { return new Result(null, e); }
    }

    private SettlementService() { }

    /** The owner's capital, or null if they have not founded one. */
    public static Settlement capitalOf(CoreState state, String ownerId) {
        if (ownerId == null) return null;
        for (Settlement s : state.settlementsView().values()) {
            if (s.type() == SettlementType.CAPITAL && s.ownerId().equals(ownerId)) return s;
        }
        return null;
    }

    /** How many real (founded) settlements this owner has. This is NOT the simulated number in Civilization. */
    public static int foundedCount(CoreState state, String ownerId) {
        int n = 0;
        for (Settlement s : state.settlementsView().values()) {
            if (s.ownerId().equals(ownerId)) n++;
        }
        return n;
    }

    public static String defaultCapitalName(String civilizationName) {
        return civilizationName + " Capital";
    }

    /**
     * Checks everything that does not need to look at the world. Changes nothing. The game layer calls this
     * before touching any blocks, so a refused request never leaves a stray marker behind.
     */
    public static SettlementError precheck(CoreState state, String ownerId, String dimensionId) {
        if (!Ledger.validId(ownerId)) return SettlementError.INVALID_PLAYER;
        if (state.civilization(ownerId) == null) return SettlementError.NO_CIVILIZATION;
        if (capitalOf(state, ownerId) != null) return SettlementError.CAPITAL_EXISTS;
        if (dimensionId == null || !SUPPORTED_DIMENSIONS.contains(dimensionId)) return SettlementError.UNSUPPORTED_DIMENSION;
        return SettlementError.NONE;
    }

    /** Records the capital. Re-checks every rule, so a repeated or racing request can never add a second one. */
    public static Result foundCapital(CoreState state, String ownerId, String settlementId, String dimensionId,
                                      int x, int y, int z, int markerX, int markerY, int markerZ, long nowEpochMs) {
        SettlementError pre = precheck(state, ownerId, dimensionId);
        if (!pre.isOk()) return Result.fail(pre);
        if (!Ledger.validId(settlementId) || state.settlement(settlementId) != null) {
            return Result.fail(SettlementError.INVALID_ID);
        }
        if (!SettlementRules.validPosition(x, y, z) || !SettlementRules.validPosition(markerX, markerY, markerZ)) {
            return Result.fail(SettlementError.INVALID_POSITION);
        }
        Civilization civ = state.civilization(ownerId);
        Settlement capital = new Settlement(settlementId, ownerId, SettlementType.CAPITAL,
                defaultCapitalName(civ.name()), dimensionId, x, y, z, 1, nowEpochMs,
                markerX, markerY, markerZ, MARKER_BASE_BLOCK);
        state.putSettlement(capital);
        return new Result(capital, SettlementError.NONE);
    }

    /** Builds the read-only snapshot of a settlement for its owner. */
    public static SettlementView view(Settlement s, MarkerStatus marker) {
        return new SettlementView(s.name(), s.type().id(), s.dimensionId(), s.x(), s.y(), s.z(), s.level(), marker.id());
    }
}
