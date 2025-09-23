package org.poolen.backend.db.constants;

import org.poolen.backend.db.interfaces.ISetting;

public abstract class Settings implements ISetting{
    public enum MatchmakerBonusSetting implements ISetting {
        HOUSE_BONUS,
        BLACKLIST_BONUS,
        BUDDY_BONUS,
        RECENCY_GRUDGE,
        MAX_REUNION_BONUS
    }
    public enum MatchmakerMultiplierSetting implements ISetting {
        HOUSE_SECOND_CHOICE_MULTIPLIER,
        HOUSE_THIRD_CHOICE_MULTIPLIER,
        HOUSE_FOURTH_CHOICE_MULTIPLIER,
        MAIN_CHARACTER_MULTIPLIER
    }
    public enum MatchmakerPrioritySetting implements ISetting {
        AMBER_PRIORITIES,
        AVENTURINE_PRIORITIES,
        GARNET_PRIORITIES,
        OPAL_PRIORITIES
    }

    public enum PersistenceSettings implements ISetting {
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

