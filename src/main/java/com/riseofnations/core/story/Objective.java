package com.riseofnations.core.story;

import com.riseofnations.core.economy.Ledger;

import java.util.Map;

/**
 * One step of a quest.
 *  - Counting objective: reacts to GameEvents of a given type (and subject, or "*" for any) until target is reached.
 *  - Choice objective: completed when the player picks one of the options; each option sets a flag
 *    (this is how the story branches).
 */
public record Objective(String id, String eventType, String subject, long target, String description,
                        Map<String, String> choiceOptions) {

    public Objective {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("objective id missing");
        if (choiceOptions == null) {
            if (eventType == null || subject == null || target <= 0) {
                throw new IllegalArgumentException("counting objective " + id + " needs type, subject and target > 0");
            }
        } else {
            if (choiceOptions.size() < 2) throw new IllegalArgumentException("choice " + id + " needs 2+ options");
            for (Map.Entry<String, String> e : choiceOptions.entrySet()) {
                if (!Ledger.validId(e.getKey()) || !Ledger.validId(e.getValue())) {
                    throw new IllegalArgumentException("choice " + id + " has an invalid option or flag id");
                }
            }
            choiceOptions = Map.copyOf(choiceOptions);
        }
    }

    public static Objective count(String id, String eventType, String subject, long target, String description) {
        return new Objective(id, eventType, subject, target, description, null);
    }

    public static Objective choice(String id, String description, Map<String, String> optionToFlag) {
        return new Objective(id, null, null, 1, description, optionToFlag);
    }

    public boolean isChoice() {
        return choiceOptions != null;
    }

    public boolean matches(GameEvent e) {
        return !isChoice() && eventType.equals(e.type()) && ("*".equals(subject) || subject.equals(e.subject()));
    }
}
