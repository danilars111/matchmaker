package org.poolen.backend.db.constants;

public abstract class Settings {
    private static final String BASE = "setting";


    // MATCHMAKING BONUSES
    private static final String BASE_MATCHMAKING = "%s_matchmaking".formatted(BASE);
    public static final String HOUSE_BONUS = "%s_houseBonus".formatted(BASE_MATCHMAKING);
    public static final String HOUSE_SECOND_CHOICE_BONUS = "%s_houseSecondChoiceBonus".formatted(BASE_MATCHMAKING);
    public static final String HOUSE_THIRD_CHOICE_BONUS = "%s_houseThirdChoiceBonus".formatted(BASE_MATCHMAKING);
    public static final String HOUSE_FOURTH_CHOICE_BONUS = "%s_houseFourthChoiceBonus".formatted(BASE_MATCHMAKING);
    public static final String BLACKLIST_BONUS = "%s_blacklistBonus".formatted(BASE_MATCHMAKING);
    public static final String BUDDY_BONUS = "%s_buddyBonus".formatted(BASE_MATCHMAKING);

    // HOUSE PRIORITIES:
    private static final String BASE_HOUSE_PRIORITIES = "%s_housePriorities".formatted(BASE);
    public static final String AMBER_PRIORITIES = "%s_amber".formatted(BASE_HOUSE_PRIORITIES);
    public static final String AVENTURINE_PRIORITIES = "%s_aventurine".formatted(BASE_HOUSE_PRIORITIES);
    public static final String GARNET_PRIORITIES = "%s_garnet".formatted(BASE_HOUSE_PRIORITIES);
    public static final String OPAL_PRIORITIES = "%s_opal".formatted(BASE_HOUSE_PRIORITIES);
}
