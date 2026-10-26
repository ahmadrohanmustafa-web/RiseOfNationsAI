package com.riseofnations.core.story;

import java.util.Set;

/** What a finished quest gives. Coins are paid through the RewardSink; the rest is applied once. */
public record Reward(long coins, int reputation, Set<String> unlocks, Set<String> setsFlags) {

    public static final Reward NONE = new Reward(0, 0, Set.of(), Set.of());

    public Reward {
        if (coins < 0) throw new IllegalArgumentException("coins must not be negative");
        unlocks = Set.copyOf(unlocks);
        setsFlags = Set.copyOf(setsFlags);
    }

    public static Reward coins(long coins, int reputation) {
        return new Reward(coins, reputation, Set.of(), Set.of());
    }
}
