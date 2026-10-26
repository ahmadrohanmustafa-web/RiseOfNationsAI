package com.riseofnations.core.civ;

/** How far the civilization has grown. Only the first stage exists so far; saved as an id so more can be added. */
public enum DevelopmentStage {
    SETTLED("settled", "Settled");

    private final String id;
    private final String displayName;

    DevelopmentStage(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    /** Unknown ids (for example from a newer version) fall back to the first stage. */
    public static DevelopmentStage fromIdOrDefault(String id) {
        for (DevelopmentStage s : values()) {
            if (s.id.equals(id)) return s;
        }
        return SETTLED;
    }
}
