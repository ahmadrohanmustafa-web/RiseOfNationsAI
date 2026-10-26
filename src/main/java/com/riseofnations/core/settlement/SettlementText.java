package com.riseofnations.core.settlement;

import java.util.List;

/** Wording used by the /ron settlement command (and tests). English only for now. */
public final class SettlementText {

    private SettlementText() { }

    public static String dimensionName(String dimensionId) {
        if (dimensionId == null) return "Unknown";
        return switch (dimensionId) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            default -> dimensionId;
        };
    }

    public static String coordinates(int x, int y, int z) {
        return x + ", " + y + ", " + z;
    }

    /** Lines for a player who has a capital. */
    public static List<String> describe(String civilizationName, SettlementView capital, int foundedCount) {
        MarkerStatus marker = MarkerStatus.fromIdOrUnknown(capital.markerStatusId());
        return List.of(
                "Civilization: " + civilizationName,
                "Capital: Founded - " + capital.name(),
                "Location: " + coordinates(capital.x(), capital.y(), capital.z()) + " (" + dimensionName(capital.dimensionId()) + ")",
                "Settlement level: " + capital.level(),
                "Founded settlements: " + foundedCount,
                "Marker: " + marker.text());
    }

    /** Lines for a player who has a civilization but no capital yet. */
    public static List<String> notFounded(String civilizationName) {
        return List.of(
                "Civilization: " + civilizationName,
                "Capital: Not founded yet",
                "Stand where you want your capital and run /ron settlement create.");
    }

    /** The message shown after a successful founding. */
    public static String founded(Settlement s) {
        return "Capital founded: " + s.name() + " at " + coordinates(s.x(), s.y(), s.z())
                + " (" + dimensionName(s.dimensionId()) + "). A small marker now stands at "
                + coordinates(s.markerX(), s.markerY(), s.markerZ()) + ".";
    }
}
