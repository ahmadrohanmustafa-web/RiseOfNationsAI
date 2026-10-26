package com.riseofnations.core;

import com.riseofnations.core.civ.CivError;
import com.riseofnations.core.civ.CivNames;
import com.riseofnations.core.civ.CivView;
import com.riseofnations.core.civ.CivWire;
import com.riseofnations.core.civ.Civilization;
import com.riseofnations.core.civ.CivilizationService;
import com.riseofnations.core.civ.DevelopmentStage;
import com.riseofnations.core.civ.EconomicStatus;
import com.riseofnations.core.civ.RequestThrottle;
import com.riseofnations.core.config.RonSettings;
import com.riseofnations.core.economy.Ledger;
import com.riseofnations.core.persist.CoreState;

import java.util.Arrays;
import java.util.List;

import static com.riseofnations.core.TestKit.check;
import static com.riseofnations.core.TestKit.eq;
import static com.riseofnations.core.TestKit.test;

final class CivilizationTests {

    private CivilizationTests() { }

    static final String A = "11111111-1111-1111-1111-111111111111";
    static final String B = "22222222-2222-2222-2222-222222222222";

    static CivilizationService.Result create(CoreState s, RonSettings cfg, String owner, String name) {
        return CivilizationService.create(s, cfg, owner, name, "Aldric Stone", "blue", "monarchy", 1_700_000_000_000L);
    }

    private static void problem(String raw, CivNames.Problem expected) {
        CivNames.NameCheck c = CivNames.check(raw);
        eq(expected, c.problem(), "problem for <" + raw + ">");
    }

