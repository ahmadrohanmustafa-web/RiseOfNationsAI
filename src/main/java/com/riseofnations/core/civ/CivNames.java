package com.riseofnations.core.civ;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Cleans and checks names typed by players (civilization names and leader names).
 *
 * Allowed: letters (any language), digits, single spaces, apostrophe ('), hyphen (-) and period (.).
 * Rejected: Minecraft formatting codes (the section sign), control characters, tabs, new lines,
 * zero-width and other invisible characters, emoji and symbols.
 * Length is 3 to 32 characters after cleaning.
 */
public final class CivNames {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;
    /** Anything longer than this is rejected before any processing (protects against huge inputs). */
    private static final int MAX_RAW_LENGTH = 200;

    public enum Problem { EMPTY, TOO_SHORT, TOO_LONG, INVALID_CHARS }

    /** Exactly one of clean / problem is set. */
    public record NameCheck(String clean, Problem problem) {
        public boolean ok() { return problem == null; }
    }

    private CivNames() { }

    public static NameCheck check(String raw) {
        if (raw == null) return new NameCheck(null, Problem.EMPTY);
        if (raw.length() > MAX_RAW_LENGTH) return new NameCheck(null, Problem.TOO_LONG);

        String n = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder();
        boolean lastWasSpace = true;     // true at the start so leading spaces are dropped
        int letters = 0;
        for (int i = 0; i < n.length(); ) {
            int cp = n.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') {
                if (!lastWasSpace) {
                    sb.append(' ');
                    lastWasSpace = true;
                }
                continue;
            }
            if (Character.isLetter(cp)) {
                letters++;
            } else if (!Character.isDigit(cp) && cp != '\'' && cp != '-' && cp != '.') {
                return new NameCheck(null, Problem.INVALID_CHARS);
            }
            sb.appendCodePoint(cp);
            lastWasSpace = false;
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') {
            sb.setLength(sb.length() - 1);
        }
        String clean = sb.toString();
        if (clean.isEmpty()) return new NameCheck(null, Problem.EMPTY);
        int length = clean.codePointCount(0, clean.length());
        if (length < MIN_LENGTH) return new NameCheck(null, Problem.TOO_SHORT);
        if (length > MAX_LENGTH) return new NameCheck(null, Problem.TOO_LONG);
        if (letters == 0) return new NameCheck(null, Problem.INVALID_CHARS);
        return new NameCheck(clean, null);
    }

    /**
     * The comparison key used to detect duplicate names: compatibility-normalized, lower-cased,
     * and stripped of spaces and punctuation. "Rome", "ROME", "R.o.m.e" and "ｒｏｍｅ" all collide.
     * (Look-alike letters from different alphabets are NOT detected; that is a known limitation.)
     */
    public static String key(String clean) {
        String n = Normalizer.normalize(clean, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); ) {
            int cp = n.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) sb.appendCodePoint(cp);
        }
        return sb.toString();
    }
}
