package org.poolen.backend.db.constants;

import org.poolen.backend.db.interfaces.ISettings;

public abstract class Settings implements ISettings {
    public enum MatchmakerBonusSettings implements ISettings {
        HOUSE_BONUS,
        BLACKLIST_BONUS,
        BUDDY_BONUS,
        RECENCY_GRUDGE,
        MAX_REUNION_BONUS
    }
    public enum MatchmakerMultiplierSettings implements ISettings {
        HOUSE_SECOND_CHOICE_MULTIPLIER,
        HOUSE_THIRD_CHOICE_MULTIPLIER,
        HOUSE_FOURTH_CHOICE_MULTIPLIER,
        MAIN_CHARACTER_MULTIPLIER
    }
    public enum MatchmakerPrioritySettings implements ISettings {
        AMBER_PRIORITIES,
        AVENTURINE_PRIORITIES,
        GARNET_PRIORITIES,
        OPAL_PRIORITIES
    }

    public enum PersistenceSettings implements ISettings {
        SHEETS_ID,
        RECAP_DEADLINE,
        PLAYER_DATA_SHEET_NAME,
        SETTINGS_DATA_SHEET_NAME,
        GARNET_SHEET_NAME,
        AMBER_SHEET_NAME,
        AVENTURINE_SHEET_NAME,
        OPAL_SHEET_NAME,
        RECAP_SHEET_NAME,
        DISCORD_WEB_HOOK
    }
}