    static void run() {
        System.out.println("Names");

        test("valid names are cleaned: trimmed and inner spaces collapsed", () -> {
            eq("New Rome", CivNames.check("  New   Rome  ").clean(), "collapsed");
            eq("O'Brien-Smith Jr.", CivNames.check("O'Brien-Smith Jr.").clean(), "punctuation");
            eq("Zürich", CivNames.check("Zürich").clean(), "unicode letters");
            eq("東京都", CivNames.check("東京都").clean(), "other scripts");
        });

        test("empty, blank and null names are rejected", () -> {
            problem(null, CivNames.Problem.EMPTY);
            problem("", CivNames.Problem.EMPTY);
            problem("     ", CivNames.Problem.EMPTY);
        });

        test("length limits: 3 to 32 characters", () -> {
            problem("ab", CivNames.Problem.TOO_SHORT);
            check(CivNames.check("abc").ok(), "3 is fine");
            check(CivNames.check("a".repeat(32)).ok(), "32 is fine");
            problem("a".repeat(33), CivNames.Problem.TOO_LONG);
            problem("a".repeat(100_000), CivNames.Problem.TOO_LONG);
        });

        test("formatting codes, control characters and invisible characters are rejected", () -> {
            problem("\u00A7aRome", CivNames.Problem.INVALID_CHARS);
            problem("Rome\tCity", CivNames.Problem.INVALID_CHARS);
            problem("Rome\nCity", CivNames.Problem.INVALID_CHARS);
            problem("Rome\rCity", CivNames.Problem.INVALID_CHARS);
            problem("Ro\u200Bme", CivNames.Problem.INVALID_CHARS);
            problem("Rome\u0000", CivNames.Problem.INVALID_CHARS);
            problem("Rome\u202Eabc", CivNames.Problem.INVALID_CHARS);
        });

        test("symbols, emoji and markup are rejected; a name needs at least one letter", () -> {
            problem("Rome\uD83D\uDE00", CivNames.Problem.INVALID_CHARS);
            problem("<b>Rome</b>", CivNames.Problem.INVALID_CHARS);
            problem("Rome_City", CivNames.Problem.INVALID_CHARS);
            problem("{\"a\":1}", CivNames.Problem.INVALID_CHARS);
            problem("12345", CivNames.Problem.INVALID_CHARS);
            problem("- - -", CivNames.Problem.INVALID_CHARS);
        });

        test("duplicate-detection key ignores case, spaces, punctuation and full-width letters", () -> {
            String k = CivNames.key("Rome");
            eq(k, CivNames.key("ROME"), "case");
            eq(k, CivNames.key("R o m e"), "spaces");
            eq(k, CivNames.key("R.o.m.e"), "dots");
            eq(k, CivNames.key("\uFF52\uFF4F\uFF4D\uFF45"), "full-width");
            check(!k.equals(CivNames.key("Rome2")), "different names differ");
        });

        System.out.println("Civilization creation");

        test("creating a civilization gives the documented starting values", () -> {
            CoreState s = new CoreState();
            RonSettings cfg = new RonSettings();
            CivilizationService.Result r = create(s, cfg, A, "  New   Rome ");
            check(r.ok(), "created: " + r.error());
            Civilization c = r.civilization();
            eq("New Rome", c.name(), "name");
            eq("Aldric Stone", c.leaderName(), "leader");
            eq(100L, c.population(), "population");
            eq(1, c.settlements(), "settlements");
            eq(DevelopmentStage.SETTLED, c.stage(), "stage");
            eq(EconomicStatus.STABLE, c.economy(), "economy");
            eq("blue", c.color().id(), "color");
            eq("monarchy", c.government().id(), "government");
            check(s.civilization(A) == c, "stored");
        });

        test("the treasury is a real ledger balance and uses the configured starting value", () -> {
            CoreState s = new CoreState();
            RonSettings cfg = new RonSettings();
            check(create(s, cfg, A, "Alpha City").ok(), "A");
            eq(5000L, s.ledger().balance("civ:" + A), "default 5000");
            cfg.startingSettlementTreasury = 1234;
            check(create(s, cfg, B, "Beta City").ok(), "B");
            eq(1234L, s.ledger().balance("civ:" + B), "configured");
            eq(6234L, s.ledger().totalMinted(), "minted exactly what was granted");
            eq(s.ledger().totalMinted() - s.ledger().totalBurned(), s.ledger().totalSupply(), "supply invariant");
        });

        test("a starting treasury of zero creates an empty account and mints nothing", () -> {
            CoreState s = new CoreState();
            RonSettings cfg = new RonSettings();
            cfg.startingSettlementTreasury = 0;
            check(create(s, cfg, A, "Poor Land").ok(), "created");
            check(s.ledger().exists("civ:" + A), "account exists");
            eq(0L, s.ledger().totalMinted(), "nothing minted");
        });

        test("a player cannot create a second civilization, and no money is duplicated", () -> {
            CoreState s = new CoreState();
            RonSettings cfg = new RonSettings();
            check(create(s, cfg, A, "First Realm").ok(), "first");
            CivilizationService.Result again = create(s, cfg, A, "Second Realm");
            eq(CivError.ALREADY_HAS_CIVILIZATION, again.error(), "refused");
            eq("First Realm", s.civilization(A).name(), "original kept");
            eq(5000L, s.ledger().totalMinted(), "no second grant");
        });

        test("duplicate names are refused, ignoring case and punctuation, and nothing is created", () -> {
            CoreState s = new CoreState();
            RonSettings cfg = new RonSettings();
            check(create(s, cfg, A, "Rome").ok(), "first");
            for (String variant : List.of("rome", "ROME", "R.o.m.e", "Ro me", "\uFF52\uFF4F\uFF4D\uFF45")) {
                eq(CivError.NAME_TAKEN, create(s, cfg, B, variant).error(), "variant " + variant);
            }
            check(s.civilization(B) == null, "no civilization for B");
            check(!s.ledger().exists("civ:" + B), "no account for B");
            eq(5000L, s.ledger().totalMinted(), "no extra money");
        });

        test("every kind of bad input is refused without changing anything", () -> {
            CoreState s = new CoreState();
            RonSettings cfg = new RonSettings();
            eq(CivError.NAME_EMPTY, CivilizationService.create(s, cfg, A, "", "Leader One", "red", "republic", 0).error(), "empty name");
            eq(CivError.NAME_TOO_SHORT, CivilizationService.create(s, cfg, A, "ab", "Leader One", "red", "republic", 0).error(), "short name");
            eq(CivError.NAME_TOO_LONG, CivilizationService.create(s, cfg, A, "x".repeat(40), "Leader One", "red", "republic", 0).error(), "long name");
            eq(CivError.NAME_INVALID_CHARS, CivilizationService.create(s, cfg, A, "\u00A74Evil", "Leader One", "red", "republic", 0).error(), "formatting code");
            eq(CivError.LEADER_EMPTY, CivilizationService.create(s, cfg, A, "Goodland", " ", "red", "republic", 0).error(), "empty leader");
            eq(CivError.LEADER_TOO_SHORT, CivilizationService.create(s, cfg, A, "Goodland", "Al", "red", "republic", 0).error(), "short leader");
            eq(CivError.LEADER_TOO_LONG, CivilizationService.create(s, cfg, A, "Goodland", "L".repeat(33), "red", "republic", 0).error(), "long leader");
            eq(CivError.LEADER_INVALID_CHARS, CivilizationService.create(s, cfg, A, "Goodland", "Lea\tder", "red", "republic", 0).error(), "tab in leader");
            eq(CivError.UNKNOWN_COLOR, CivilizationService.create(s, cfg, A, "Goodland", "Leader One", "pink", "republic", 0).error(), "color");
            eq(CivError.UNKNOWN_GOVERNMENT, CivilizationService.create(s, cfg, A, "Goodland", "Leader One", "red", "empire", 0).error(), "government");
            eq(CivError.INVALID_PLAYER, CivilizationService.create(s, cfg, "bad id", "Goodland", "Leader One", "red", "republic", 0).error(), "player");
            eq(CivError.INVALID_PLAYER, CivilizationService.create(s, cfg, null, "Goodland", "Leader One", "red", "republic", 0).error(), "null player");
            check(s.civilizationsView().isEmpty(), "no civilizations");
            check(s.ledger().balancesView().isEmpty(), "no ledger accounts");
            eq(0L, s.ledger().totalMinted(), "nothing minted");
        });

        test("a leftover treasury account is reused and never minted again", () -> {
            CoreState s = new CoreState();
            s.ledger().open("civ:" + A);
            s.ledger().mint("civ:" + A, 777);
            check(create(s, new RonSettings(), A, "Reborn").ok(), "created");
            eq(777L, s.ledger().balance("civ:" + A), "balance unchanged");
            eq(777L, s.ledger().totalMinted(), "no new money");
        });

        System.out.println("Rename");

        test("renaming works, keeps everything else, and allows changing only the capitalization", () -> {
            CoreState s = new CoreState();
            check(create(s, new RonSettings(), A, "Rome").ok(), "created");
            CivilizationService.Result r = CivilizationService.rename(s, A, "  New  Rome ");
            check(r.ok(), "renamed");
            eq("New Rome", s.civilization(A).name(), "name");
            eq("Aldric Stone", s.civilization(A).leaderName(), "leader unchanged");
            eq(5000L, s.ledger().balance("civ:" + A), "treasury unchanged");
            check(CivilizationService.rename(s, A, "NEW ROME").ok(), "case-only change by the same owner is allowed");
            eq("NEW ROME", s.civilization(A).name(), "new capitalization");
        });

        test("rename is validated: invalid, same, taken, and no civilization", () -> {
            CoreState s = new CoreState();
            RonSettings cfg = new RonSettings();
            create(s, cfg, A, "Rome");
            create(s, cfg, B, "Athens");
            eq(CivError.NAME_INVALID_CHARS, CivilizationService.rename(s, A, "\u00A7cRed").error(), "formatting code");
            eq(CivError.NAME_TOO_SHORT, CivilizationService.rename(s, A, "ab").error(), "short");
            eq(CivError.SAME_NAME, CivilizationService.rename(s, A, " Rome ").error(), "same");
            eq(CivError.NAME_TAKEN, CivilizationService.rename(s, A, "ATHENS").error(), "taken by B");
            eq(CivError.NO_CIVILIZATION, CivilizationService.rename(s, "33333333-3333-3333-3333-333333333333", "Nowhere").error(), "none");
            eq(CivError.INVALID_PLAYER, CivilizationService.rename(s, "bad id", "Nowhere").error(), "bad id");
            eq("Rome", s.civilization(A).name(), "still Rome");
        });

        test("the view comes from the ledger, so it cannot disagree with the real treasury", () -> {
            CoreState s = new CoreState();
            create(s, new RonSettings(), A, "Rome");
            s.ledger().open("player:x");
            eq(5000L, CivilizationService.view(s, A).treasury(), "initial");
            s.ledger().transfer("civ:" + A, "player:x", 1200);
            eq(3800L, CivilizationService.view(s, A).treasury(), "after a real transfer");
            eq(null, CivilizationService.view(s, B), "no view for someone without a civilization");
        });

        System.out.println("Wire format and client authority");

        test("create request round-trips, including spaces and unicode", () -> {
            String text = CivWire.encodeCreate("Zürich Union", "東京 Taro", "green", "tribal_council");
            CivWire.CreateRequest r = CivWire.decodeCreate(text);
            check(r != null, "decoded");
            eq("Zürich Union", r.name(), "name");
            eq("東京 Taro", r.leader(), "leader");
            eq("green", r.colorId(), "color");
            eq("tribal_council", r.governmentId(), "government");
        });

        test("a name containing separators cannot smuggle in extra fields", () -> {
            String text = CivWire.encodeCreate("Evil\tred\tmonarchy\t999999", "Leader One", "blue", "republic");
            CivWire.CreateRequest r = CivWire.decodeCreate(text);
            check(r != null, "still exactly four fields");
            eq("Evil\tred\tmonarchy\t999999", r.name(), "the text stayed inside the name");
            eq(CivError.NAME_INVALID_CHARS, CivilizationService.create(new CoreState(), new RonSettings(), A,
                    r.name(), r.leader(), r.colorId(), r.governmentId(), 0).error(), "and the name is then refused");
        });

        test("malformed or oversized requests are rejected", () -> {
            check(CivWire.decodeCreate(null) == null, "null");
            check(CivWire.decodeCreate("") == null, "empty");
            check(CivWire.decodeCreate("rename\tA\tB\tred\tmonarchy") == null, "wrong tag");
            check(CivWire.decodeCreate("create\tA\tB\tred") == null, "too few fields");
            check(CivWire.decodeCreate("create\tAAA\tBBB\tred\tmonarchy\textra") == null, "too many fields");
            check(CivWire.decodeCreate("create\t%zz\tBBB\tred\tmonarchy") == null, "bad percent-encoding");
            check(CivWire.decodeCreate("create\tAAA\tBBB\tRED\tmonarchy") == null, "color must be a lowercase id");
            check(CivWire.decodeCreate("create\tAAA\tBBB\tred\tmon archy") == null, "government must be an id");
            check(CivWire.decodeCreate("create\t" + "A".repeat(5000) + "\tBBB\tred\tmonarchy") == null, "oversized");
            check(CivWire.decodeRename("rename") == null, "rename without name");
            check(CivWire.decodeRename("rename\tA\tB") == null, "rename with extra field");
        });

        test("requests have no field for treasury, population, owner or any server value", () -> {
            List<String> create = Arrays.stream(CivWire.CreateRequest.class.getRecordComponents()).map(c -> c.getName()).toList();
            eq(List.of("name", "leader", "colorId", "governmentId"), create, "create fields");
            List<String> rename = Arrays.stream(CivWire.RenameRequest.class.getRecordComponents()).map(c -> c.getName()).toList();
            eq(List.of("name"), rename, "rename fields");
            check(CivWire.decodeCreate("create\tAAA\tBBB\tred\tmonarchy\ttreasury=999999") == null, "extra field refused");
        });

        test("state messages round-trip with and without a civilization", () -> {
            CoreState s = new CoreState();
            create(s, new RonSettings(), A, "Zürich Union");
            CivView v = CivilizationService.view(s, A);
            CivWire.StateMessage m = CivWire.decodeState(CivWire.encodeState(new CivWire.StateMessage(true, CivError.NONE, v)));
            check(m != null && m.open() && m.error() == CivError.NONE, "header");
            eq(v, m.view(), "view identical");
            CivWire.StateMessage none = CivWire.decodeState(CivWire.encodeState(new CivWire.StateMessage(false, CivError.NAME_TAKEN, null)));
            check(none != null && !none.open() && none.view() == null, "no view");
            eq(CivError.NAME_TAKEN, none.error(), "error code survives");
        });

        test("garbage state messages from a server are ignored, not trusted", () -> {
            check(CivWire.decodeState(null) == null, "null");
            check(CivWire.decodeState("hello") == null, "garbage");
            check(CivWire.decodeState("state\t2\tOK\t0") == null, "bad open flag");
            check(CivWire.decodeState("state\t1\tOK\t1\tA\tB") == null, "truncated");
            check(CivWire.decodeState("state\t1\tOK\t1\tAAA\tBBB\tred\tmonarchy\t-5\t100\t1\tsettled\tstable\t0") == null, "negative treasury");
            check(CivWire.decodeState("state\t1\tOK\t1\tAAA\tBBB\tred\tmonarchy\tabc\t100\t1\tsettled\tstable\t0") == null, "non-number");
            eq(CivError.INVALID_REQUEST, CivError.fromCode("NOT_A_REAL_CODE"), "unknown code maps to a safe error");
        });

        System.out.println("Request throttle");

        test("requests are spaced out per player", () -> {
            RequestThrottle t = new RequestThrottle();
            check(t.allow(A, 100, 10), "first");
            check(!t.allow(A, 105, 10), "too soon");
            check(t.allow(B, 105, 10), "other player unaffected");
            check(t.allow(A, 110, 10), "after interval");
            check(t.allow(A, 5, 10), "tick counter reset is not a lockout");
        });

        test("the throttle map stays bounded", () -> {
            RequestThrottle t = new RequestThrottle();
            for (int i = 0; i < 10_000; i++) check(t.allow("p" + i, i, 1), "request " + i);
        });
    }
}
