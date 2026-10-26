package com.riseofnations.core.persist;

import com.riseofnations.core.civ.CivColor;
import com.riseofnations.core.civ.CivNames;
import com.riseofnations.core.civ.CivWire;
import com.riseofnations.core.civ.Civilization;
import com.riseofnations.core.civ.DevelopmentStage;
import com.riseofnations.core.civ.EconomicStatus;
import com.riseofnations.core.civ.Government;
import com.riseofnations.core.economy.Ledger;
import com.riseofnations.core.settlement.Settlement;
import com.riseofnations.core.settlement.SettlementRules;
import com.riseofnations.core.settlement.SettlementService;
import com.riseofnations.core.settlement.SettlementType;
import com.riseofnations.core.economy.WageTracker;
import com.riseofnations.core.story.PlayerStory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns CoreState into a versioned text block and back. Pure Java.
 *
 * Safety rules:
 *  - Bad or unknown lines are skipped with a warning; they never crash loading.
 *  - A save from a NEWER schema is refused (refused=true). The caller must keep the raw text and
 *    must not overwrite it, so a downgrade cannot destroy data.
 *  - Negative balances and unsafe ids are dropped; the ledger is reconciled.
 */
public final class StateCodec {

    public static final int SCHEMA_VERSION = 3;
    private static final String HEADER = "ron-state";

    public record Decoded(CoreState state, List<String> warnings, boolean refused) { }

    private StateCodec() { }

