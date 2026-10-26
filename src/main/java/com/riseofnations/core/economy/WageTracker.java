package com.riseofnations.core.economy;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pays wages from an employer account (a settlement or nation treasury) to a worker, with limits.
 * There is no way to earn money here unless the employer actually has it, and no way to
 * collect wages faster than the cooldown or above the daily cap.
 */
public final class WageTracker {

    public enum Result { PAID, INVALID, ON_COOLDOWN, DAILY_CAP_REACHED, EMPLOYER_CANNOT_PAY, LEDGER_ERROR }

    public record Outcome(Result result, long paid) { }

    /** key "worker|job" -> tick of last payment */
    private final Map<String, Long> lastPaid = new TreeMap<>();
    /** worker -> {dayIndex, earnedThatDay} */
    private final Map<String, long[]> daily = new TreeMap<>();

    public Outcome pay(Ledger ledger, String employer, String worker, String job, long amount,
                       long nowTick, long cooldownTicks, long dailyCap, long dayLengthTicks) {
        if (amount <= 0 || nowTick < 0 || cooldownTicks < 0 || dailyCap <= 0 || dayLengthTicks <= 0
                || !Ledger.validId(worker) || !Ledger.validId(job)) {
            return new Outcome(Result.INVALID, 0);
        }
        String key = worker + "|" + job;
        Long last = lastPaid.get(key);
        // If the clock went backwards (restored backup), do not lock the player out forever.
        if (last != null && nowTick >= last && nowTick - last < cooldownTicks) {
            return new Outcome(Result.ON_COOLDOWN, 0);
        }
        long day = nowTick / dayLengthTicks;
        long[] d = daily.get(worker);
        long earnedToday = (d != null && d[0] == day) ? d[1] : 0;
        if (earnedToday > dailyCap - amount) {
            return new Outcome(Result.DAILY_CAP_REACHED, 0);
        }
        if (ledger.balance(employer) < amount) {
            return new Outcome(Result.EMPLOYER_CANNOT_PAY, 0);
        }
        Ledger.Result r = ledger.transfer(employer, worker, amount);
        if (r != Ledger.Result.OK) {
            return new Outcome(Result.LEDGER_ERROR, 0);
        }
        lastPaid.put(key, nowTick);
        daily.put(worker, new long[] {day, earnedToday + amount});
        return new Outcome(Result.PAID, amount);
    }

    public Map<String, Long> lastPaidView() { return Collections.unmodifiableMap(lastPaid); }

    public Map<String, long[]> dailyView() { return Collections.unmodifiableMap(daily); }

    public void restoreLastPaid(String key, long tick) {
        if (key != null && key.indexOf('|') > 0 && tick >= 0) lastPaid.put(key, tick);
    }

    public void restoreDaily(String worker, long day, long earned) {
        if (Ledger.validId(worker) && day >= 0 && earned >= 0) daily.put(worker, new long[] {day, earned});
    }
}
