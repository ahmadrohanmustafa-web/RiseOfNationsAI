package com.riseofnations.core;

import com.riseofnations.core.civ.CivView;
import com.riseofnations.core.civ.CivWire;
import com.riseofnations.core.civ.CivError;
import com.riseofnations.core.civ.CivilizationService;
import com.riseofnations.core.config.RonSettings;
import com.riseofnations.core.persist.CoreState;
import com.riseofnations.core.settlement.MarkerStatus;
import com.riseofnations.core.settlement.Settlement;
import com.riseofnations.core.settlement.SettlementError;
import com.riseofnations.core.settlement.SettlementRules;
import com.riseofnations.core.settlement.SettlementService;
import com.riseofnations.core.settlement.SettlementText;
import com.riseofnations.core.settlement.SettlementType;
import com.riseofnations.core.settlement.SitePlanner;
import com.riseofnations.core.settlement.SitePlanner.SiteInfo;

import java.util.List;

import static com.riseofnations.core.CivilizationTests.A;
import static com.riseofnations.core.CivilizationTests.B;
import static com.riseofnations.core.TestKit.check;
import static com.riseofnations.core.TestKit.eq;
import static com.riseofnations.core.TestKit.test;

final class SettlementTests {

    private SettlementTests() { }

    static final String OVERWORLD = "minecraft:overworld";

    /** A world with a civilization named "Rome" owned by A. */
    static CoreState worldWithRome() {
        CoreState s = new CoreState();
        check(CivilizationTests.create(s, new RonSettings(), A, "Rome").ok(), "setup: civilization");
        return s;
    }

    static SettlementService.Result found(CoreState s, String owner, String id) {
        return SettlementService.foundCapital(s, owner, id, OVERWORLD, 100, 64, -200, 101, 64, -200, 1_700_000_000_000L);
    }

