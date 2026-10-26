package com.riseofnations.core.settlement;

/** Every way founding a settlement can fail, with a message that is safe and useful to show to the player. */
public enum SettlementError {
    NONE("OK"),
    INVALID_PLAYER("Invalid player."),
    NO_CIVILIZATION("You need a civilization first. Use /ron to found one."),
    CAPITAL_EXISTS("Your civilization already has a capital. Use /ron settlement to see it."),
    UNSUPPORTED_DIMENSION("Capitals can only be founded in the Overworld. Go to the Overworld and try again."),
    SPECTATOR("Spectators cannot found a capital."),
    INVALID_POSITION("That position is not valid for a settlement."),
    INVALID_ID("The settlement could not be registered. Nothing was created."),
    OUT_OF_BUILD_HEIGHT("This spot is too close to the world's height limit. Move somewhere else and try again."),
    MARKER_BLOCKED("There is no free space for the capital marker next to you. Move to an open, flat area and try again."),
    NO_GROUND("The marker needs solid ground. Move to dry land and try again."),
    PLACEMENT_FAILED("The marker could not be placed. Nothing was created. Try another spot."),
    RATE_LIMITED("Too many requests. Please wait a moment."),
    DATA_PROTECTED("Saved data is from a newer version of the mod. Settlements are disabled to protect it."),
    SERVER_NOT_READY("The server is not ready yet. Please try again.");

    private final String message;

    SettlementError(String message) {
        this.message = message;
    }

    public String message() { return message; }

    public boolean isOk() { return this == NONE; }
}