    public static String encode(CoreState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\t').append(SCHEMA_VERSION).append('\n');
        Ledger l = state.ledger();
        sb.append("minted\t").append(l.totalMinted()).append('\n');
        sb.append("burned\t").append(l.totalBurned()).append('\n');
        for (Map.Entry<String, Long> e : l.balancesView().entrySet()) {
            sb.append("bal\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        for (Civilization c : state.civilizationsView().values()) {
            sb.append("civ\t").append(c.ownerId()).append('\t').append(CivWire.enc(c.name())).append('\t')
                    .append(CivWire.enc(c.leaderName())).append('\t').append(c.color().id()).append('\t')
                    .append(c.government().id()).append('\t').append(c.population()).append('\t')
                    .append(c.settlements()).append('\t').append(c.stage().id()).append('\t')
                    .append(c.economy().id()).append('\t').append(c.createdAtEpochMs()).append('\n');
        }
        for (Settlement t : state.settlementsView().values()) {
            sb.append("settle\t").append(t.id()).append('\t').append(t.ownerId()).append('\t')
                    .append(CivWire.enc(t.name())).append('\t').append(t.type().id()).append('\t')
                    .append(t.dimensionId()).append('\t').append(t.x()).append('\t').append(t.y()).append('\t')
                    .append(t.z()).append('\t').append(t.level()).append('\t').append(t.createdAtEpochMs()).append('\t')
                    .append(t.markerX()).append('\t').append(t.markerY()).append('\t').append(t.markerZ()).append('\t')
                    .append(t.markerBlockId()).append('\n');
        }
        for (Map.Entry<String, Long> e : state.wages().lastPaidView().entrySet()) {
            int bar = e.getKey().indexOf('|');
            sb.append("wage\t").append(e.getKey(), 0, bar).append('\t').append(e.getKey().substring(bar + 1))
                    .append('\t').append(e.getValue()).append('\n');
        }
        for (Map.Entry<String, long[]> e : state.wages().dailyView().entrySet()) {
            sb.append("daily\t").append(e.getKey()).append('\t').append(e.getValue()[0]).append('\t')
                    .append(e.getValue()[1]).append('\n');
        }
        for (Map.Entry<String, PlayerStory> pe : state.storiesView().entrySet()) {
            String p = pe.getKey();
            PlayerStory s = pe.getValue();
            if (s.started()) sb.append("started\t").append(p).append('\n');
            sb.append("rep\t").append(p).append('\t').append(s.reputation()).append('\n');
            for (Map.Entry<String, PlayerStory.Status> e : s.statusView().entrySet()) {
                sb.append("st\t").append(p).append('\t').append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            for (Map.Entry<String, Long> e : s.progressView().entrySet()) {
                int slash = e.getKey().indexOf('/');
                sb.append("pg\t").append(p).append('\t').append(e.getKey(), 0, slash).append('\t')
                        .append(e.getKey().substring(slash + 1)).append('\t').append(e.getValue()).append('\n');
            }
            for (String f : s.flagsView()) sb.append("flag\t").append(p).append('\t').append(f).append('\n');
            for (String f : s.rewardsPaidView()) sb.append("paid\t").append(p).append('\t').append(f).append('\n');
            for (String f : s.unlocksView()) sb.append("unlock\t").append(p).append('\t').append(f).append('\n');
            for (String k : s.seenKeysView()) {
                int bar = k.indexOf('|');
                sb.append("seen\t").append(p).append('\t').append(k, 0, bar).append('\t').append(k.substring(bar + 1)).append('\n');
            }
        }
        return sb.toString();
    }

    public static Decoded decode(String text) {
        List<String> warnings = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return new Decoded(new CoreState(), warnings, false);   // brand new world
        }
        String[] lines = text.split("\n", -1);
        String[] head = lines[0].split("\t", -1);
        if (head.length != 2 || !HEADER.equals(head[0])) {
            warnings.add("Save data has no valid header; refusing to load it.");
            return new Decoded(new CoreState(), warnings, true);
        }
        int version;
        try {
            version = Integer.parseInt(head[1].trim());
        } catch (NumberFormatException e) {
            warnings.add("Save data version is not a number; refusing to load it.");
            return new Decoded(new CoreState(), warnings, true);
        }
        if (version > SCHEMA_VERSION || version < 1) {
            warnings.add("Save data is schema " + version + " but this mod understands up to " + SCHEMA_VERSION
                    + "; refusing to load so nothing is overwritten.");
            return new Decoded(new CoreState(), warnings, true);
        }

        long minted = 0;
        long burned = 0;
        Map<String, Long> balances = new TreeMap<>();
        WageTracker wages = new WageTracker();
        CoreState[] holder = new CoreState[1];
        Map<String, PlayerStory> stories = new TreeMap<>();
        List<Civilization> civs = new ArrayList<>();
        List<Settlement> settlementRecords = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            String[] f = line.split("\t", -1);
            try {
                switch (f[0]) {
                    case "minted" -> minted = Long.parseLong(need(f, 2)[1]);
                    case "burned" -> burned = Long.parseLong(need(f, 2)[1]);
                    case "bal" -> balances.put(need(f, 3)[1], Long.parseLong(f[2]));
                    case "civ" -> civs.add(decodeCivilization(f, i + 1, warnings));
                    case "settle" -> settlementRecords.add(decodeSettlement(f));
                    case "wage" -> {
                        need(f, 4);
                        if (!Ledger.validId(f[1]) || !Ledger.validId(f[2])) throw new IllegalArgumentException("unsafe id");
                        wages.restoreLastPaid(f[1] + "|" + f[2], Long.parseLong(f[3]));
                    }
                    case "daily" -> {
                        need(f, 4);
                        wages.restoreDaily(f[1], Long.parseLong(f[2]), Long.parseLong(f[3]));
                    }
                    case "started", "rep", "st", "pg", "flag", "paid", "unlock", "seen" -> {
                        if (f.length < 2 || !Ledger.validId(f[1])) throw new IllegalArgumentException("unsafe player id");
                        PlayerStory s = stories.computeIfAbsent(f[1], k -> new PlayerStory());
                        applyStoryLine(s, f);
                    }
                    default -> warnings.add("line " + (i + 1) + ": unknown record '" + f[0] + "' skipped");
                }
            } catch (RuntimeException e) {
                warnings.add("line " + (i + 1) + ": skipped (" + e.getMessage() + ")");
            }
        }

        Ledger.Restored restored = Ledger.restore(balances, minted, burned);
        if (restored.reconciled()) {
            warnings.add("Ledger totals were inconsistent and have been reconciled ("
                    + restored.droppedAccounts() + " bad accounts dropped).");
        }
        holder[0] = new CoreState(restored.ledger(), wages);
        for (Map.Entry<String, PlayerStory> e : stories.entrySet()) {
            PlayerStory target = holder[0].story(e.getKey());
            copyInto(target, e.getValue());
        }
        for (Civilization civ : civs) {
            if (holder[0].civilization(civ.ownerId()) != null) {
                warnings.add("duplicate civilization for " + civ.ownerId() + " ignored");
                continue;
            }
            holder[0].putCivilization(civ);
            String account = com.riseofnations.core.civ.CivilizationService.treasuryAccount(civ.ownerId());
            if (!holder[0].ledger().exists(account)) {
                holder[0].ledger().open(account);
                warnings.add("treasury account for " + civ.ownerId() + " was missing and has been opened empty");
            }
        }
        attachSettlements(holder[0], settlementRecords, warnings);
        return new Decoded(holder[0], warnings, false);
    }

