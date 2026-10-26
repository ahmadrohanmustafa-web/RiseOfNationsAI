package com.riseofnations.core.civ;

/** Overall economic condition. Only "Stable" exists so far; saved as an id so more can be added. */
public enum EconomicStatus {
    STABLE("stable", "Stable");

    private final String id;
    private final String displayName;

    EconomicStatus(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    public static EconomicStatus fromIdOrDefault(String id) {
        for (EconomicStatus s : values()) {
            if (s.id.equals(id)) return s;
        }
        return STABLE;
    }
}
