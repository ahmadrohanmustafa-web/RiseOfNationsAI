package com.riseofnations.core;

import com.riseofnations.core.config.RonSettings;
import com.riseofnations.core.persist.CoreState;
import com.riseofnations.core.persist.StateCodec;
import com.riseofnations.core.settlement.Settlement;
import com.riseofnations.core.settlement.SettlementService;

import static com.riseofnations.core.CivilizationTests.A;
import static com.riseofnations.core.CivilizationTests.B;
import static com.riseofnations.core.SettlementTests.OVERWORLD;
import static com.riseofnations.core.TestKit.check;
import static com.riseofnations.core.TestKit.eq;
import static com.riseofnations.core.TestKit.test;

final class PersistenceSettlementTests {

    private PersistenceSettlementTests() { }

    private static final String CIV_A = "civ\t" + A + "\tRome\tAldric+Stone\tblue\tmonarchy\t100\t1\tsettled\tstable\t0\n";

    private static String settle(String id, String owner, String name, String type, String dim, String x, String y,
                                 String z, String level, String created, String mx, String my, String mz, String block) {
        return "settle\t" + id + "\t" + owner + "\t" + name + "\t" + type + "\t" + dim + "\t" + x + "\t" + y + "\t" + z
                + "\t" + level + "\t" + created + "\t" + mx + "\t" + my + "\t" + mz + "\t" + block + "\n";
    }

    private static String goodSettle(String id) {
        return settle(id, A, "Rome+Capital", "capital", OVERWORLD, "100", "64", "-200", "1", "1700000000000",
                "101", "64", "-200", "minecraft:chiseled_stone_bricks");
    }

