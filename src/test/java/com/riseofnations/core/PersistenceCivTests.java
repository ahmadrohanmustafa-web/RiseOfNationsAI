package com.riseofnations.core;

import com.riseofnations.core.civ.CivColor;
import com.riseofnations.core.civ.Civilization;
import com.riseofnations.core.civ.DevelopmentStage;
import com.riseofnations.core.civ.EconomicStatus;
import com.riseofnations.core.civ.Government;
import com.riseofnations.core.config.RonSettings;
import com.riseofnations.core.persist.CoreState;
import com.riseofnations.core.persist.StateCodec;

import static com.riseofnations.core.CivilizationTests.A;
import static com.riseofnations.core.CivilizationTests.B;
import static com.riseofnations.core.TestKit.check;
import static com.riseofnations.core.TestKit.eq;
import static com.riseofnations.core.TestKit.test;

final class PersistenceCivTests {

    private PersistenceCivTests() { }

    private static String civLine(String owner, String name, String leader, String color, String gov,
                                  String pop, String settlements, String stage, String economy, String created) {
        return "civ\t" + owner + "\t" + name + "\t" + leader + "\t" + color + "\t" + gov + "\t" + pop + "\t"
                + settlements + "\t" + stage + "\t" + economy + "\t" + created + "\n";
    }

    static void run() {
        System.out.println("Civilization persistence");

        test("save and load round-trip keeps civilizations, treasury and unicode names", () -> {
            CoreState s = new CoreState();
            RonSettings cfg = new RonSettings();
            CivilizationTests.create(s, cfg, A, "Zürich Union");
            CivilizationTests.create(s, cfg, B, "東京都 League");
            s.ledger().open("player:x");
            s.ledger().transfer("civ:" + A, "player:x", 1500);

            String text = StateCodec.encode(s);
            StateCodec.Decoded d = StateCodec.decode(text);
            check(!d.refused(), "not refused");
            eq(0, d.warnings().size(), "no warnings: " + d.warnings());
            eq(text, StateCodec.encode(d.state()), "re-encoding gives identical text");
            Civilization a = d.state().civilization(A);
            eq("Zürich Union", a.name(), "unicode name");
            eq("東京都 League", d.state().civilization(B).name(), "other script");
            eq(100L, a.population(), "population");
            eq(1, a.settlements(), "settlements");
            eq(3500L, d.state().ledger().balance("civ:" + A), "treasury after the earlier transfer");
            eq(s.civilization(A), a, "whole record equal");
        });

        test("a version 1 save (before civilizations existed) still loads and is upgraded on the next save", () -> {
            String v1 = "ron-state\t1\nminted\t100\nburned\t0\nbal\tplayer:a\t100\n";
            StateCodec.Decoded d = StateCodec.decode(v1);
            check(!d.refused(), "accepted");
            eq(100L, d.state().ledger().balance("player:a"), "balance kept");
            check(d.state().civilizationsView().isEmpty(), "no civilizations yet");
            check(StateCodec.encode(d.state()).startsWith("ron-state\t3\n"), "re-saved as the current version (3)");
        });

        test("a save from a newer schema is refused", () -> {
            check(StateCodec.decode("ron-state\t4\nciv\tx\n").refused(), "version 4 refused");
        });

        test("a brand-new world starts with no civilizations", () -> {
            StateCodec.Decoded d = StateCodec.decode("");
            check(!d.refused() && d.state().civilizationsView().isEmpty(), "fresh");
        });

        test("damaged civilization fields get safe defaults and the civilization is kept", () -> {
            String text = "ron-state\t2\nminted\t10\nburned\t0\nbal\tciv:" + A + "\t10\n"
                    + civLine(A, "%E2%82%AC%00bad", "%00", "pink", "empire", "abc", "-5", "future_stage", "future_economy", "xyz");
            StateCodec.Decoded d = StateCodec.decode(text);
            check(!d.refused(), "not refused");
            Civilization c = d.state().civilization(A);
            check(c != null, "kept");
            eq("Unnamed Civilization", c.name(), "default name");
            eq("Unknown Leader", c.leaderName(), "default leader");
            eq(CivColor.values()[0], c.color(), "default color");
            eq(Government.values()[0], c.government(), "default government");
            eq(100L, c.population(), "default population");
            eq(1, c.settlements(), "default settlements");
            eq(DevelopmentStage.SETTLED, c.stage(), "default stage");
            eq(EconomicStatus.STABLE, c.economy(), "default economy");
            check(d.warnings().size() >= 6, "each repair is reported: " + d.warnings());
        });

        test("civilization lines with the wrong shape are skipped, not guessed", () -> {
            String text = "ron-state\t2\nciv\t" + A + "\tonly\ttwo\n";
            StateCodec.Decoded d = StateCodec.decode(text);
            check(d.state().civilizationsView().isEmpty(), "skipped");
            check(!d.warnings().isEmpty(), "reported");
            String badOwner = "ron-state\t2\n" + civLine("bad owner", "Rome", "Leader One", "red", "republic", "100", "1", "settled", "stable", "0");
            check(StateCodec.decode(badOwner).state().civilizationsView().isEmpty(), "unsafe owner id skipped");
        });

        test("two civilizations for one player: the first wins and the loading is not duplicated", () -> {
            String text = "ron-state\t2\n"
                    + civLine(A, "First", "Leader One", "red", "republic", "100", "1", "settled", "stable", "0")
                    + civLine(A, "Second", "Leader Two", "blue", "monarchy", "100", "1", "settled", "stable", "0");
            StateCodec.Decoded d = StateCodec.decode(text);
            eq(1, d.state().civilizationsView().size(), "one civilization");
            check(!d.warnings().isEmpty(), "duplicate reported");
        });

        test("a civilization whose treasury account is missing gets an empty account (no free money)", () -> {
            String text = "ron-state\t2\n" + civLine(A, "Rome", "Leader One", "red", "republic", "100", "1", "settled", "stable", "0");
            StateCodec.Decoded d = StateCodec.decode(text);
            check(d.state().ledger().exists("civ:" + A), "account opened");
            eq(0L, d.state().ledger().balance("civ:" + A), "empty");
            eq(0L, d.state().ledger().totalMinted(), "nothing minted");
        });

        test("a hostile name cannot forge extra save records", () -> {
            CoreState s = new CoreState();
            Civilization evil = new Civilization(A, "Rome\nbal\tplayer:thief\t999999\nciv\t" + B, "Leader One",
                    CivColor.RED, Government.REPUBLIC, 100, 1, DevelopmentStage.SETTLED, EconomicStatus.STABLE, 0);
            s.putCivilization(evil);
            s.ledger().open("civ:" + A);
            StateCodec.Decoded d = StateCodec.decode(StateCodec.encode(s));
            check(!d.state().ledger().exists("player:thief"), "no forged account");
            eq(1, d.state().civilizationsView().size(), "no forged civilization");
            eq(0L, d.state().ledger().totalSupply(), "no forged money");
        });
    }
}
