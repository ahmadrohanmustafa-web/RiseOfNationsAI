package com.riseofnations.core;

import com.riseofnations.core.economy.Ledger;
import com.riseofnations.core.story.Chapter;
import com.riseofnations.core.story.DefaultStory;
import com.riseofnations.core.story.GameEvent;
import com.riseofnations.core.story.Objective;
import com.riseofnations.core.story.PlayerStory;
import com.riseofnations.core.story.Quest;
import com.riseofnations.core.story.QuestEngine;
import com.riseofnations.core.story.QuestEngine.UpdateKind;
import com.riseofnations.core.story.Reward;
import com.riseofnations.core.story.StoryDefinition;

import java.util.List;
import java.util.Set;

import static com.riseofnations.core.TestKit.check;
import static com.riseofnations.core.TestKit.eq;
import static com.riseofnations.core.TestKit.test;

final class StoryTests {

    private StoryTests() { }

    static final String P = "p1";

    /** A world with a funded settlement treasury and an engine that pays rewards from it. */
    static final class World {
        final Ledger ledger = new Ledger();
        final QuestEngine engine;
        final PlayerStory story = new PlayerStory();

        World(long treasury) {
            ledger.open("settlement:s1");
            ledger.open("player:" + P);
            if (treasury > 0) ledger.mint("settlement:s1", treasury);
            engine = new QuestEngine(DefaultStory.create(),
                    (player, quest, coins) -> ledger.transfer("settlement:s1", "player:" + player, coins) == Ledger.Result.OK);
        }

        List<QuestEngine.StoryUpdate> ev(String type, String subject, long amount) {
            return engine.onEvent(P, story, GameEvent.of(type, subject, amount));
        }

        long coins() { return ledger.balance("player:" + P); }
    }

    private static boolean has(List<QuestEngine.StoryUpdate> ups, UpdateKind kind, String quest) {
        for (var u : ups) if (u.kind() == kind && u.questId().equals(quest)) return true;
        return false;
    }

    /** Plays chapters 1-3 using only game events. Returns the world afterwards. */
    static World playThrough(boolean farmersSide) {
        World w = new World(5000);
        w.engine.start(P, w.story);
        w.ev("npc_talked", "steward", 1);
        w.ev("item_delivered", "minecraft:wheat", 10);
        w.ev("item_delivered", "minecraft:wheat", 6);
        w.ev("item_delivered", "minecraft:oak_log", 20);
        w.ev("wage_earned", "farmer", 100);
        w.ev("wage_earned", "miner", 50);
        w.ev("property_acquired", "cottage", 1);
        w.ev("npc_talked", "merchant", 1);
        w.ev("trade_completed", "grain", 1);
        for (int i = 0; i < 5; i++) {
            w.engine.onEvent(P, w.story, new GameEvent("contract_completed", "farm", 1, "contract-" + i));
        }
        var out = new java.util.ArrayList<QuestEngine.StoryUpdate>();
        w.engine.choose(P, w.story, "q3_dispute", "side", farmersSide ? "farmers" : "merchants", out);
        if (farmersSide) {
            w.ev("item_delivered", "minecraft:wheat", 32);
        } else {
            w.ev("trade_completed", "grain", 3);
        }
        return w;
    }