    static void run() {
        System.out.println("Settlement persistence");

        test("7. a capital survives a save and load, and re-saving gives identical text", () -> {
            CoreState s = new CoreState();
            CivilizationTests.create(s, new RonSettings(), A, "Zürich Union");
            SettlementTests.found(s, A, "s-1");
            String text = StateCodec.encode(s);
            check(text.startsWith("ron-state\t3\n"), "written as version 3");
            StateCodec.Decoded d = StateCodec.decode(text);
            check(!d.refused(), "not refused");
            eq(0, d.warnings().size(), "no warnings: " + d.warnings());
            eq(text, StateCodec.encode(d.state()), "identical text");
            Settlement c = SettlementService.capitalOf(d.state(), A);
            check(c != null, "capital present after reload");
            eq(s.settlement("s-1"), c, "every field equal");
            eq("Zürich Union Capital", c.name(), "unicode name");
            eq(1, d.state().settlementsView().size(), "exactly one settlement (no duplication)");
        });

        test("loading and saving again and again never duplicates a capital", () -> {
            CoreState s = SettlementTests.worldWithRome();
            SettlementTests.found(s, A, "s-1");
            String text = StateCodec.encode(s);
            for (int i = 0; i < 5; i++) {
                StateCodec.Decoded d = StateCodec.decode(text);
                text = StateCodec.encode(d.state());
                eq(1, d.state().settlementsView().size(), "round " + i);
            }
        });

        test("8. older saves without settlements still load (schema 1 and 2) and are not wiped", () -> {
            String v2 = "ron-state\t2\nminted\t5000\nburned\t0\nbal\tciv:" + A + "\t5000\n" + CIV_A;
            StateCodec.Decoded d = StateCodec.decode(v2);
            check(!d.refused(), "schema 2 accepted");
            eq(0, d.warnings().size(), "clean: " + d.warnings());
            eq("Rome", d.state().civilization(A).name(), "civilization kept");
            eq(5000L, d.state().ledger().balance("civ:" + A), "treasury kept");
            check(d.state().settlementsView().isEmpty(), "no settlements, none invented");
            check(StateCodec.encode(d.state()).startsWith("ron-state\t3\n"), "upgraded on the next save");
            check(!StateCodec.decode("ron-state\t1\nminted\t1\nburned\t0\nbal\tplayer:a\t1\n").refused(), "schema 1 accepted");
        });

        test("a capital can be founded in a world loaded from an old save", () -> {
            String v2 = "ron-state\t2\nminted\t5000\nburned\t0\nbal\tciv:" + A + "\t5000\n" + CIV_A;
            CoreState s = StateCodec.decode(v2).state();
            check(SettlementTests.found(s, A, "s-1").ok(), "founded");
            check(StateCodec.decode(StateCodec.encode(s)).state().settlement("s-1") != null, "and it persists");
        });

        test("saves from a newer schema are still refused", () -> {
            check(StateCodec.decode("ron-state\t4\n" + CIV_A + goodSettle("s-1")).refused(), "version 4 refused");
            check(StateCodec.decode("ron-state\t99\n").refused(), "version 99 refused");
        });

        test("a well-formed hand-written settlement record loads", () -> {
            StateCodec.Decoded d = StateCodec.decode("ron-state\t3\nminted\t0\nburned\t0\n" + CIV_A + goodSettle("s-1"));
            eq(0L, d.warnings().stream().filter(w -> w.contains("settle")).count(), "no settlement complaints: " + d.warnings());
            Settlement c = d.state().settlement("s-1");
            check(c != null, "loaded");
            eq(-200, c.z(), "z");
            eq("Rome Capital", c.name(), "plus-encoded space decoded");
        });

        test("9. malformed settlement records are skipped with a warning and never crash loading", () -> {
            String head = "ron-state\t3\nminted\t0\nburned\t0\n" + CIV_A;
            String[] bad = {
                    "settle\ts-1\tonly\ttoo\tfew\n",
                    settle("s-1", "bad owner", "N", "capital", OVERWORLD, "1", "1", "1", "1", "0", "1", "1", "1", "minecraft:stone"),
                    settle("Bad Id", A, "N", "capital", OVERWORLD, "1", "1", "1", "1", "0", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "metropolis", OVERWORLD, "1", "1", "1", "1", "0", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "capital", "NOT A DIMENSION", "1", "1", "1", "1", "0", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "capital", OVERWORLD, "abc", "1", "1", "1", "0", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "capital", OVERWORLD, "99999999", "1", "1", "1", "0", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "capital", OVERWORLD, "1", "999999", "1", "1", "0", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "capital", OVERWORLD, "1", "1", "1", "0", "0", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "capital", OVERWORLD, "1", "1", "1", "-5", "0", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "capital", OVERWORLD, "1", "1", "1", "1", "-1", "1", "1", "1", "minecraft:stone"),
                    settle("s-1", A, "N", "capital", OVERWORLD, "1", "1", "1", "1", "0", "1", "1", "1", "Not A Block"),
                    settle("s-1", A, "N", "capital", OVERWORLD, "1", "1", "1", "1", "0", "1", "99999", "1", "minecraft:stone"),
            };
            for (int i = 0; i < bad.length; i++) {
                StateCodec.Decoded d = StateCodec.decode(head + bad[i]);
                check(!d.refused(), "record " + i + ": file is still loaded");
                check(d.state().settlementsView().isEmpty(), "record " + i + " skipped: " + bad[i].trim());
                check(!d.warnings().isEmpty(), "record " + i + " reported");
                eq("Rome", d.state().civilization(A).name(), "record " + i + ": civilization untouched");
            }
        });

        test("a settlement whose owner has no civilization is skipped", () -> {
            String text = "ron-state\t3\n" + CIV_A + settle("s-9", B, "Ghost+Capital", "capital", OVERWORLD,
                    "1", "1", "1", "1", "0", "1", "1", "1", "minecraft:stone");
            StateCodec.Decoded d = StateCodec.decode(text);
            check(d.state().settlementsView().isEmpty(), "skipped");
            check(d.warnings().stream().anyMatch(w -> w.contains("no civilization")), "explained: " + d.warnings());
        });

        test("two capitals for one owner in a file: the first is kept, the second is skipped", () -> {
            String text = "ron-state\t3\n" + CIV_A + goodSettle("s-1") + settle("s-2", A, "Other", "capital", OVERWORLD,
                    "5", "5", "5", "1", "0", "6", "5", "5", "minecraft:stone");
            StateCodec.Decoded d = StateCodec.decode(text);
            eq(1, d.state().settlementsView().size(), "one settlement");
            check(d.state().settlement("s-1") != null && d.state().settlement("s-2") == null, "first wins");
            check(d.warnings().stream().anyMatch(w -> w.contains("already has a capital")), "reported");
        });

        test("a duplicated settlement id in a file is loaded only once", () -> {
            String text = "ron-state\t3\n" + CIV_A + goodSettle("s-1") + goodSettle("s-1");
            eq(1, StateCodec.decode(text).state().settlementsView().size(), "one");
        });

        test("an unusable stored name is replaced by the default, the rest of the record is kept", () -> {
            String text = "ron-state\t3\n" + CIV_A + settle("s-1", A, "%00%0A", "capital", OVERWORLD, "7", "8", "9",
                    "2", "0", "8", "8", "9", "minecraft:chiseled_stone_bricks");
            StateCodec.Decoded d = StateCodec.decode(text);
            Settlement c = d.state().settlement("s-1");
            check(c != null, "kept");
            eq("Rome Capital", c.name(), "default name");
            eq(7, c.x(), "coordinates kept");
            eq(2, c.level(), "level kept");
        });

        test("a hostile civilization name cannot forge settlement records in the save text", () -> {
            CoreState s = new CoreState();
            CivilizationTests.create(s, new RonSettings(), A, "Rome");
            SettlementTests.found(s, A, "s-1");
            // bypass the validators on purpose, as a corrupted or hostile in-memory record might
            Settlement evil = s.settlement("s-1").withName("X\nsettle\tforged\t" + B + "\tN\tcapital\t" + OVERWORLD + "\t0\t0\t0\t1\t0\t0\t0\t0\tminecraft:stone");
            s.putSettlement(evil);
            StateCodec.Decoded d = StateCodec.decode(StateCodec.encode(s));
            eq(1, d.state().settlementsView().size(), "no forged settlement");
            check(d.state().settlement("forged") == null, "forged id absent");
            eq("Rome Capital", d.state().settlement("s-1").name(), "the unusable name was replaced");
        });
    }
}
