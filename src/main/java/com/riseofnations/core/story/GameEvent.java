package com.riseofnations.core.story;

import com.riseofnations.core.economy.Ledger;

/**
 * Something real that happened in the game, reported by the Minecraft layer.
 * Examples: ("item_delivered", "minecraft:wheat", 16), ("wage_earned", "farmer", 20),
 * ("npc_talked", "steward", 1), ("property_acquired", "cottage", 1).
 *
 * dedupeKey is optional. If set (for example a contract id), the same event can only ever count once.
 */
public record GameEvent(String type, String subject, long amount, String dedupeKey) {

    public static GameEvent of(String type, String subject, long amount) {
        return new GameEvent(type, subject, amount, null);
    }

    public boolean isValid() {
        return Ledger.validId(type) && Ledger.validId(subject) && amount > 0 && amount <= 1_000_000_000L
                && (dedupeKey == null || Ledger.validId(dedupeKey));
    }
}
