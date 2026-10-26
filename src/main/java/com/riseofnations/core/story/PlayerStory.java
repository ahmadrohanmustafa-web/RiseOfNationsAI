package com.riseofnations.core.story;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map;

/** Everything the story knows about one player. Plain data so it can be saved and restored. */
public final class PlayerStory {

    public enum Status { ACTIVE, COMPLETED }

    public static final int MAX_DEDUPE_KEYS = 1000;

    boolean started;
    int reputation;
    final Map<String, Status> status = new TreeMap<>();
    /** key "questId/objectiveId" -> progress */
    final Map<String, Long> progress = new TreeMap<>();
    final Set<String> flags = new TreeSet<>();
    final Set<String> rewardsPaid = new TreeSet<>();
    final Set<String> unlocks = new TreeSet<>();
    /** oldest first, trimmed to MAX_DEDUPE_KEYS */
    final Set<String> seenKeys = new LinkedHashSet<>();

    public boolean started() { return started; }

    public int reputation() { return reputation; }

    public Status statusOf(String questId) { return status.get(questId); }

    public long progressOf(String questId, String objectiveId) {
        return progress.getOrDefault(questId + "/" + objectiveId, 0L);
    }

    public boolean hasFlag(String flag) { return flags.contains(flag); }

    public boolean hasUnlock(String id) { return unlocks.contains(id); }

    public boolean rewardPaid(String questId) { return rewardsPaid.contains(questId); }

    public Map<String, Status> statusView() { return java.util.Collections.unmodifiableMap(status); }

    public Map<String, Long> progressView() { return java.util.Collections.unmodifiableMap(progress); }

    public Set<String> flagsView() { return java.util.Collections.unmodifiableSet(flags); }

    public Set<String> unlocksView() { return java.util.Collections.unmodifiableSet(unlocks); }

    public Set<String> rewardsPaidView() { return java.util.Collections.unmodifiableSet(rewardsPaid); }

    public Set<String> seenKeysView() { return java.util.Collections.unmodifiableSet(seenKeys); }

    // ---- restore helpers used by the save-data decoder (all defensive) ----
    public void restoreStarted(boolean v) { started = v; }

    public void restoreReputation(int v) { reputation = v; }

    public void restoreStatus(String questId, Status s) { if (questId != null && s != null) status.put(questId, s); }

    public void restoreProgress(String key, long v) { if (key != null && v >= 0) progress.put(key, v); }

    public void restoreFlag(String f) { if (f != null) flags.add(f); }

    public void restorePaid(String q) { if (q != null) rewardsPaid.add(q); }

    public void restoreUnlock(String u) { if (u != null) unlocks.add(u); }

    public void restoreSeen(String k) { if (k != null) { seenKeys.add(k); trimSeen(); } }

    void trimSeen() {
        while (seenKeys.size() > MAX_DEDUPE_KEYS) {
            var it = seenKeys.iterator();
            it.next();
            it.remove();
        }
    }
}