    static void run() {
        System.out.println("Story");

        test("starting the campaign activates only the first quest, once", () -> {
            World w = new World(1000);
            var first = w.engine.start(P, w.story);
            check(has(first, UpdateKind.QUEST_STARTED, "q1_steward"), "steward started");
            eq(null, w.story.statusOf("q1_harvest"), "later quest not started");
            eq(0, w.engine.start(P, w.story).size(), "second start does nothing");
        });

        test("events before the campaign starts are ignored", () -> {
            World w = new World(1000);
            eq(0, w.ev("npc_talked", "steward", 1).size(), "ignored");
        });

        test("the story advances by itself from real events (chapters 1-3, no commands)", () -> {
            World w = playThrough(true);
            for (Quest q : w.engine.story().quests()) {
                if (q.id().equals("q3_merchants")) continue;
                eq(PlayerStory.Status.COMPLETED, w.story.statusOf(q.id()), q.id());
            }
            check(w.story.hasFlag("chapter_done:ch1"), "chapter 1 done");
            check(w.story.hasFlag("chapter_done:ch2"), "chapter 2 done");
            check(w.story.hasFlag("chapter_done:ch3"), "chapter 3 done (other branch excluded)");
            check(w.story.hasUnlock("market_access"), "unlock granted");
            check(w.story.hasUnlock("home_storage"), "unlock granted");
            eq(14, w.story.reputation(), "reputation total");
        });

        test("rewards come from the treasury and add up exactly", () -> {
            World w = playThrough(true);
            eq(560L, w.coins(), "player coins");
            eq(5000L - 560L, w.ledger.balance("settlement:s1"), "treasury");
            eq(w.ledger.totalMinted() - w.ledger.totalBurned(), w.ledger.totalSupply(), "no money created");
        });

        test("branching: the other choice leads to a different path", () -> {
            World w = playThrough(false);
            eq(PlayerStory.Status.COMPLETED, w.story.statusOf("q3_merchants"), "merchants path done");
            eq(null, w.story.statusOf("q3_farmers"), "farmers path never offered");
            check(w.story.hasFlag("backed_merchants"), "decision remembered");
            check(!w.story.hasFlag("backed_farmers"), "other decision absent");
        });

        test("a decision cannot be changed afterwards", () -> {
            World w = playThrough(true);
            var out = new java.util.ArrayList<QuestEngine.StoryUpdate>();
            eq(QuestEngine.ChoiceResult.QUEST_NOT_ACTIVE, w.engine.choose(P, w.story, "q3_dispute", "side", "merchants", out), "re-choose");
            check(!w.story.hasFlag("dispute_merchants"), "flag not set");
        });

        test("invalid choices are rejected without effect", () -> {
            World w = new World(1000);
            w.engine.start(P, w.story);
            var out = new java.util.ArrayList<QuestEngine.StoryUpdate>();
            eq(QuestEngine.ChoiceResult.QUEST_NOT_ACTIVE, w.engine.choose(P, w.story, "q3_dispute", "side", "farmers", out), "not active yet");
            eq(QuestEngine.ChoiceResult.QUEST_NOT_ACTIVE, w.engine.choose(P, w.story, "no_such_quest", "x", "y", out), "unknown quest");
            eq(0, out.size(), "no updates");
            PlayerStory s2 = playThrough(true).story;   // reach a state where the dispute exists, on a fresh world
            check(s2 != null, "sanity");
        });

        test("choice objectives cannot be completed by sending game events", () -> {
            World w = new World(1000);
            w.engine.start(P, w.story);
            w.ev("npc_talked", "steward", 1);
            w.ev("item_delivered", "minecraft:wheat", 16);
            w.ev("item_delivered", "minecraft:oak_log", 20);
            w.ev("wage_earned", "farmer", 150);
            w.ev("property_acquired", "cottage", 1);
            w.ev("npc_talked", "merchant", 1);
            w.ev("trade_completed", "x", 1);
            for (int i = 0; i < 5; i++) w.engine.onEvent(P, w.story, new GameEvent("contract_completed", "c", 1, "k" + i));
            eq(PlayerStory.Status.ACTIVE, w.story.statusOf("q3_dispute"), "waiting for a decision");
            w.ev("choice", "side", 1);
            eq(PlayerStory.Status.ACTIVE, w.story.statusOf("q3_dispute"), "still waiting");
        });

        test("no duplicate rewards when the same events are replayed", () -> {
            World w = playThrough(true);
            long before = w.coins();
            for (int round = 0; round < 3; round++) {
                w.ev("npc_talked", "steward", 1);
                w.ev("item_delivered", "minecraft:wheat", 500);
                w.ev("item_delivered", "minecraft:oak_log", 500);
                w.ev("wage_earned", "farmer", 5000);
                w.ev("trade_completed", "grain", 50);
            }
            eq(before, w.coins(), "coins unchanged");
            eq(14, w.story.reputation(), "reputation unchanged");
        });

        test("the same contract id only ever counts once", () -> {
            World w = new World(1000);
            w.engine.start(P, w.story);
            w.ev("npc_talked", "steward", 1);
            w.ev("item_delivered", "minecraft:wheat", 16);
            w.ev("item_delivered", "minecraft:oak_log", 20);
            w.ev("wage_earned", "farmer", 150);
            w.ev("property_acquired", "cottage", 1);
            w.ev("npc_talked", "merchant", 1);
            w.ev("trade_completed", "x", 1);
            for (int i = 0; i < 20; i++) {
                w.engine.onEvent(P, w.story, new GameEvent("contract_completed", "c", 1, "same-contract"));
            }
            eq(1L, w.story.progressOf("q3_contracts", "contracts"), "counted once");
        });

        test("progress is capped at the target", () -> {
            World w = new World(1000);
            w.engine.start(P, w.story);
            w.ev("npc_talked", "steward", 1);
            w.ev("item_delivered", "minecraft:wheat", 1_000_000);
            eq(16L, w.story.progressOf("q1_harvest", "wheat"), "capped");
        });

        test("a quest that is not active yet gets no progress", () -> {
            World w = new World(1000);
            w.engine.start(P, w.story);
            w.ev("item_delivered", "minecraft:wheat", 16);   // steward not met yet
            eq(null, w.story.statusOf("q1_harvest"), "not active");
            eq(0L, w.story.progressOf("q1_harvest", "wheat"), "no progress stored");
        });

        test("wrong item does not count toward an objective", () -> {
            World w = new World(1000);
            w.engine.start(P, w.story);
            w.ev("npc_talked", "steward", 1);
            w.ev("item_delivered", "minecraft:carrot", 64);
            eq(0L, w.story.progressOf("q1_harvest", "wheat"), "carrots are not wheat");
        });

        test("invalid events (bad ids, zero, negative, huge) are ignored", () -> {
            World w = new World(1000);
            w.engine.start(P, w.story);
            w.ev("npc_talked", "steward", 1);
            eq(0, w.ev("item_delivered", "minecraft:wheat", 0).size(), "zero");
            eq(0, w.ev("item_delivered", "minecraft:wheat", -10).size(), "negative");
            eq(0, w.ev("item_delivered", "minecraft:wheat", Long.MAX_VALUE).size(), "huge");
            eq(0, w.ev("Item Delivered", "minecraft:wheat", 1).size(), "bad type");
            eq(0, w.engine.onEvent(P, w.story, null).size(), "null");
            eq(0L, w.story.progressOf("q1_harvest", "wheat"), "nothing recorded");
        });

        test("an empty treasury leaves the reward pending, then pays exactly once later", () -> {
            World w = new World(0);
            w.engine.start(P, w.story);
            w.ev("npc_talked", "steward", 1);
            var done = w.ev("item_delivered", "minecraft:wheat", 16);
            check(has(done, UpdateKind.QUEST_COMPLETED, "q1_harvest"), "quest completes anyway");
            check(has(done, UpdateKind.REWARD_PENDING, "q1_harvest"), "reward pending");
            check(!w.story.rewardPaid("q1_harvest"), "not marked paid");
            eq(0L, w.coins(), "nothing paid");
            eq(0L, w.engine.retryUnpaidRewards(P, w.story).stream().filter(u -> u.kind() == UpdateKind.REWARD_PAID).count(), "still broke");
            w.ledger.mint("settlement:s1", 500);
            var retry = w.engine.retryUnpaidRewards(P, w.story);
            check(has(retry, UpdateKind.REWARD_PAID, "q1_harvest"), "paid on retry");
            eq(40L, w.coins(), "paid once");
            eq(0, w.engine.retryUnpaidRewards(P, w.story).size(), "second retry does nothing");
            eq(40L, w.coins(), "still once");
        });

        test("chapter completion is announced once", () -> {
            World w = new World(5000);
            w.engine.start(P, w.story);
            int announced = 0;
            var all = new java.util.ArrayList<QuestEngine.StoryUpdate>();
            all.addAll(w.ev("npc_talked", "steward", 1));
            all.addAll(w.ev("item_delivered", "minecraft:wheat", 16));
            all.addAll(w.ev("item_delivered", "minecraft:oak_log", 20));
            all.addAll(w.ev("item_delivered", "minecraft:oak_log", 20));
            for (var u : all) if (u.kind() == UpdateKind.CHAPTER_COMPLETED && u.questId().equals("ch1")) announced++;
            eq(1, announced, "announced once");
        });

        System.out.println("Story definition validation");

        Chapter c = new Chapter("c1", "C", "s");
        Objective o = Objective.count("o", "t", "*", 1, "d");

        test("prerequisite cycles are rejected", () -> {
            Quest a = Quest.simple("a", "c1", "A", "i", List.of(o), Set.of("b"), Reward.NONE);
            Quest b = Quest.simple("b", "c1", "B", "i", List.of(o), Set.of("a"), Reward.NONE);
            boolean threw = false;
            try { new StoryDefinition(List.of(c), List.of(a, b)); } catch (IllegalArgumentException e) { threw = true; }
            check(threw, "cycle must throw");
        });

        test("unknown prerequisite, unknown chapter and duplicates are rejected", () -> {
            Quest a = Quest.simple("a", "c1", "A", "i", List.of(o), Set.of("ghost"), Reward.NONE);
            boolean t1 = false;
            try { new StoryDefinition(List.of(c), List.of(a)); } catch (IllegalArgumentException e) { t1 = true; }
            check(t1, "unknown prerequisite");
            Quest b = Quest.simple("b", "nochapter", "B", "i", List.of(o), Set.of(), Reward.NONE);
            boolean t2 = false;
            try { new StoryDefinition(List.of(c), List.of(b)); } catch (IllegalArgumentException e) { t2 = true; }
            check(t2, "unknown chapter");
            Quest d = Quest.simple("d", "c1", "D", "i", List.of(o), Set.of(), Reward.NONE);
            boolean t3 = false;
            try { new StoryDefinition(List.of(c), List.of(d, d)); } catch (IllegalArgumentException e) { t3 = true; }
            check(t3, "duplicate quest");
        });

        test("unsafe ids (slash, spaces) are rejected", () -> {
            Quest bad = Quest.simple("a/b", "c1", "A", "i", List.of(o), Set.of(), Reward.NONE);
            boolean threw = false;
            try { new StoryDefinition(List.of(c), List.of(bad)); } catch (IllegalArgumentException e) { threw = true; }
            check(threw, "slash id must throw");
        });

        test("the shipped starter story is valid", () -> {
            StoryDefinition d = DefaultStory.create();
            eq(3, d.chapters().size(), "chapters");
            eq(10, d.quests().size(), "quests");
        });
    }
}
