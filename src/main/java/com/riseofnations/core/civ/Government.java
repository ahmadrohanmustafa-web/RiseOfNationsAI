package com.riseofnations.core.civ;

/**
 * Starting government choices. In this phase the choice is stored and shown only;
 * it does not change any rules yet (that comes with the government phase).
 */
public enum Government {
    MONARCHY("monarchy", "Monarchy", "A crowned ruler leads. Rule passes to an heir."),
    REPUBLIC("republic", "Republic", "Leaders are chosen by the citizens."),
    TRIBAL_COUNCIL("tribal_council", "Tribal Council", "Elders decide together."),
    MERCHANT_LEAGUE("merchant_league", "Merchant League", "Wealthy traders hold power.");

    private final String id;
    private final String displayName;
    private final String description;

    Government(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    public String description() { return description; }

    public static Government fromId(String id) {
        for (Government g : values()) {
            if (g.id.equals(id)) return g;
        }
        return null;
    }
}