    static void run() {
        System.out.println("Capital settlement");

        test("1. a player without a civilization cannot found a capital", () -> {
            CoreState s = new CoreState();
            SettlementService.Result r = found(s, A, "s-1");
            eq(SettlementError.NO_CIVILIZATION, r.error(), "refused");
            check(s.settlementsView().isEmpty(), "nothing stored");
            eq(SettlementError.NO_CIVILIZATION, SettlementService.precheck(s, A, OVERWORLD), "precheck agrees");
        });

        test("2+3. a civilization can found one capital, stored with the right owner, dimension and coordinates", () -> {
            CoreState s = worldWithRome();
            SettlementService.Result r = found(s, A, "s-1");
            check(r.ok(), "founded: " + r.error());
            Settlement c = r.settlement();
            eq("s-1", c.id(), "id");
            eq(A, c.ownerId(), "owner");
            eq(SettlementType.CAPITAL, c.type(), "type");
            eq("Rome Capital", c.name(), "default name is <Civilization Name> Capital");
            eq(OVERWORLD, c.dimensionId(), "dimension");
            eq(100, c.x(), "x");
            eq(64, c.y(), "y");
            eq(-200, c.z(), "z");
            eq(1, c.level(), "level starts at 1");
            eq(1_700_000_000_000L, c.createdAtEpochMs(), "created timestamp");
            eq(101, c.markerX(), "marker x");
            eq(SettlementService.MARKER_BASE_BLOCK, c.markerBlockId(), "marker block recorded");
            check(s.settlement("s-1") == c, "stored under its id");
            check(SettlementService.capitalOf(s, A) == c, "found as the owner's capital");
        });

        test("4. a second capital for the same civilization is rejected and changes nothing", () -> {
            CoreState s = worldWithRome();
            check(found(s, A, "s-1").ok(), "first");
            SettlementService.Result second = SettlementService.foundCapital(s, A, "s-2", OVERWORLD, 5, 70, 5, 6, 70, 5, 0);
            eq(SettlementError.CAPITAL_EXISTS, second.error(), "refused");
            eq(1, s.settlementsView().size(), "still one settlement");
            eq(100, SettlementService.capitalOf(s, A).x(), "original capital untouched");
            eq(SettlementError.CAPITAL_EXISTS, SettlementService.precheck(s, A, OVERWORLD), "precheck also refuses");
        });

        test("5. repeating the request many times, even with different ids, never creates a duplicate", () -> {
            CoreState s = worldWithRome();
            int created = 0;
            for (int i = 0; i < 25; i++) {
                if (found(s, A, "s-" + i).ok()) created++;
            }
            eq(1, created, "exactly one success");
            eq(1, s.settlementsView().size(), "exactly one record");
            check(!found(s, A, "s-0").ok(), "same id again is refused too");
        });

        test("6. ownership: one player's capital is invisible to and unchangeable by another", () -> {
            CoreState s = worldWithRome();
            check(CivilizationTests.create(s, new RonSettings(), B, "Athens").ok(), "second civilization");
            check(found(s, A, "s-a").ok(), "A founds");
            eq(null, SettlementService.capitalOf(s, B), "B has no capital");
            eq(null, CivilizationService.view(s, B).capital(), "B's view shows none");
            eq(0, CivilizationService.view(s, B).foundedSettlements(), "B's founded count is 0");
            SettlementService.Result b = SettlementService.foundCapital(s, B, "s-b", OVERWORLD, 9, 70, 9, 10, 70, 9, 0);
            check(b.ok(), "B can found their own");
            eq(B, b.settlement().ownerId(), "owned by B");
            eq(A, s.settlement("s-a").ownerId(), "A's record unchanged");
            eq(100, s.settlement("s-a").x(), "A's coordinates unchanged");
            eq("Athens Capital", SettlementService.capitalOf(s, B).name(), "named after B's civilization");
        });

        test("only supported dimensions are accepted", () -> {
            CoreState s = worldWithRome();
            for (String dim : new String[] {"minecraft:the_nether", "minecraft:the_end", "mod:custom", "", null, "Overworld"}) {
                eq(SettlementError.UNSUPPORTED_DIMENSION,
                        SettlementService.foundCapital(s, A, "s-1", dim, 0, 64, 0, 1, 64, 0, 0).error(), "dimension " + dim);
            }
            check(s.settlementsView().isEmpty(), "nothing stored");
        });

        test("invalid owner, settlement id and positions are rejected without storing anything", () -> {
            CoreState s = worldWithRome();
            eq(SettlementError.INVALID_PLAYER, found(s, "bad id", "s-1").error(), "bad owner");
            eq(SettlementError.INVALID_PLAYER, found(s, null, "s-1").error(), "null owner");
            eq(SettlementError.INVALID_ID, found(s, A, "Bad Id").error(), "bad settlement id");
            eq(SettlementError.INVALID_ID, found(s, A, null).error(), "null settlement id");
            eq(SettlementError.INVALID_POSITION,
                    SettlementService.foundCapital(s, A, "s-1", OVERWORLD, 40_000_000, 64, 0, 0, 64, 0, 0).error(), "x too large");
            eq(SettlementError.INVALID_POSITION,
                    SettlementService.foundCapital(s, A, "s-1", OVERWORLD, 0, 99_999, 0, 0, 64, 0, 0).error(), "y too large");
            eq(SettlementError.INVALID_POSITION,
                    SettlementService.foundCapital(s, A, "s-1", OVERWORLD, 0, 64, 0, 0, -99_999, 0, 0).error(), "marker y too small");
            check(s.settlementsView().isEmpty(), "nothing stored");
        });

        test("a settlement id that is already taken cannot be reused by another owner", () -> {
            CoreState s = worldWithRome();
            CivilizationTests.create(s, new RonSettings(), B, "Athens");
            check(found(s, A, "shared-id").ok(), "A");
            eq(SettlementError.INVALID_ID, found(s, B, "shared-id").error(), "B cannot take the same id");
            eq(null, SettlementService.capitalOf(s, B), "B still has no capital");
        });

        test("renaming the civilization keeps the capital linked and keeps its name", () -> {
            CoreState s = worldWithRome();
            found(s, A, "s-1");
            check(CivilizationService.rename(s, A, "New Rome").ok(), "renamed");
            Settlement c = SettlementService.capitalOf(s, A);
            check(c != null, "still linked through the owner id");
            eq("Rome Capital", c.name(), "capital name is stable");
        });

        test("founding does not touch the treasury or the simulated settlement count", () -> {
            CoreState s = worldWithRome();
            long before = s.ledger().balance("civ:" + A);
            found(s, A, "s-1");
            eq(before, s.ledger().balance("civ:" + A), "treasury unchanged");
            eq(1, s.civilization(A).settlements(), "simulated count unchanged");
        });

        System.out.println("Capital in the overview snapshot");

        test("the snapshot separates the simulated count from the founded count", () -> {
            CoreState s = worldWithRome();
            CivView before = CivilizationService.view(s, A);
            eq(1, before.settlements(), "simulated settlements");
            eq(0, before.foundedSettlements(), "founded settlements");
            eq(null, before.capital(), "no capital yet");
            found(s, A, "s-1");
            CivView after = CivilizationService.view(s, A, MarkerStatus.PRESENT);
            eq(1, after.settlements(), "simulated count is independent");
            eq(1, after.foundedSettlements(), "founded");
            eq("Rome Capital", after.capital().name(), "capital name");
            eq("present", after.capital().markerStatusId(), "marker status passed through");
            eq("unknown", CivilizationService.view(s, A).capital().markerStatusId(), "default status is unknown");
        });

        test("a snapshot with a capital survives the wire format, including worst-case non-Latin names", () -> {
            CoreState s = new CoreState();
            String longName = "東".repeat(32);
            check(CivilizationService.create(s, new RonSettings(), A, longName, "京".repeat(32), "red", "republic", 5).ok(), "create");
            found(s, A, "s-1");
            CivView v = CivilizationService.view(s, A, MarkerStatus.MISSING);
            String wire = CivWire.encodeState(new CivWire.StateMessage(true, CivError.NONE, v));
            check(wire.length() > 900 && wire.length() < 4096, "a worst-case name pair is close to the old 1024 limit but far below the new one: " + wire.length());
            CivWire.StateMessage back = CivWire.decodeState(wire);
            check(back != null, "still decodes");
            eq(v, back.view(), "identical");
            eq("missing", back.view().capital().markerStatusId(), "marker status");
        });

        test("state messages with a damaged capital section are rejected", () -> {
            CoreState s = worldWithRome();
            found(s, A, "s-1");
            String ok = CivWire.encodeState(new CivWire.StateMessage(true, CivError.NONE, CivilizationService.view(s, A)));
            check(CivWire.decodeState(ok) != null, "baseline decodes");
            String[] f = ok.split("\t", -1);
            check(f.length == 24, "24 fields with a capital, got " + f.length);
            check(CivWire.decodeState(String.join("\t", java.util.Arrays.copyOf(f, 20))) == null, "truncated capital");
            for (int index : new int[] {18, 19, 22}) {
                String[] g = f.clone();
                g[index] = index == 18 ? "NOT A DIMENSION" : "abc";
                check(CivWire.decodeState(String.join("\t", g)) == null, "bad field " + index);
            }
            String[] lvl = f.clone();
            lvl[22] = "0";
            check(CivWire.decodeState(String.join("\t", lvl)) == null, "level 0");
            String[] far = f.clone();
            far[19] = "99999999";
            check(CivWire.decodeState(String.join("\t", far)) == null, "x out of range");
            String[] flag = f.clone();
            flag[15] = "2";
            check(CivWire.decodeState(String.join("\t", flag)) == null, "bad capital flag");
        });

        System.out.println("Marker site rules");

        test("a marker needs height room, two empty blocks and solid ground", () -> {
            eq(SettlementError.NONE, SitePlanner.evaluate(new SiteInfo(true, true, true, true)), "good site");
            eq(SettlementError.OUT_OF_BUILD_HEIGHT, SitePlanner.evaluate(new SiteInfo(false, true, true, true)), "height");
            eq(SettlementError.MARKER_BLOCKED, SitePlanner.evaluate(new SiteInfo(true, false, true, true)), "base occupied (never replaced)");
            eq(SettlementError.MARKER_BLOCKED, SitePlanner.evaluate(new SiteInfo(true, true, false, true)), "top occupied");
            eq(SettlementError.NO_GROUND, SitePlanner.evaluate(new SiteInfo(true, true, true, false)), "no ground");
            eq(SettlementError.OUT_OF_BUILD_HEIGHT, SitePlanner.evaluate(new SiteInfo(false, false, false, false)), "height is reported first");
        });

        test("settlement rules: names, positions, levels, ids", () -> {
            check(SettlementRules.safeName("Rome Capital"), "normal");
            check(!SettlementRules.safeName(""), "empty");
            check(!SettlementRules.safeName(" Rome"), "leading space");
            check(!SettlementRules.safeName("Ro\nme"), "newline");
            check(!SettlementRules.safeName("Ro\u00A7me"), "formatting code");
            check(!SettlementRules.safeName("a".repeat(65)), "too long");
            check(SettlementRules.safeName("a".repeat(64)), "64 is fine");
            check(SettlementRules.validNamespacedId("minecraft:overworld"), "dimension id");
            check(!SettlementRules.validNamespacedId("overworld"), "no namespace");
            check(!SettlementRules.validNamespacedId("minecraft:Over World"), "bad characters");
            check(SettlementRules.validPosition(30_000_000, 4096, -30_000_000), "limits are inclusive");
            check(!SettlementRules.validPosition(Integer.MAX_VALUE, 0, 0), "huge x");
            check(!SettlementRules.validPosition(Integer.MIN_VALUE, 0, 0), "huge negative x");
            check(SettlementRules.validLevel(1) && !SettlementRules.validLevel(0) && !SettlementRules.validLevel(-3), "levels");
        });

        test("command text: dimension names, coordinates and instructions", () -> {
            eq("Overworld", SettlementText.dimensionName("minecraft:overworld"), "overworld");
            eq("Nether", SettlementText.dimensionName("minecraft:the_nether"), "nether");
            eq("mod:custom", SettlementText.dimensionName("mod:custom"), "unknown ids are shown as they are");
            CoreState s = worldWithRome();
            List<String> none = SettlementText.notFounded("Rome");
            check(none.stream().anyMatch(l -> l.contains("/ron settlement create")), "explains how to create one");
            check(none.stream().anyMatch(l -> l.contains("Not founded")), "says it is not founded");
            found(s, A, "s-1");
            CivView v = CivilizationService.view(s, A, MarkerStatus.PRESENT);
            List<String> lines = SettlementText.describe("Rome", v.capital(), v.foundedSettlements());
            String all = String.join("\n", lines);
            check(all.contains("Rome Capital") && all.contains("100, 64, -200") && all.contains("Overworld")
                    && all.contains("level: 1") && all.contains("Founded settlements: 1"), "all required facts shown: " + all);
            check(SettlementText.founded(s.settlement("s-1")).contains("100, 64, -200"), "success message has the coordinates");
        });
    }
}