    /**
     * Reads one "settle" line. Anything that does not look right throws, and the caller skips the line with a
     * warning: a settlement is a real place in the world, so it is never guessed or repaired. The name is the one
     * exception (it may be replaced by a default later, when the owning civilization is known).
     */
    private static Settlement decodeSettlement(String[] f) {
        if (f.length != 15) throw new IllegalArgumentException("expected 15 fields, got " + f.length);
        String id = safe(f[1]);
        String owner = safe(f[2]);
        String name = CivWire.dec(f[3]);
        SettlementType type = SettlementType.fromId(f[4]);
        if (type == null) throw new IllegalArgumentException("unknown settlement type '" + f[4] + "'");
        String dimension = f[5];
        if (!SettlementRules.validNamespacedId(dimension)) throw new IllegalArgumentException("bad dimension id");
        int x = Integer.parseInt(f[6]);
        int y = Integer.parseInt(f[7]);
        int z = Integer.parseInt(f[8]);
        int level = Integer.parseInt(f[9]);
        long created = Long.parseLong(f[10]);
        int mx = Integer.parseInt(f[11]);
        int my = Integer.parseInt(f[12]);
        int mz = Integer.parseInt(f[13]);
        String markerBlock = f[14];
        if (!SettlementRules.validPosition(x, y, z) || !SettlementRules.validPosition(mx, my, mz)) {
            throw new IllegalArgumentException("position out of range");
        }
        if (!SettlementRules.validLevel(level)) throw new IllegalArgumentException("level out of range");
        if (created < 0) throw new IllegalArgumentException("negative creation time");
        if (!SettlementRules.validNamespacedId(markerBlock)) throw new IllegalArgumentException("bad marker block id");
        return new Settlement(id, owner, type, name == null ? "" : name, dimension, x, y, z, level, created,
                mx, my, mz, markerBlock);
    }

    /**
     * Adds loaded settlements to the state, but only the ones that are consistent: the owner must have a
     * civilization, ids must be unique, and an owner can have only ONE capital (the first one in the file wins).
     */
    private static void attachSettlements(CoreState state, List<Settlement> records, List<String> warnings) {
        for (Settlement s : records) {
            Civilization owner = state.civilization(s.ownerId());
            if (owner == null) {
                warnings.add("settlement " + s.id() + " skipped: its owner has no civilization");
                continue;
            }
            if (state.settlement(s.id()) != null) {
                warnings.add("settlement " + s.id() + " skipped: duplicate id");
                continue;
            }
            if (s.type() == SettlementType.CAPITAL && SettlementService.capitalOf(state, s.ownerId()) != null) {
                warnings.add("settlement " + s.id() + " skipped: " + s.ownerId() + " already has a capital");
                continue;
            }
            Settlement toStore = s;
            if (!SettlementRules.safeName(s.name())) {
                toStore = s.withName(SettlementService.defaultCapitalName(owner.name()));
                warnings.add("settlement " + s.id() + ": unusable name replaced with a default");
            }
            state.putSettlement(toStore);
        }
    }

