package com.riseofnations.core;

import com.riseofnations.core.config.RonSettings;
import com.riseofnations.core.economy.Ledger;
import com.riseofnations.core.persist.CoreState;
import com.riseofnations.core.persist.StateCodec;
import com.riseofnations.core.story.GameEvent;
import com.riseofnations.core.story.PlayerStory;
import com.riseofnations.core.story.QuestEngine;

import static com.riseofnations.core.TestKit.check;
import static com.riseofnations.core.TestKit.eq;
import static com.riseofnations.core.TestKit.test;

final class PersistenceTests {

    private PersistenceTests() { }

    private static CoreState sampleState(StoryTests.World w) {
        CoreState cs = new CoreState(w.ledger, new com.riseofnations.core.economy.WageTracker());
        PlayerStory dst = cs.story(StoryTests.P);
        // copy the played story into the state through the encoder/decoder-free restore API
        w.story.statusView().forEach(dst::restoreStatus);
        w.story.progressView().forEach(dst::restoreProgress);
        w.story.flagsView().forEach(dst::restoreFlag);
        w.story.rewardsPaidView().forEach(dst::restorePaid);
        w.story.unlocksView().forEach(dst::restoreUnlock);
        w.story.seenKeysView().forEach(dst::restoreSeen);
        dst.restoreStarted(true);
        dst.restoreReputation(w.story.reputation());
        cs.wages().restoreLastPaid("p1|farmer", 1234);
        cs.wages().restoreDaily("p1", 2, 77);
        return cs;
    }

