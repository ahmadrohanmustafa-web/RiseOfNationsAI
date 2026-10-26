package com.riseofnations.core.config;

import java.util.ArrayList;
import java.util.List;

/**
 * All balance numbers in one place. Public fields so Gson can read and write the JSON config file.
 * Anything out of range is clamped by {@link #sanitize()} so a typo cannot break the server.
 */
public final class RonSettings {

    public int configVersion = 1;

    /** Minimum ticks between two wage payments for the same job (20 ticks = 1 second). */
    public long wageCooldownTicks = 1200;
    /** Most coins one player can earn from wages in one in-game day. */
    public long dailyWageCap = 500;
    public long dayLengthTicks = 24000;
    /** Market tax in basis points (100 = 1%). Goes to the settlement treasury. */
    public int marketTaxBasisPoints = 500;
    /** Coins minted ONCE into each new settlement treasury. After that, money only moves. */
    public long startingSettlementTreasury = 5000;
    /** How often the world simulation (nations, events) runs. Never per tick. */
    public long simulationIntervalTicks = 1200;
    public int maxActiveNpcs = 24;
    public int maxNations = 50;

    /** Clamps every value into a safe range and returns a note for each value that was changed. */
    public List<String> sanitize() {
        List<String> notes = new ArrayList<>();
        wageCooldownTicks = clamp("wageCooldownTicks", wageCooldownTicks, 20, 72_000, notes);
        dailyWageCap = clamp("dailyWageCap", dailyWageCap, 1, 1_000_000, notes);
        dayLengthTicks = clamp("dayLengthTicks", dayLengthTicks, 1200, 240_000, notes);
        marketTaxBasisPoints = (int) clamp("marketTaxBasisPoints", marketTaxBasisPoints, 0, 5000, notes);
        startingSettlementTreasury = clamp("startingSettlementTreasury", startingSettlementTreasury, 0, 10_000_000, notes);
        simulationIntervalTicks = clamp("simulationIntervalTicks", simulationIntervalTicks, 200, 72_000, notes);
        maxActiveNpcs = (int) clamp("maxActiveNpcs", maxActiveNpcs, 0, 200, notes);
        maxNations = (int) clamp("maxNations", maxNations, 1, 100, notes);
        return notes;
    }

    private static long clamp(String name, long value, long min, long max, List<String> notes) {
        long fixed = Math.max(min, Math.min(max, value));
        if (fixed != value) notes.add(name + " was " + value + ", changed to " + fixed + " (allowed " + min + ".." + max + ")");
        return fixed;
    }
}
