package com.riseofnations.core.persist;

import com.riseofnations.core.civ.Civilization;
import com.riseofnations.core.economy.Ledger;
import com.riseofnations.core.settlement.Settlement;
import com.riseofnations.core.economy.WageTracker;
import com.riseofnations.core.story.PlayerStory;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Everything that must survive a restart. One instance per world. */
public final class CoreState {

    private final Ledger ledger;
    private final WageTracker wages;
    private final Map<String, PlayerStory> stories = new TreeMap<>();
    private final Map<String, Civilization> civilizations = new TreeMap<>();
    private final Map<String, Settlement> settlements = new TreeMap<>();

    public CoreState() {
        this(new Ledger(), new WageTracker());
    }

    public CoreState(Ledger ledger, WageTracker wages) {
        this.ledger = ledger;
        this.wages = wages;
    }

    public Ledger ledger() { return ledger; }

    public WageTracker wages() { return wages; }

    /** Returns the player's story, creating it on first use. Returns null for an unsafe id. */
    public PlayerStory story(String playerId) {
        if (!Ledger.validId(playerId)) return null;
        return stories.computeIfAbsent(playerId, k -> new PlayerStory());
    }

    public Map<String, PlayerStory> storiesView() {
        return Collections.unmodifiableMap(stories);
    }

    /** The civilization owned by this player, or null. */
    public Civilization civilization(String ownerId) {
        return ownerId == null ? null : civilizations.get(ownerId);
    }

    public void putCivilization(Civilization civ) {
        civilizations.put(civ.ownerId(), civ);
    }

    public Map<String, Civilization> civilizationsView() {
        return Collections.unmodifiableMap(civilizations);
    }

    /** A settlement by its unique id, or null. */
    public Settlement settlement(String settlementId) {
        return settlementId == null ? null : settlements.get(settlementId);
    }

    public void putSettlement(Settlement settlement) {
        settlements.put(settlement.id(), settlement);
    }

    /** Only used to undo a settlement when the marker could not be placed. */
    public void removeSettlement(String settlementId) {
        settlements.remove(settlementId);
    }

    public Map<String, Settlement> settlementsView() {
        return Collections.unmodifiableMap(settlements);
    }
}
