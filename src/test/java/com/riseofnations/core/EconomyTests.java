package com.riseofnations.core;

import com.riseofnations.core.economy.Ledger;
import com.riseofnations.core.economy.WageTracker;

import java.util.HashMap;
import java.util.Map;

import static com.riseofnations.core.TestKit.check;
import static com.riseofnations.core.TestKit.eq;
import static com.riseofnations.core.TestKit.test;

final class EconomyTests {

    private EconomyTests() { }

    private static Ledger ledgerWith(long a, long b) {
        Ledger l = new Ledger();
        l.open("player:a");
        l.open("player:b");
        l.open("settlement:s1");
        l.mint("player:a", a);
        l.mint("player:b", b);
        return l;
    }

    private static void supplyHolds(Ledger l) {
        eq(l.totalMinted() - l.totalBurned(), l.totalSupply(), "supply invariant");
    }

    static void run() {
        System.out.println("Economy");

        test("transfer moves money and keeps total supply", () -> {
            Ledger l = ledgerWith(100, 0);
            eq(Ledger.Result.OK, l.transfer("player:a", "player:b", 40), "result");
            eq(60L, l.balance("player:a"), "a");
            eq(40L, l.balance("player:b"), "b");
            supplyHolds(l);
        });

        test("cannot overspend; failed transfer changes nothing", () -> {
            Ledger l = ledgerWith(50, 10);
            eq(Ledger.Result.INSUFFICIENT_FUNDS, l.transfer("player:a", "player:b", 51), "result");
            eq(50L, l.balance("player:a"), "a");
            eq(10L, l.balance("player:b"), "b");
        });

        test("zero, negative, same-account and unknown-account transfers are rejected", () -> {
            Ledger l = ledgerWith(50, 10);
            eq(Ledger.Result.INVALID_AMOUNT, l.transfer("player:a", "player:b", 0), "zero");
            eq(Ledger.Result.INVALID_AMOUNT, l.transfer("player:a", "player:b", -5), "negative");
            eq(Ledger.Result.SAME_ACCOUNT, l.transfer("player:a", "player:a", 5), "same");
            eq(Ledger.Result.UNKNOWN_ACCOUNT, l.transfer("player:a", "player:ghost", 5), "unknown");
            eq(60L, l.totalSupply(), "supply untouched");
        });

        test("minting past the maximum is detected and changes nothing", () -> {
            Ledger l = new Ledger();
            l.open("player:rich");
            eq(Ledger.Result.OK, l.mint("player:rich", Long.MAX_VALUE), "mint max");
            eq(Ledger.Result.OVERFLOW, l.mint("player:rich", 1), "one more");
            eq(Long.MAX_VALUE, l.balance("player:rich"), "unchanged");
        });

        test("sink destroys money and the supply invariant still holds", () -> {
            Ledger l = ledgerWith(100, 0);
            eq(Ledger.Result.OK, l.sink("player:a", 30), "sink");
            eq(70L, l.totalSupply(), "supply");
            eq(30L, l.totalBurned(), "burned");
            supplyHolds(l);
            eq(Ledger.Result.INSUFFICIENT_FUNDS, l.sink("player:a", 1000), "cannot sink more than owned");
        });

        test("taxed transfer splits payment: buyer pays all, seller gets rest, treasury gets tax", () -> {
            Ledger l = ledgerWith(1000, 0);
            Ledger.TaxedResult r = l.transferTaxed("player:a", "player:b", 200, "settlement:s1", 500);
            eq(Ledger.Result.OK, r.result(), "result");
            eq(10L, r.tax(), "tax 5% of 200");
            eq(800L, l.balance("player:a"), "buyer");
            eq(190L, l.balance("player:b"), "seller");
            eq(10L, l.balance("settlement:s1"), "treasury");
            supplyHolds(l);
        });

        test("tax rounds down and a failed taxed transfer is atomic", () -> {
            Ledger l = ledgerWith(1000, 0);
            eq(1L, l.transferTaxed("player:a", "player:b", 33, "settlement:s1", 500).tax(), "33*5% = 1.65 -> 1");
            Ledger.TaxedResult bad = l.transferTaxed("player:a", "player:b", 5000, "settlement:s1", 500);
            eq(Ledger.Result.INSUFFICIENT_FUNDS, bad.result(), "too poor");
            eq(967L, l.balance("player:a"), "a unchanged by failure");
            eq(Ledger.Result.INVALID_AMOUNT, l.transferTaxed("player:a", "player:b", 10, "settlement:s1", 10_001).result(), "tax over 100%");
            supplyHolds(l);
        });

        test("account ids are validated (no spaces, tabs, uppercase, empty)", () -> {
            Ledger l = new Ledger();
            check(!l.open("bad id"), "space");
            check(!l.open("bad\tid"), "tab");
            check(!l.open("UPPER"), "uppercase");
            check(!l.open(""), "empty");
            check(!l.open(null), "null");
            check(l.open("player:ok-1"), "good id");
            check(!l.open("player:ok-1"), "duplicate");
        });

        test("restore drops negative balances and reconciles minted", () -> {
            Map<String, Long> saved = new HashMap<>();
            saved.put("player:a", 100L);
            saved.put("player:cheat", -999L);
            saved.put("BAD ID", 5L);
            Ledger.Restored r = Ledger.restore(saved, 12345, 0);
            check(r.reconciled(), "flagged");
            eq(2, r.droppedAccounts(), "dropped");
            eq(100L, r.ledger().totalSupply(), "supply");
            supplyHolds(r.ledger());
        });

        test("restore refuses balances that would overflow total supply", () -> {
            Map<String, Long> saved = new java.util.TreeMap<>();
            saved.put("player:a", Long.MAX_VALUE);
            saved.put("player:b", 10L);
            Ledger.Restored r = Ledger.restore(saved, 0, 0);
            check(r.reconciled(), "flagged");
            check(r.ledger().totalSupply() >= 0, "supply did not wrap negative");
            supplyHolds(r.ledger());
        });

        System.out.println("Wages");

        test("wage is paid from the employer's treasury, not created", () -> {
            Ledger l = ledgerWith(0, 0);
            l.mint("settlement:s1", 1000);
            WageTracker w = new WageTracker();
            WageTracker.Outcome o = w.pay(l, "settlement:s1", "player:a", "farmer", 20, 100, 1200, 500, 24000);
            eq(WageTracker.Result.PAID, o.result(), "paid");
            eq(980L, l.balance("settlement:s1"), "treasury");
            eq(20L, l.balance("player:a"), "worker");
            supplyHolds(l);
        });

        test("cooldown blocks repeated wage spam", () -> {
            Ledger l = ledgerWith(0, 0);
            l.mint("settlement:s1", 1000);
            WageTracker w = new WageTracker();
            w.pay(l, "settlement:s1", "player:a", "farmer", 20, 100, 1200, 500, 24000);
            eq(WageTracker.Result.ON_COOLDOWN, w.pay(l, "settlement:s1", "player:a", "farmer", 20, 101, 1200, 500, 24000).result(), "spam");
            eq(WageTracker.Result.ON_COOLDOWN, w.pay(l, "settlement:s1", "player:a", "farmer", 20, 1299, 1200, 500, 24000).result(), "just before");
            eq(WageTracker.Result.PAID, w.pay(l, "settlement:s1", "player:a", "farmer", 20, 1300, 1200, 500, 24000).result(), "after cooldown");
            eq(40L, l.balance("player:a"), "only two payments");
        });

        test("cooldown is per job, so a different job is independent", () -> {
            Ledger l = ledgerWith(0, 0);
            l.mint("settlement:s1", 1000);
            WageTracker w = new WageTracker();
            w.pay(l, "settlement:s1", "player:a", "farmer", 20, 100, 1200, 500, 24000);
            eq(WageTracker.Result.PAID, w.pay(l, "settlement:s1", "player:a", "miner", 20, 101, 1200, 500, 24000).result(), "other job");
        });

        test("daily cap stops earning and resets the next day", () -> {
            Ledger l = ledgerWith(0, 0);
            l.mint("settlement:s1", 10_000);
            WageTracker w = new WageTracker();
            long t = 0;
            long total = 0;
            while (w.pay(l, "settlement:s1", "player:a", "farmer", 100, t, 10, 500, 24000).result() == WageTracker.Result.PAID) {
                total += 100;
                t += 10;
            }
            eq(500L, total, "cap reached exactly");
            eq(WageTracker.Result.DAILY_CAP_REACHED, w.pay(l, "settlement:s1", "player:a", "farmer", 100, t + 20, 10, 500, 24000).result(), "still capped");
            eq(WageTracker.Result.PAID, w.pay(l, "settlement:s1", "player:a", "farmer", 100, 24000, 10, 500, 24000).result(), "next day");
        });

        test("broke employer cannot pay and nothing is recorded", () -> {
            Ledger l = ledgerWith(0, 0);
            l.mint("settlement:s1", 10);
            WageTracker w = new WageTracker();
            eq(WageTracker.Result.EMPLOYER_CANNOT_PAY, w.pay(l, "settlement:s1", "player:a", "farmer", 20, 100, 1200, 500, 24000).result(), "broke");
            eq(0L, l.balance("player:a"), "no money created");
            eq(WageTracker.Result.PAID, w.pay(l, "settlement:s1", "player:a", "farmer", 10, 101, 1200, 500, 24000).result(), "retry works at once");
        });

        test("clock moving backwards does not lock the player out", () -> {
            Ledger l = ledgerWith(0, 0);
            l.mint("settlement:s1", 1000);
            WageTracker w = new WageTracker();
            w.pay(l, "settlement:s1", "player:a", "farmer", 10, 50_000, 1200, 5000, 24000);
            eq(WageTracker.Result.PAID, w.pay(l, "settlement:s1", "player:a", "farmer", 10, 100, 1200, 5000, 24000).result(), "rewound");
        });

        test("invalid wage inputs are rejected", () -> {
            Ledger l = ledgerWith(0, 0);
            l.mint("settlement:s1", 1000);
            WageTracker w = new WageTracker();
            eq(WageTracker.Result.INVALID, w.pay(l, "settlement:s1", "player:a", "farmer", 0, 1, 1, 1, 1).result(), "zero");
            eq(WageTracker.Result.INVALID, w.pay(l, "settlement:s1", "player:a", "farmer", -5, 1, 1, 1, 1).result(), "negative");
            eq(WageTracker.Result.INVALID, w.pay(l, "settlement:s1", "BAD NAME", "farmer", 5, 1, 1, 1, 1).result(), "bad worker");
        });
    }
}
