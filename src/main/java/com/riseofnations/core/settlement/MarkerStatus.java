package com.riseofnations.core.settlement;

/** Whether the capital's physical marker block is still standing. It is checked live, never saved. */
public enum MarkerStatus {
    PRESENT("present", "present"),
    MISSING("missing", "missing (it may have been broken)"),
    UNKNOWN("unknown", "not checked (you are far away or in another dimension)");

    private final String id;
    private final String text;

    MarkerStatus(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public String id() { return id; }

    public String text() { return text; }

    public static MarkerStatus fromIdOrUnknown(String id) {
        for (MarkerStatus s : values()) {
            if (s.id.equals(id)) return s;
        }
        return UNKNOWN;
    }
}
