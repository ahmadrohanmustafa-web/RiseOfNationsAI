package com.riseofnations.core.settlement;

/** What a player's client may see about their own capital. A read-only snapshot built on the server. */
public record SettlementView(String name, String typeId, String dimensionId, int x, int y, int z, int level,
                             String markerStatusId) { }
