package org.poolen.backend.db.constants;

public abstract class Settings {
    private static final String BASE = "setting";


    // MATCHMAKING BONUSES
    private static final String BASE_MATCHMAKING = "%s_matchmaking".formatted(BASE);
    public static final String HOUSE_BONUS = "%s_houseBonus".formatted(BASE_MATCHMAKING);
    public static final String HOUSE_SECOND_CHOICE_MULTIPLIER = "%s_houseSecondChoiceMultiplier".formatted(BASE_MATCHMAKING);
    public static final String HOUSE_THIRD_CHOICE_MULTIPLIER = "%s_houseThirdChoiceMultiplier".formatted(BASE_MATCHMAKING);
    public static final String HOUSE_FOURTH_CHOICE_MULTIPLIER = "%s_houseFourthChoiceMultiplier".formatted(BASE_MATCHMAKING);
    public static final String BLACKLIST_BONUS = "%s_blacklistBonus".formatted(BASE_MATCHMAKING);
    public static final String BUDDY_BONUS = "%s_buddyBonus".formatted(BASE_MATCHMAKING);
    public static final String MAIN_CHARACTER_MULTIPLIER = "%s_mainCharacterMultiplier".formatted(BASE_MATCHMAKING);
    public static final String RECENCY_GRUDGE = "%s_recencyGrudge".formatted(BASE_MATCHMAKING);
    public static final String MAX_REUNION_BONUS = "%s_maxReunionBonus".formatted(BASE_MATCHMAKING);
    public static final String BASE_MATCHMAKING_HOUSE = "%s_house".formatted(BASE_MATCHMAKING);

    // HOUSE PRIORITIES:
    private static final String BASE_HOUSE_PRIORITIES = "%s_housePriorities".formatted(BASE);
    public static final String AMBER_PRIORITIES = "%s_amber".formatted(BASE_HOUSE_PRIORITIES);
    public static final String AVENTURINE_PRIORITIES = "%s_aventurine".formatted(BASE_HOUSE_PRIORITIES);
    public static final String GARNET_PRIORITIES = "%s_garnet".formatted(BASE_HOUSE_PRIORITIES);
    public static final String OPAL_PRIORITIES = "%s_opal".formatted(BASE_HOUSE_PRIORITIES);
}
