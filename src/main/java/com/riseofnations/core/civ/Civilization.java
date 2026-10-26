package com.riseofnations.core.civ;

/**
 * One player's civilization. The treasury is NOT stored here: it is the balance of the ledger account
 * "civ:&lt;ownerId&gt;", so there is only one source of truth for money.
 *
 * Population and settlements are simulated numbers. They are not Minecraft entities or buildings.
 */
public record Civilization(String ownerId, String name, String leaderName, CivColor color, Government government,
                           long population, int settlements, DevelopmentStage stage, EconomicStatus economy,
                           long createdAtEpochMs) {

    public Civilization withName(String newName) {
        return new Civilization(ownerId, newName, leaderName, color, government, population, settlements,
                stage, economy, createdAtEpochMs);
    }
}