    static void run() {
        System.out.println("Persistence");

        test("save then load gives identical data (text round trip is stable)", () -> {
            StoryTests.World w = StoryTests.playThrough(true);
            CoreState cs = sampleState(w);
            String text = StateCodec.encode(cs);
            StateCodec.Decoded d = StateCodec.decode(text);
            check(!d.refused(), "not refused");
            eq(0, d.warnings().size(), "no warnings: " + d.warnings());
            eq(text, StateCodec.encode(d.state()), "re-encoded text identical");
            eq(560L, d.state().ledger().balance("player:p1"), "coins survived");
            eq(PlayerStory.Status.COMPLETED, d.state().story("p1").statusOf("q3_farmers"), "quest status survived");
            check(d.state().story("p1").hasFlag("dispute_farmers"), "decision survived");
            eq(14, d.state().story("p1").reputation(), "reputation survived");
            eq(1234L, d.state().wages().lastPaidView().get("p1|farmer"), "wage cooldown survived");
        });

        test("after a restart no reward is paid twice", () -> {
            StoryTests.World w = StoryTests.playThrough(true);
            StateCodec.Decoded d = StateCodec.decode(StateCodec.encode(sampleState(w)));
            Ledger l = d.state().ledger();
            PlayerStory s = d.state().story("p1");
            // prove the story really came back from disk, otherwise this test would pass for the wrong reason
            eq(PlayerStory.Status.COMPLETED, s.statusOf("q1_harvest"), "story restored");
            check(s.rewardPaid("q1_harvest"), "paid-marker restored");
            QuestEngine engine = new QuestEngine(com.riseofnations.core.story.DefaultStory.create(),
                    (player, quest, coins) -> l.transfer("settlement:s1", "player:" + player, coins) == Ledger.Result.OK);
            long before = l.balance("player:p1");
            engine.start("p1", s);
            engine.onEvent("p1", s, GameEvent.of("npc_talked", "steward", 1));
            engine.onEvent("p1", s, GameEvent.of("item_delivered", "minecraft:wheat", 100));
            engine.retryUnpaidRewards("p1", s);
            eq(before, l.balance("player:p1"), "balance unchanged after reload + replay");
            for (int i = 0; i < 5; i++) engine.onEvent("p1", s, new GameEvent("contract_completed", "c", 1, "contract-" + i));
            eq(before, l.balance("player:p1"), "old contract ids still remembered");
        });

        test("empty or missing save data is a clean new world", () -> {
            StateCodec.Decoded d = StateCodec.decode("");
            check(!d.refused(), "not refused");
            eq(0, d.warnings().size(), "no warnings");
            check(StateCodec.decode(null).state().storiesView().isEmpty(), "null ok");
        });

        test("data from a newer version is refused so it cannot be overwritten", () -> {
            StateCodec.Decoded d = StateCodec.decode("ron-state\t99\nbal\tplayer:a\t5\n");
            check(d.refused(), "refused");
            check(!d.warnings().isEmpty(), "explained");
        });

        test("garbage with no valid header is refused", () -> {
            check(StateCodec.decode("hello world").refused(), "no header");
            check(StateCodec.decode("ron-state\tabc\n").refused(), "bad version");
            check(StateCodec.decode("ron-state\t0\n").refused(), "version zero");
        });

        test("bad lines are skipped, good lines still load, negative balances are dropped", () -> {
            String text = "ron-state\t1\n"
                    + "bal\tplayer:a\t100\n"
                    + "bal\tplayer:b\tabc\n"
                    + "bal\tplayer:cheat\t-500\n"
                    + "bal\tBAD ID\t5\n"
                    + "wat\tx\n"
                    + "st\tp1\tq1_steward\tNOT_A_STATUS\n"
                    + "flag\tBAD PLAYER\tf\n"
                    + "st\tp1\tq1_steward\tCOMPLETED\n"
                    + "pg\tp1\tq1_harvest\n"
                    + "rep\tp1\t7\n";
            StateCodec.Decoded d = StateCodec.decode(text);
            check(!d.refused(), "not refused");
            eq(100L, d.state().ledger().balance("player:a"), "good balance kept");
            check(!d.state().ledger().exists("player:cheat"), "negative dropped");
            check(!d.state().ledger().exists("player:b"), "unparseable dropped");
            eq(PlayerStory.Status.COMPLETED, d.state().story("p1").statusOf("q1_steward"), "good story line kept");
            eq(7, d.state().story("p1").reputation(), "reputation");
            check(d.warnings().size() >= 5, "warnings reported: " + d.warnings());
            eq(d.state().ledger().totalMinted() - d.state().ledger().totalBurned(), d.state().ledger().totalSupply(), "invariant");
        });

        test("hand-edited saves cannot inject extra fields or fake the supply", () -> {
            String text = "ron-state\t1\nminted\t1000000\nbal\tplayer:a\t10\nbal\tplayer:a\t10\textra\n";
            StateCodec.Decoded d = StateCodec.decode(text);
            eq(10L, d.state().ledger().totalMinted(), "minted reconciled to real supply");
            check(!d.warnings().isEmpty(), "reported");
        });

        test("unsafe player ids never create a story", () -> {
            CoreState cs = new CoreState();
            eq(null, cs.story("bad id"), "space");
            eq(null, cs.story(null), "null");
            check(cs.story("player-1") != null, "good id");
        });

        System.out.println("Settings");

        test("default settings are already valid", () -> {
            eq(0, new RonSettings().sanitize().size(), "no clamping needed");
        });

        test("out-of-range settings are clamped with a note each", () -> {
            RonSettings s = new RonSettings();
            s.wageCooldownTicks = 0;
            s.dailyWageCap = -5;
            s.marketTaxBasisPoints = 99_999;
            s.maxNations = 100_000;
            s.maxActiveNpcs = -1;
            var notes = s.sanitize();
            eq(5, notes.size(), "five changes");
            eq(20L, s.wageCooldownTicks, "cooldown");
            eq(1L, s.dailyWageCap, "cap");
            eq(5000, s.marketTaxBasisPoints, "tax");
            eq(100, s.maxNations, "nations");
            eq(0, s.maxActiveNpcs, "npcs");
        });
    }
}
