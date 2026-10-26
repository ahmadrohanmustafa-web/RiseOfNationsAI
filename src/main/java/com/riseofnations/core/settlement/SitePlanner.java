package com.riseofnations.core.settlement;

/**
 * Decides whether a spot is suitable for the capital marker. The game layer looks at the real blocks and
 * fills in a {@link SiteInfo}; the decision itself is plain logic so it can be tested.
 *
 * The marker is a two-block pillar placed next to the player. We only ever use EMPTY space
 * (never replace an existing block) and require solid, non-liquid ground underneath.
 */
public final class SitePlanner {

    /** insideBuildHeight: marker, top and ground are all within the world's height limits. */
    public record SiteInfo(boolean insideBuildHeight, boolean baseIsAir, boolean topIsAir, boolean groundIsSolid) { }

    private SitePlanner() { }

    public static SettlementError evaluate(SiteInfo site) {
        if (!site.insideBuildHeight()) return SettlementError.OUT_OF_BUILD_HEIGHT;
        if (!site.baseIsAir() || !site.topIsAir()) return SettlementError.MARKER_BLOCKED;
        if (!site.groundIsSolid()) return SettlementError.NO_GROUND;
        return SettlementError.NONE;
    }
}
