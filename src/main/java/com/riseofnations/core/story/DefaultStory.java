package com.riseofnations.core.story;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Starter campaign content: chapters 1 to 3 of the planned 10. Later chapters are NOT written yet.
 * Rewards are small on purpose; they come out of a real treasury, not thin air.
 */
public final class DefaultStory {

    private DefaultStory() { }

    public static StoryDefinition create() {
        List<Chapter> chapters = List.of(
                new Chapter("ch1", "A New Beginning", "You arrive in a small settlement and learn how it works."),
                new Chapter("ch2", "Build Your Future", "Earn an honest living and find a place to call home."),
                new Chapter("ch3", "A Name in the Community", "Prove yourself and take a side in a local dispute."));

        Quest steward = Quest.simple("q1_steward", "ch1", "Meet the Steward",
                "Speak to the settlement steward to learn what the town needs.",
                List.of(Objective.count("talk", "npc_talked", "steward", 1, "Talk to the steward")),
                Set.of(), Reward.NONE);
        Quest harvest = Quest.simple("q1_harvest", "ch1", "The First Harvest",
                "The granary is low. Deliver wheat to the steward.",
                List.of(Objective.count("wheat", "item_delivered", "minecraft:wheat", 16, "Deliver wheat")),
                Set.of("q1_steward"), Reward.coins(40, 1));
        Quest timber = Quest.simple("q1_timber", "ch1", "Timber for the Town",
                "Builders need logs for the new market stalls.",
                List.of(Objective.count("logs", "item_delivered", "minecraft:oak_log", 20, "Deliver oak logs")),
                Set.of("q1_steward"), Reward.coins(40, 1));

        Quest earn = Quest.simple("q2_earn", "ch2", "An Honest Living",
                "Complete work contracts and earn wages from the settlement.",
                List.of(Objective.count("wages", "wage_earned", "*", 150, "Earn wages")),
                Set.of("q1_harvest", "q1_timber"),
                new Reward(60, 2, Set.of("market_access"), Set.of()));
        Quest home = Quest.simple("q2_home", "ch2", "A Roof Overhead",
                "Rent or claim a home through the settlement office.",
                List.of(Objective.count("home", "property_acquired", "*", 1, "Obtain a home")),
                Set.of("q2_earn"),
                new Reward(100, 2, Set.of("home_storage"), Set.of()));
        Quest merchant = Quest.simple("q2_merchant", "ch2", "Meet the Merchant",
                "Introduce yourself to the merchant and make a first trade.",
                List.of(Objective.count("talk", "npc_talked", "merchant", 1, "Talk to the merchant"),
                        Objective.count("trade", "trade_completed", "*", 1, "Complete a trade")),
                Set.of("q2_earn"), Reward.coins(50, 1));

        Quest contracts = Quest.simple("q3_contracts", "ch3", "Making a Name",
                "Finish five different contracts. Each contract only counts once.",
                List.of(Objective.count("contracts", "contract_completed", "*", 5, "Complete contracts")),
                Set.of("q2_home", "q2_merchant"), Reward.coins(120, 3));
        Quest dispute = Quest.simple("q3_dispute", "ch3", "Farmers and Merchants",
                "Farmers and merchants argue about grain prices. Whose side will you take?",
                List.of(Objective.choice("side", "Choose a side",
                        Map.of("farmers", "dispute_farmers", "merchants", "dispute_merchants"))),
                Set.of("q3_contracts"), Reward.coins(0, 0));
        Quest farmersPath = new Quest("q3_farmers", "ch3", "Feed the Fields",
                "The farmers trust you. Deliver more wheat so they can hold prices steady.",
                List.of(Objective.count("wheat", "item_delivered", "minecraft:wheat", 32, "Deliver wheat")),
                Set.of("q3_dispute"), Set.of("dispute_farmers"), Set.of("dispute_merchants"),
                new Reward(150, 4, Set.of(), Set.of("backed_farmers")));
        Quest merchantsPath = new Quest("q3_merchants", "ch3", "Open the Markets",
                "The merchants trust you. Complete trades to prove the market is healthy.",
                List.of(Objective.count("trades", "trade_completed", "*", 3, "Complete trades")),
                Set.of("q3_dispute"), Set.of("dispute_merchants"), Set.of("dispute_farmers"),
                new Reward(150, 4, Set.of(), Set.of("backed_merchants")));

        return new StoryDefinition(chapters,
                List.of(steward, harvest, timber, earn, home, merchant, contracts, dispute, farmersPath, merchantsPath));
    }
}
