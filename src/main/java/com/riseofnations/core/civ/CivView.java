package com.riseofnations.core.civ;

import com.riseofnations.core.settlement.SettlementView;

/**
 * Exactly what a player's client is allowed to see about their own civilization.
 * It is a read-only snapshot built on the server; the client can never send one back.
 *
 * settlements is the SIMULATED count from the civilization model (the starting value is 1).
 * foundedSettlements is the number of REAL settlements founded in the world. capital is null until founded.
 */
public record CivView(String name, String leaderName, String colorId, String governmentId, long treasury,
                      long population, int settlements, String stageId, String economyId, long createdAtEpochMs,
                      int foundedSettlements, SettlementView capital) { }
