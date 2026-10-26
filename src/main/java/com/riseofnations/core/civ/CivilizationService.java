package com.riseofnations.core.civ;

import com.riseofnations.core.config.RonSettings;
import com.riseofnations.core.economy.Ledger;
import com.riseofnations.core.persist.CoreState;
import com.riseofnations.core.settlement.MarkerStatus;
import com.riseofnations.core.settlement.Settlement;
import com.riseofnations.core.settlement.SettlementService;

/**
 * All rules for creating and renaming a civilization. Pure Java, no Minecraft code.
 * The server calls this with the REAL player id; nothing here accepts a treasury, population or any
 * other authoritative value from a request.
 */
public final class CivilizationService {

    public static final long STARTING_POPULATION = 100;
    public static final int STARTING_SETTLEMENTS = 1;

    public record Result(Civilization civilization, CivError error) {
        public boolean ok() { return error == CivError.NONE; }

        static Result fail(CivError e) { return new Result(null, e); }
    }

    private CivilizationService() { }

    /** The ledger account that holds this civilization's treasury. */
    public static String treasuryAccount(String ownerId) {
        return "civ:" + ownerId;
    }

    public static Result create(CoreState state, RonSettings settings, String ownerId, String rawName,
                                String rawLeader, String colorId, String governmentId, long nowEpochMs) {
        if (!Ledger.validId(ownerId)) return Result.fail(CivError.INVALID_PLAYER);
        if (state.civilization(ownerId) != null) return Result.fail(CivError.ALREADY_HAS_CIVILIZATION);

        CivNames.NameCheck name = CivNames.check(rawName);
        if (!name.ok()) return Result.fail(CivError.forCivName(name.problem()));
        CivNames.NameCheck leader = CivNames.check(rawLeader);
        if (!leader.ok()) return Result.fail(CivError.forLeaderName(leader.problem()));

        CivColor color = CivColor.fromId(colorId);
        if (color == null) return Result.fail(CivError.UNKNOWN_COLOR);
        Government government = Government.fromId(governmentId);
        if (government == null) return Result.fail(CivError.UNKNOWN_GOVERNMENT);

        if (nameTaken(state, name.clean(), null)) return Result.fail(CivError.NAME_TAKEN);

        // Treasury: one ledger account. If an account with this id already exists (it can only be left
        // over from damaged data), reuse it and do NOT mint again, so money is never duplicated.
        Ledger ledger = state.ledger();
        String account = treasuryAccount(ownerId);
        if (!ledger.exists(account)) {
            if (!ledger.open(account)) return Result.fail(CivError.LEDGER_ERROR);
            long start = settings.startingSettlementTreasury;
            if (start > 0 && ledger.mint(account, start) != Ledger.Result.OK) {
                return Result.fail(CivError.LEDGER_ERROR);
            }
        }

        Civilization civ = new Civilization(ownerId, name.clean(), leader.clean(), color, government,
                STARTING_POPULATION, STARTING_SETTLEMENTS, DevelopmentStage.SETTLED, EconomicStatus.STABLE,
                nowEpochMs);
        state.putCivilization(civ);
        return new Result(civ, CivError.NONE);
    }

    public static Result rename(CoreState state, String ownerId, String rawName) {
        if (!Ledger.validId(ownerId)) return Result.fail(CivError.INVALID_PLAYER);
        Civilization current = state.civilization(ownerId);
        if (current == null) return Result.fail(CivError.NO_CIVILIZATION);

        CivNames.NameCheck name = CivNames.check(rawName);
        if (!name.ok()) return Result.fail(CivError.forCivName(name.problem()));
        if (name.clean().equals(current.name())) return Result.fail(CivError.SAME_NAME);
        if (nameTaken(state, name.clean(), ownerId)) return Result.fail(CivError.NAME_TAKEN);

        Civilization renamed = current.withName(name.clean());
        state.putCivilization(renamed);
        return new Result(renamed, CivError.NONE);
    }

    /** True if another civilization (not excludeOwner) already uses a name that collides with this one. */
    public static boolean nameTaken(CoreState state, String cleanName, String excludeOwner) {
        String key = CivNames.key(cleanName);
        for (Civilization other : state.civilizationsView().values()) {
            if (other.ownerId().equals(excludeOwner)) continue;
            if (CivNames.key(other.name()).equals(key)) return true;
        }
        return false;
    }

    /** Builds the read-only snapshot for the owner, or null if they have no civilization. */
    public static CivView view(CoreState state, String ownerId) {
        return view(state, ownerId, MarkerStatus.UNKNOWN);
    }

    /** Same, with the live state of the capital's marker block (the game layer looks at the world). */
    public static CivView view(CoreState state, String ownerId, MarkerStatus markerStatus) {
        Civilization c = state.civilization(ownerId);
        if (c == null) return null;
        Settlement capital = SettlementService.capitalOf(state, ownerId);
        return new CivView(c.name(), c.leaderName(), c.color().id(), c.government().id(),
                state.ledger().balance(treasuryAccount(ownerId)), c.population(), c.settlements(),
                c.stage().id(), c.economy().id(), c.createdAtEpochMs(),
                SettlementService.foundedCount(state, ownerId),
                capital == null ? null : SettlementService.view(capital, markerStatus));
    }
}
