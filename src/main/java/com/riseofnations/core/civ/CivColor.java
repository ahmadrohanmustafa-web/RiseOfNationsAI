package com.riseofnations.core.civ;

/** The civilization colors a player can pick. The rgb value is used by the client to tint the name. */
public enum CivColor {
    RED("red", "Crimson", 0xE04848),
    ORANGE("orange", "Amber", 0xE8903A),
    YELLOW("yellow", "Gold", 0xE8D048),
    GREEN("green", "Emerald", 0x48C060),
    CYAN("cyan", "Azure", 0x48C8D8),
    BLUE("blue", "Sapphire", 0x4878E8),
    PURPLE("purple", "Violet", 0xA060E0),
    WHITE("white", "Ivory", 0xE8E8E8);

    private final String id;
    private final String displayName;
    private final int rgb;

    CivColor(String id, String displayName, int rgb) {
        this.id = id;
        this.displayName = displayName;
        this.rgb = rgb;
    }

    public String id() { return id; }

    public String displayName() { return displayName; }

    public int rgb() { return rgb; }

    /** Returns the color with this id, or null if there is none. */
    public static CivColor fromId(String id) {
        for (CivColor c : values()) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }
}
