package com.riseofnations.core.settlement;

/** What kind of settlement a record is. Only capitals exist so far; saved as an id so more can be added. */
public enum SettlementType {
    CAPITAL("capital", "Capital");

    private final String id;
    private final String displayName;

    SettlementType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    /** Returns null for an unknown id (for example from a newer version). */
    public static SettlementType fromId(String id) {
        for (SettlementType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
