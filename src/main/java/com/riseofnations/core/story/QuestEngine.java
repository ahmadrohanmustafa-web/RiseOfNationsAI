package com.riseofnations.core.story;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the story. The Minecraft layer reports real events with {@link #onEvent}; the engine updates
 * progress, completes quests, pays rewards exactly once and unlocks the next quests by itself.
 * No "continue" command exists or is needed.
 */
public final class QuestEngine {

    /** Pays coins for a finished quest. Return true only if the money really moved. */
    public interface RewardSink {
        boolean pay(String playerId, String questId, long coins);
    }

    public enum UpdateKind { QUEST_STARTED, PROGRESS, QUEST_COMPLETED, REWARD_PAID, REWARD_PENDING, CHAPTER_COMPLETED }

    public record StoryUpdate(UpdateKind kind, String questId, String text) { }

    public enum ChoiceResult { OK, NOT_STARTED, QUEST_NOT_ACTIVE, NO_SUCH_OBJECTIVE, NOT_A_CHOICE, INVALID_OPTION, ALREADY_CHOSEN }

    private final StoryDefinition story;
    private final RewardSink sink;

    public QuestEngine(StoryDefinition story, RewardSink sink) {
        this.story = story;
        this.sink = sink;
    }

    public StoryDefinition story() { return story; }

    /** Begins the campaign for a player. Safe to call many times; only the first call does anything. */
    public List<StoryUpdate> start(String playerId, PlayerStory s) {
        List<StoryUpdate> out = new ArrayList<>();
        if (s.started) return out;
        s.started = true;
        activateAvailable(s, out);
        return out;
    }

    public List<StoryUpdate> onEvent(String playerId, PlayerStory s, GameEvent e) {
        List<StoryUpdate> out = new ArrayList<>();
        if (e == null || !e.isValid() || !s.started) return out;
        if (e.dedupeKey() != null) {
            String key = e.type() + "|" + e.dedupeKey();
            if (!s.seenKeys.add(key)) return out;   // already counted once
            s.trimSeen();
        }
        for (Quest q : story.quests()) {
            if (s.status.get(q.id()) != PlayerStory.Status.ACTIVE) continue;
            boolean changed = false;
            for (Objective o : q.objectives()) {
                if (!o.matches(e)) continue;
                String key = q.id() + "/" + o.id();
                long cur = s.progress.getOrDefault(key, 0L);
                if (cur >= o.target()) continue;
                long next = Math.min(o.target(), cur + e.amount());
                s.progress.put(key, next);
                changed = true;
                out.add(new StoryUpdate(UpdateKind.PROGRESS, q.id(), o.description() + " (" + next + "/" + o.target() + ")"));
            }
            if (changed) completeIfDone(playerId, s, q, out);
        }
        activateAvailable(s, out);
        return out;
    }

    /** The player picks an option of a choice objective. This is how branches are decided. */
    public ChoiceResult choose(String playerId, PlayerStory s, String questId, String objectiveId,
                               String optionId, List<StoryUpdate> out) {
        if (!s.started) return ChoiceResult.NOT_STARTED;
        Quest q = story.quest(questId);
        if (q == null || s.status.get(questId) != PlayerStory.Status.ACTIVE) return ChoiceResult.QUEST_NOT_ACTIVE;
        Objective target = null;
        for (Objective o : q.objectives()) {
            if (o.id().equals(objectiveId)) target = o;
        }
        if (target == null) return ChoiceResult.NO_SUCH_OBJECTIVE;
        if (!target.isChoice()) return ChoiceResult.NOT_A_CHOICE;
        String flag = target.choiceOptions().get(optionId);
        if (flag == null) return ChoiceResult.INVALID_OPTION;
        String key = questId + "/" + objectiveId;
        if (s.progress.getOrDefault(key, 0L) >= 1) return ChoiceResult.ALREADY_CHOSEN;
        s.progress.put(key, 1L);
        s.flags.add(flag);
        out.add(new StoryUpdate(UpdateKind.PROGRESS, questId, target.description() + " -> " + optionId));
        completeIfDone(playerId, s, q, out);
        activateAvailable(s, out);
        return ChoiceResult.OK;
    }

    /** Tries again to pay rewards that could not be paid earlier (for example an empty treasury). */
    public List<StoryUpdate> retryUnpaidRewards(String playerId, PlayerStory s) {
        List<StoryUpdate> out = new ArrayList<>();
        for (Quest q : story.quests()) {
            if (s.status.get(q.id()) == PlayerStory.Status.COMPLETED && !s.rewardsPaid.contains(q.id())) {
                payReward(playerId, s, q, out);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------

    private void completeIfDone(String playerId, PlayerStory s, Quest q, List<StoryUpdate> out) {
        if (s.status.get(q.id()) != PlayerStory.Status.ACTIVE) return;
        for (Objective o : q.objectives()) {
            if (s.progress.getOrDefault(q.id() + "/" + o.id(), 0L) < o.target()) return;
        }
        s.status.put(q.id(), PlayerStory.Status.COMPLETED);
        Reward r = q.reward();
        long rep = (long) s.reputation + r.reputation();
        s.reputation = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, rep));
        s.flags.addAll(r.setsFlags());
        s.unlocks.addAll(r.unlocks());
        out.add(new StoryUpdate(UpdateKind.QUEST_COMPLETED, q.id(), q.title()));
        payReward(playerId, s, q, out);
        checkChapter(s, q.chapterId(), out);
    }

    private void payReward(String playerId, PlayerStory s, Quest q, List<StoryUpdate> out) {
        if (s.rewardsPaid.contains(q.id())) return;
        long coins = q.reward().coins();
        if (coins == 0 || sink.pay(playerId, q.id(), coins)) {
            s.rewardsPaid.add(q.id());
            if (coins > 0) out.add(new StoryUpdate(UpdateKind.REWARD_PAID, q.id(), coins + " coins"));
        } else {
            out.add(new StoryUpdate(UpdateKind.REWARD_PENDING, q.id(), coins + " coins could not be paid yet"));
        }
    }

    /** A chapter is done when each of its quests is completed or was ruled out by an earlier choice. */
    private void checkChapter(PlayerStory s, String chapterId, List<StoryUpdate> out) {
        String flag = "chapter_done:" + chapterId;
        if (s.flags.contains(flag)) return;
        for (Quest q : story.questsOfChapter(chapterId)) {
            PlayerStory.Status st = s.status.get(q.id());
            if (st == PlayerStory.Status.COMPLETED) continue;
            if (st == null && isExcluded(q, s)) continue;
            return;
        }
        s.flags.add(flag);
        out.add(new StoryUpdate(UpdateKind.CHAPTER_COMPLETED, chapterId, story.chapter(chapterId).title()));
    }

    private boolean isExcluded(Quest q, PlayerStory s) {
        for (String f : q.forbidsFlags()) {
            if (s.flags.contains(f)) return true;
        }
        return false;
    }

    private boolean isAvailable(Quest q, PlayerStory s) {
        for (String req : q.requiresQuests()) {
            if (s.status.get(req) != PlayerStory.Status.COMPLETED) return false;
        }
        if (!s.flags.containsAll(q.requiresFlags())) return false;
        return !isExcluded(q, s);
    }

    private void activateAvailable(PlayerStory s, List<StoryUpdate> out) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Quest q : story.quests()) {
                if (s.status.get(q.id()) == null && isAvailable(q, s)) {
                    s.status.put(q.id(), PlayerStory.Status.ACTIVE);
                    out.add(new StoryUpdate(UpdateKind.QUEST_STARTED, q.id(), q.title()));
                    changed = true;
                }
            }
        }
    }
}