    /**
     * Reads one "civ" line. Missing or invalid fields get safe defaults (with a warning) instead of
     * losing the civilization. Throws only if the owner id itself is unusable.
     */
    private static Civilization decodeCivilization(String[] f, int lineNo, List<String> warnings) {
        if (f.length != 11) throw new IllegalArgumentException("expected 11 fields, got " + f.length);
        String owner = safe(f[1]);
        String name = CivWire.dec(f[2]);
        CivNames.NameCheck nameCheck = CivNames.check(name == null ? "" : name);
        if (!nameCheck.ok()) {
            warnings.add("line " + lineNo + ": invalid civilization name, using a default");
            nameCheck = new CivNames.NameCheck("Unnamed Civilization", null);
        }
        String leader = CivWire.dec(f[3]);
        CivNames.NameCheck leaderCheck = CivNames.check(leader == null ? "" : leader);
        if (!leaderCheck.ok()) {
            warnings.add("line " + lineNo + ": invalid leader name, using a default");
            leaderCheck = new CivNames.NameCheck("Unknown Leader", null);
        }
        CivColor color = CivColor.fromId(f[4]);
        if (color == null) {
            warnings.add("line " + lineNo + ": unknown color '" + f[4] + "', using default");
            color = CivColor.values()[0];
        }
        Government gov = Government.fromId(f[5]);
        if (gov == null) {
            warnings.add("line " + lineNo + ": unknown government '" + f[5] + "', using default");
            gov = Government.values()[0];
        }
        long population = parseOr(f[6], com.riseofnations.core.civ.CivilizationService.STARTING_POPULATION, 0, warnings, lineNo, "population");
        int settlements = (int) parseOr(f[7], com.riseofnations.core.civ.CivilizationService.STARTING_SETTLEMENTS, 0, warnings, lineNo, "settlements");
        long created = parseOr(f[10], 0, 0, warnings, lineNo, "created time");
        return new Civilization(owner, nameCheck.clean(), leaderCheck.clean(), color, gov, population,
                settlements, DevelopmentStage.fromIdOrDefault(f[8]), EconomicStatus.fromIdOrDefault(f[9]), created);
    }

    private static long parseOr(String text, long fallback, long min, List<String> warnings, int lineNo, String what) {
        try {
            long v = Long.parseLong(text);
            if (v >= min && v <= Integer.MAX_VALUE * 1000L) return v;
        } catch (NumberFormatException ignored) {
            // fall through to the default below
        }
        warnings.add("line " + lineNo + ": invalid " + what + ", using " + fallback);
        return fallback;
    }

    private static void applyStoryLine(PlayerStory s, String[] f) {
        switch (f[0]) {
            case "started" -> {
                need(f, 2);
                s.restoreStarted(true);
            }
            case "rep" -> s.restoreReputation(Integer.parseInt(need(f, 3)[2]));
            case "st" -> {
                need(f, 4);
                s.restoreStatus(f[2], PlayerStory.Status.valueOf(f[3]));
            }
            case "pg" -> {
                need(f, 5);
                s.restoreProgress(f[2] + "/" + f[3], Long.parseLong(f[4]));
            }
            case "flag" -> s.restoreFlag(safe(need(f, 3)[2]));
            case "paid" -> s.restorePaid(safe(need(f, 3)[2]));
            case "unlock" -> s.restoreUnlock(safe(need(f, 3)[2]));
            case "seen" -> {
                need(f, 4);
                s.restoreSeen(safe(f[2]) + "|" + safe(f[3]));
            }
            default -> throw new IllegalArgumentException("unreachable");
        }
    }

    /** Applies decoded values onto the CoreState-owned PlayerStory instance. */
    private static void copyInto(PlayerStory target, PlayerStory from) {
        target.restoreStarted(from.started());
        target.restoreReputation(from.reputation());
        from.statusView().forEach(target::restoreStatus);
        from.progressView().forEach(target::restoreProgress);
        from.flagsView().forEach(target::restoreFlag);
        from.rewardsPaidView().forEach(target::restorePaid);
        from.unlocksView().forEach(target::restoreUnlock);
        from.seenKeysView().forEach(target::restoreSeen);
    }

    private static String[] need(String[] f, int count) {
        if (f.length != count) throw new IllegalArgumentException("expected " + count + " fields, got " + f.length);
        return f;
    }

    private static String safe(String id) {
        if (!Ledger.validId(id)) throw new IllegalArgumentException("unsafe id");
        return id;
    }
}
