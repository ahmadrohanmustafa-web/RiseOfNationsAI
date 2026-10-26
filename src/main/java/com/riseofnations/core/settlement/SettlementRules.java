package com.riseofnations.core.settlement;

import java.util.regex.Pattern;

/** Validation shared by the service (new settlements) and the save loader (stored settlements). */
public final class SettlementRules {

    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_HORIZONTAL = 30_000_000;
    public static final int MIN_Y = -2048;
    public static final int MAX_Y = 4096;
    public static final int MAX_LEVEL = 1000;

    private static final Pattern NAMESPACED_ID = Pattern.compile("^[a-z0-9_.\\-]{1,32}:[a-z0-9_./\\-]{1,64}$");

    private SettlementRules() { }

    /** A dimension or block id such as "minecraft:overworld". */
    public static boolean validNamespacedId(String id) {
        return id != null && NAMESPACED_ID.matcher(id).matches();
    }

    public static boolean validPosition(int x, int y, int z) {
        return Math.abs((long) x) <= MAX_HORIZONTAL && Math.abs((long) z) <= MAX_HORIZONTAL
                && y >= MIN_Y && y <= MAX_Y;
    }

    public static boolean validLevel(int level) {
        return level >= 1 && level <= MAX_LEVEL;
    }

    /** A settlement name is safe to store and show: not empty, not too long, no control or formatting characters. */
    public static boolean safeName(String name) {
        if (name == null || name.isBlank() || !name.equals(name.strip())) return false;
        if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) return false;
        for (int i = 0; i < name.length(); ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp) || cp == 0xA7 || Character.getType(cp) == Character.FORMAT) return false;
        }
        return true;
    }
}
