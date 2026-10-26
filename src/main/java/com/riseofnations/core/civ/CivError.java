package com.riseofnations.core.civ;

/** Every way a civilization request can fail, with a short message that is safe to show to the player. */
public enum CivError {
    NONE("OK"),
    INVALID_PLAYER("Invalid player."),
    INVALID_REQUEST("The request was not understood."),
    RATE_LIMITED("Too many requests. Please wait a moment."),
    SERVER_NOT_READY("The server is not ready yet. Please try again."),
    DATA_PROTECTED("Saved data is from a newer version of the mod. Civilizations are disabled to protect it."),
    ALREADY_HAS_CIVILIZATION("You already have a civilization."),
    NO_CIVILIZATION("You do not have a civilization yet."),
    NAME_EMPTY("Please enter a civilization name."),
    NAME_TOO_SHORT("The civilization name must be at least 3 characters."),
    NAME_TOO_LONG("The civilization name can be at most 32 characters."),
    NAME_INVALID_CHARS("The civilization name may only use letters, numbers, spaces, ' - and ."),
    NAME_TAKEN("Another civilization already uses that name."),
    SAME_NAME("That is already your civilization's name."),
    LEADER_EMPTY("Please enter a leader name."),
    LEADER_TOO_SHORT("The leader name must be at least 3 characters."),
    LEADER_TOO_LONG("The leader name can be at most 32 characters."),
    LEADER_INVALID_CHARS("The leader name may only use letters, numbers, spaces, ' - and ."),
    UNKNOWN_COLOR("Please choose one of the listed colors."),
    UNKNOWN_GOVERNMENT("Please choose one of the listed governments."),
    LEDGER_ERROR("The treasury could not be set up. Nothing was created.");

    private final String message;

    CivError(String message) {
        this.message = message;
    }

    public String message() { return message; }

    public boolean isOk() { return this == NONE; }

    /** Wire code. NONE is sent as "OK". */
    public String code() { return this == NONE ? "OK" : name(); }

    public static CivError fromCode(String code) {
        if ("OK".equals(code)) return NONE;
        for (CivError e : values()) {
            if (e.name().equals(code)) return e;
        }
        return INVALID_REQUEST;
    }

    public static CivError forCivName(CivNames.Problem p) {
        return switch (p) {
            case EMPTY -> NAME_EMPTY;
            case TOO_SHORT -> NAME_TOO_SHORT;
            case TOO_LONG -> NAME_TOO_LONG;
            case INVALID_CHARS -> NAME_INVALID_CHARS;
        };
    }

    public static CivError forLeaderName(CivNames.Problem p) {
        return switch (p) {
            case EMPTY -> LEADER_EMPTY;
            case TOO_SHORT -> LEADER_TOO_SHORT;
            case TOO_LONG -> LEADER_TOO_LONG;
            case INVALID_CHARS -> LEADER_INVALID_CHARS;
        };
    }
}
