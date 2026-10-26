package com.riseofnations.core.civ;

import java.util.HashMap;
import java.util.Map;

/** Limits how fast one player can send requests (server ticks). Keeps the map small. */
public final class RequestThrottle {

    private static final int MAX_ENTRIES = 2048;

    private final Map<String, Long> lastTick = new HashMap<>();

    /** Returns true and records the request if enough time has passed since this player's last request. */
    public boolean allow(String playerId, long nowTick, long minIntervalTicks) {
        Long last = lastTick.get(playerId);
        // If the tick counter went backwards (new server session), do not block.
        if (last != null && nowTick >= last && nowTick - last < minIntervalTicks) {
            return false;
        }
        if (lastTick.size() >= MAX_ENTRIES) lastTick.clear();
        lastTick.put(playerId, nowTick);
        return true;
    }
}
