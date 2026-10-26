package com.riseofnations.core.settlement;

/**
 * A real place in the world that belongs to a civilization.
 *
 * ownerId is the owning player's UUID, which is also the key of the owning civilization, so the link
 * to the civilization is stable even if the civilization is renamed.
 * (x, y, z) is the block the player stood on when founding it. The marker (a small pillar) stands
 * next to that block at (markerX, markerY, markerZ); markerBlockId says what the pillar's base is made of,
 * so the server can later check that the marker in the world is the one that belongs to this settlement.
 */
public record Settlement(String id, String ownerId, SettlementType type, String name, String dimensionId,
                         int x, int y, int z, int level, long createdAtEpochMs,
                         int markerX, int markerY, int markerZ, String markerBlockId) {

    public Settlement withName(String newName) {
        return new Settlement(id, ownerId, type, newName, dimensionId, x, y, z, level, createdAtEpochMs,
                markerX, markerY, markerZ, markerBlockId);
    }
}
