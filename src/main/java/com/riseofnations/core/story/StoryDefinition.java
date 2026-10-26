package com.riseofnations.core.story;

import com.riseofnations.core.economy.Ledger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The full, validated story: chapters and quests. Invalid stories are rejected when loaded. */
public final class StoryDefinition {

    private final Map<String, Chapter> chapters = new LinkedHashMap<>();
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    public StoryDefinition(List<Chapter> chapterList, List<Quest> questList) {
        for (Chapter c : chapterList) {
            requireSafeId(c.id());
            if (chapters.put(c.id(), c) != null) throw new IllegalArgumentException("duplicate chapter " + c.id());
        }
        for (Quest q : questList) {
            requireSafeId(q.id());
            if (!chapters.containsKey(q.chapterId())) {
                throw new IllegalArgumentException("quest " + q.id() + " uses unknown chapter " + q.chapterId());
            }
            if (quests.put(q.id(), q) != null) throw new IllegalArgumentException("duplicate quest " + q.id());
            Set<String> objectiveIds = new HashSet<>();
            for (Objective o : q.objectives()) {
                requireSafeId(o.id());
                if (!objectiveIds.add(o.id())) {
                    throw new IllegalArgumentException("duplicate objective " + o.id() + " in " + q.id());
                }
            }
        }
        for (Quest q : quests.values()) {
            for (String req : q.requiresQuests()) {
                if (!quests.containsKey(req)) {
                    throw new IllegalArgumentException("quest " + q.id() + " requires unknown quest " + req);
                }
            }
        }
        rejectCycles();
    }

    /** Ids end up in save files, so they must be simple. '/' is reserved as a separator. */
    private static void requireSafeId(String id) {
        if (!Ledger.validId(id) || id.indexOf('/') >= 0) {
            throw new IllegalArgumentException("unsafe id: " + id);
        }
    }

    private void rejectCycles() {
        Set<String> done = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String id : quests.keySet()) {
            visit(id, done, visiting);
        }
    }

    private void visit(String id, Set<String> done, Set<String> visiting) {
        if (done.contains(id)) return;
        if (!visiting.add(id)) throw new IllegalArgumentException("quest prerequisite cycle at " + id);
        for (String req : quests.get(id).requiresQuests()) {
            visit(req, done, visiting);
        }
        visiting.remove(id);
        done.add(id);
    }

    public Quest quest(String id) { return quests.get(id); }

    public Chapter chapter(String id) { return chapters.get(id); }

    public List<Quest> quests() { return new ArrayList<>(quests.values()); }

    public List<Chapter> chapters() { return new ArrayList<>(chapters.values()); }

    public List<Quest> questsOfChapter(String chapterId) {
        List<Quest> out = new ArrayList<>();
        for (Quest q : quests.values()) {
            if (q.chapterId().equals(chapterId)) out.add(q);
        }
        return out;
    }
}
