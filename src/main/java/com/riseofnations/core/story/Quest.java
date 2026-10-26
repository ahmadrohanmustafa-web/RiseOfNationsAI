package com.riseofnations.core.story;

import java.util.List;
import java.util.Set;

/**
 * A quest becomes available automatically when all prerequisite quests are completed,
 * all required flags are set and no forbidden flag is set. Nobody has to type "continue".
 */
public record Quest(String id, String chapterId, String title, String intro, List<Objective> objectives,
                    Set<String> requiresQuests, Set<String> requiresFlags, Set<String> forbidsFlags,
                    Reward reward) {

    public Quest {
        if (objectives == null || objectives.isEmpty()) throw new IllegalArgumentException("quest " + id + " has no objectives");
        objectives = List.copyOf(objectives);
        requiresQuests = Set.copyOf(requiresQuests);
        requiresFlags = Set.copyOf(requiresFlags);
        forbidsFlags = Set.copyOf(forbidsFlags);
        if (reward == null) reward = Reward.NONE;
    }

    public static Quest simple(String id, String chapterId, String title, String intro,
                               List<Objective> objectives, Set<String> requiresQuests, Reward reward) {
        return new Quest(id, chapterId, title, intro, objectives, requiresQuests, Set.of(), Set.of(), reward);
    }
}
