package com.riseofnations.core.economy;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * The single source of truth for money. Pure Java (no Minecraft code) so it can be unit tested.
 *
 * Rules that make exploits impossible by design:
 *  - Balances can never go negative (no debt yet).
 *  - Money only enters through {@link #mint} (explicit, counted) and only leaves through {@link #sink}.
 *  - Invariant: totalSupply() == totalMinted() - totalBurned() at all times.
 *  - Every operation is all-or-nothing: if it fails, nothing changes.
 *
 * Account ids look like "player:<uuid>", "nation:<id>", "settlement:<id>".
 */
public final class Ledger {

    public enum Result { OK, INVALID_AMOUNT, SAME_ACCOUNT, UNKNOWN_ACCOUNT, INSUFFICIENT_FUNDS, OVERFLOW }

    public record TaxedResult(Result result, long tax) { }

    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_:.\\-/]{1,96}$");
    public static final int MAX_BASIS_POINTS = 10_000;

    private final Map<String, Long> balances = new TreeMap<>();
    private long minted;
    private long burned;

    public static boolean validId(String id) {
        return id != null && VALID_ID.matcher(id).matches();
    }

    /** Opens an account with a zero balance. Returns false if the id is invalid or already exists. */
    public boolean open(String id) {
        if (!validId(id) || balances.containsKey(id)) {
            return false;
        }
        balances.put(id, 0L);
        return true;
    }

    public boolean exists(String id) {
        return id != null && balances.containsKey(id);
    }

    /** Balance of an account, or 0 if it does not exist. */
    public long balance(String id) {
        return id == null ? 0L : balances.getOrDefault(id, 0L);
    }

    public long totalMinted() { return minted; }

    public long totalBurned() { return burned; }

    public long totalSupply() {
        long sum = 0;
        for (long v : balances.values()) {
            sum += v;
        }
        return sum;
    }

    public Map<String, Long> balancesView() {
        return Collections.unmodifiableMap(balances);
    }

    /** Creates new money. Only for deliberate sources (starting treasuries, config seeds). */
    public Result mint(String to, long amount) {
        if (amount <= 0) return Result.INVALID_AMOUNT;
        if (!exists(to)) return Result.UNKNOWN_ACCOUNT;
        long newBalance;
        long newMinted;
        try {
            newBalance = Math.addExact(balances.get(to), amount);
            newMinted = Math.addExact(minted, amount);
        } catch (ArithmeticException e) {
            return Result.OVERFLOW;
        }
        balances.put(to, newBalance);
        minted = newMinted;
        return Result.OK;
    }

    /** Destroys money (maintenance, construction, upkeep). */
    public Result sink(String from, long amount) {
        if (amount <= 0) return Result.INVALID_AMOUNT;
        if (!exists(from)) return Result.UNKNOWN_ACCOUNT;
        long have = balances.get(from);
        if (have < amount) return Result.INSUFFICIENT_FUNDS;
        long newBurned;
        try {
            newBurned = Math.addExact(burned, amount);
        } catch (ArithmeticException e) {
            return Result.OVERFLOW;
        }
        balances.put(from, have - amount);
        burned = newBurned;
        return Result.OK;
    }

    public Result transfer(String from, String to, long amount) {
        if (amount <= 0) return Result.INVALID_AMOUNT;
        if (from == null || from.equals(to)) return Result.SAME_ACCOUNT;
        if (!exists(from) || !exists(to)) return Result.UNKNOWN_ACCOUNT;
        long have = balances.get(from);
        if (have < amount) return Result.INSUFFICIENT_FUNDS;
        long newTo;
        try {
            newTo = Math.addExact(balances.get(to), amount);
        } catch (ArithmeticException e) {
            return Result.OVERFLOW;
        }
        balances.put(from, have - amount);
        balances.put(to, newTo);
        return Result.OK;
    }

    /**
     * Buyer pays the full amount; the seller receives amount minus tax; the tax goes to taxAccount.
     * Tax is rounded down in integer math (no fractional coins).
     */
    public TaxedResult transferTaxed(String from, String to, long amount, String taxAccount, int basisPoints) {
        if (amount <= 0 || basisPoints < 0 || basisPoints > MAX_BASIS_POINTS) {
            return new TaxedResult(Result.INVALID_AMOUNT, 0);
        }
        if (from == null || from.equals(to) || from.equals(taxAccount)) {
            return new TaxedResult(Result.SAME_ACCOUNT, 0);
        }
        if (!exists(from) || !exists(to) || !exists(taxAccount)) {
            return new TaxedResult(Result.UNKNOWN_ACCOUNT, 0);
        }
        if (amount > Long.MAX_VALUE / MAX_BASIS_POINTS) {
            return new TaxedResult(Result.OVERFLOW, 0);
        }
        long tax = amount * basisPoints / MAX_BASIS_POINTS;
        long toSeller = amount - tax;
        long have = balances.get(from);
        if (have < amount) {
            return new TaxedResult(Result.INSUFFICIENT_FUNDS, 0);
        }
        long newTo;
        long newTax;
        try {
            if (to.equals(taxAccount)) {
                newTo = Math.addExact(balances.get(to), amount);
                newTax = newTo;
            } else {
                newTo = Math.addExact(balances.get(to), toSeller);
                newTax = Math.addExact(balances.get(taxAccount), tax);
            }
        } catch (ArithmeticException e) {
            return new TaxedResult(Result.OVERFLOW, 0);
        }
        balances.put(from, have - amount);
        balances.put(to, newTo);
        balances.put(taxAccount, newTax);
        return new TaxedResult(Result.OK, tax);
    }

    /**
     * Rebuilds a ledger from saved data. Invalid accounts or negative balances are dropped.
     * The minted figure is reconciled so the supply invariant always holds after loading.
     */
    public static Restored restore(Map<String, Long> saved, long savedMinted, long savedBurned) {
        Ledger ledger = new Ledger();
        int dropped = 0;
        long supply = 0;
        for (Map.Entry<String, Long> e : saved.entrySet()) {
            if (!validId(e.getKey()) || e.getValue() == null || e.getValue() < 0) {
                dropped++;
                continue;
            }
            long next;
            try {
                next = Math.addExact(supply, e.getValue());
            } catch (ArithmeticException ex) {
                dropped++;      // would make the total supply wrap around; refuse this account
                continue;
            }
            supply = next;
            ledger.balances.put(e.getKey(), e.getValue());
        }
        long burnedValue = Math.max(0, savedBurned);
        long expectedMinted;
        try {
            expectedMinted = Math.addExact(supply, burnedValue);
        } catch (ArithmeticException ex) {
            burnedValue = 0;
            dropped++;
            expectedMinted = supply;
        }
        boolean reconciled = dropped > 0 || savedMinted != expectedMinted || savedBurned < 0;
        ledger.burned = burnedValue;
        ledger.minted = expectedMinted;
        return new Restored(ledger, reconciled, dropped);
    }

    public record Restored(Ledger ledger, boolean reconciled, int droppedAccounts) { }
}
