package com.riseofnations.core.civ;

import com.riseofnations.core.settlement.SettlementRules;
import com.riseofnations.core.settlement.SettlementView;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The text format used between client and server (each packet carries one of these strings).
 * Pure Java so it can be tested. Free text is percent-encoded, so a name can never smuggle in a
 * separator or an extra field. Decoders are strict: wrong field count, bad numbers or oversized
 * input return null instead of guessing.
 *
 * Requests from the client contain ONLY what the player typed or picked. They have no field for
 * treasury, population, owner or anything else the server decides.
 */
public final class CivWire {

    /** Requests from clients are tiny; anything bigger is rejected. */
    private static final int MAX_MESSAGE_LENGTH = 1024;
    /** Server answers can hold long non-Latin names, which percent-encoding makes up to 9x longer. */
    private static final int MAX_STATE_LENGTH = 4096;
    private static final Pattern ID = Pattern.compile("^[a-z_]{1,32}$");

    public record CreateRequest(String name, String leader, String colorId, String governmentId) { }

    public record RenameRequest(String name) { }

    /** Server to client: the current state, whether to open the screen, and a status code. */
    public record StateMessage(boolean open, CivError error, CivView view) { }

    private CivWire() { }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Returns null if the text is not valid percent-encoding. */
    public static String dec(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- client -> server ----

    public static String encodeCreate(String name, String leader, String colorId, String governmentId) {
        return "create\t" + enc(name) + "\t" + enc(leader) + "\t" + colorId + "\t" + governmentId;
    }

    public static CreateRequest decodeCreate(String text) {
        String[] f = split(text, "create", 5);
        if (f == null) return null;
        String name = dec(f[1]);
        String leader = dec(f[2]);
        if (name == null || leader == null || !ID.matcher(f[3]).matches() || !ID.matcher(f[4]).matches()) return null;
        return new CreateRequest(name, leader, f[3], f[4]);
    }

    public static String encodeRename(String name) {
        return "rename\t" + enc(name);
    }

    public static RenameRequest decodeRename(String text) {
        String[] f = split(text, "rename", 2);
        if (f == null) return null;
        String name = dec(f[1]);
        return name == null ? null : new RenameRequest(name);
    }

    // ---- server -> client ----

    public static String encodeState(StateMessage m) {
        StringBuilder sb = new StringBuilder("state\t");
        sb.append(m.open() ? '1' : '0').append('\t').append(m.error().code()).append('\t');
        CivView v = m.view();
        if (v == null) {
            sb.append('0');
        } else {
            sb.append("1\t").append(enc(v.name())).append('\t').append(enc(v.leaderName())).append('\t')
                    .append(v.colorId()).append('\t').append(v.governmentId()).append('\t')
                    .append(v.treasury()).append('\t').append(v.population()).append('\t')
                    .append(v.settlements()).append('\t').append(v.stageId()).append('\t')
                    .append(v.economyId()).append('\t').append(v.createdAtEpochMs()).append('\t')
                    .append(v.foundedSettlements()).append('\t');
            SettlementView c = v.capital();
            if (c == null) {
                sb.append('0');
            } else {
                sb.append("1\t").append(enc(c.name())).append('\t').append(c.typeId()).append('\t')
                        .append(c.dimensionId()).append('\t').append(c.x()).append('\t').append(c.y()).append('\t')
                        .append(c.z()).append('\t').append(c.level()).append('\t').append(c.markerStatusId());
            }
        }
        return sb.toString();
    }

    public static StateMessage decodeState(String text) {
        if (text == null || text.length() > MAX_STATE_LENGTH) return null;
        String[] f = text.split("\t", -1);
        if (f.length < 4 || !"state".equals(f[0])) return null;
        boolean open;
        if ("1".equals(f[1])) open = true;
        else if ("0".equals(f[1])) open = false;
        else return null;
        CivError error = CivError.fromCode(f[2]);
        if ("0".equals(f[3])) {
            return f.length == 4 ? new StateMessage(open, error, null) : null;
        }
        if (!"1".equals(f[3])) return null;
        // 16 fields without a capital, 24 with one (see encodeState)
        if (f.length < 16 || !("0".equals(f[15]) ? f.length == 16 : "1".equals(f[15]) && f.length == 24)) return null;
        try {
            String name = dec(f[4]);
            String leader = dec(f[5]);
            if (name == null || leader == null) return null;
            long treasury = Long.parseLong(f[8]);
            long population = Long.parseLong(f[9]);
            int settlements = Integer.parseInt(f[10]);
            long created = Long.parseLong(f[13]);
            int founded = Integer.parseInt(f[14]);
            if (treasury < 0 || population < 0 || settlements < 0 || created < 0 || founded < 0) return null;
            if (!ID.matcher(f[6]).matches() || !ID.matcher(f[7]).matches()
                    || !ID.matcher(f[11]).matches() || !ID.matcher(f[12]).matches()) return null;
            SettlementView capital = null;
            if ("1".equals(f[15])) {
                String capitalName = dec(f[16]);
                int x = Integer.parseInt(f[19]);
                int y = Integer.parseInt(f[20]);
                int z = Integer.parseInt(f[21]);
                int level = Integer.parseInt(f[22]);
                if (capitalName == null || !SettlementRules.safeName(capitalName)
                        || !ID.matcher(f[17]).matches() || !SettlementRules.validNamespacedId(f[18])
                        || !SettlementRules.validPosition(x, y, z) || !SettlementRules.validLevel(level)
                        || !ID.matcher(f[23]).matches()) return null;
                capital = new SettlementView(capitalName, f[17], f[18], x, y, z, level, f[23]);
            }
            return new StateMessage(open, error,
                    new CivView(name, leader, f[6], f[7], treasury, population, settlements, f[11], f[12], created,
                            founded, capital));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Splits a message and checks the tag and field count. Returns null if anything is off. */
    private static String[] split(String text, String tag, int fields) {
        if (text == null || text.length() > MAX_MESSAGE_LENGTH) return null;
        String[] f = text.split("\t", -1);
        if (f.length != fields || !tag.equals(f[0])) return null;
        return f;
    }
}
