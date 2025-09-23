package org.poolen.backend.db.store;


import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Setting;
import org.poolen.backend.db.interfaces.ISetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.poolen.backend.db.constants.Settings.MatchmakerBonusSetting.*;
import static org.poolen.backend.db.constants.Settings.MatchmakerMultiplierSetting.*;
import static org.poolen.backend.db.constants.Settings.MatchmakerPrioritySetting.*;
import static org.poolen.backend.db.constants.Settings.PersistenceSettings.*;

public class SettingsStore {

    // The single, final instance of our class.
    private static final SettingsStore INSTANCE = new SettingsStore();

    private Map<ISetting, Setting> settingsMap;

    // Private constructor to prevent additional instances and to enforce
    // singleton
    private SettingsStore() {
        this.settingsMap = new HashMap<>();
        setDefaultSettings();
    }
    public static SettingsStore getInstance() {
        return INSTANCE;
    }

    public Setting getSetting(ISetting setting){
        return this.settingsMap.get(setting);
    }
    public Map<ISetting, Setting> getSettingsMap() {
        return this.settingsMap;
    }

    public <T> void updateSetting(ISetting setting, T value) {
        if (!this.settingsMap.containsKey(setting)) {
            throw new IllegalArgumentException("Oh no! The setting '" + setting.toString() + "' doesn't exist. I can't update something that isn't here!");
        }

        Setting existingSetting = this.settingsMap.get(setting);
        Object existingValue = existingSetting.getSettingValue();

        // A more forgiving check! We allow any List implementation to replace another List.
        boolean isExistingValueList = existingValue instanceof List;
        boolean isNewValueList = value instanceof List;

        if (isExistingValueList && isNewValueList) {
            // Both are lists, this is fine! We proceed.
        } else if (existingValue != null && !existingValue.getClass().equals(value.getClass())) {
            // If they are not both lists, we fall back to the strict class check.
            String existingType = existingValue.getClass().getSimpleName();
            String newType = value.getClass().getSimpleName();
            throw new IllegalArgumentException("How dare you! The value type is all wrong. Expected a '" + existingType + "' but got a '" + newType + "'.");
        }

        // If we've made it this far, everything is just fine!
        existingSetting.setSettingValue(value);
    }


    public void setDefaultSettings() {
        List<House> amberPriorities = new ArrayList<>(List.of(House.GARNET, House.OPAL, House.AVENTURINE));
        List<House> aventurinePriorities = new ArrayList<>(List.of(House.OPAL, House.AMBER, House.GARNET));
        List<House> garnetPriorities = new ArrayList<>(List.of(House.AMBER, House.OPAL, House.AVENTURINE));
        List<House> opalPriorities = new ArrayList<>(List.of(House.AMBER, House.GARNET, House.AVENTURINE));

        this.settingsMap.put(
                HOUSE_BONUS, new Setting<Double>(HOUSE_BONUS, "The matchmaking bonus applied when there is a perfect house match", 500.0));
        this.settingsMap.put(
                HOUSE_SECOND_CHOICE_MULTIPLIER, new Setting<Double>(HOUSE_SECOND_CHOICE_MULTIPLIER, "Multiplier applied to the house bonus when there's a second choice match", 0.5));
        this.settingsMap.put(
                HOUSE_THIRD_CHOICE_MULTIPLIER, new Setting<Double>(HOUSE_THIRD_CHOICE_MULTIPLIER, "Multiplier applied to the house bonus when there's a third choice match", 0.25));
        this.settingsMap.put(
                HOUSE_FOURTH_CHOICE_MULTIPLIER, new Setting<Double>(HOUSE_FOURTH_CHOICE_MULTIPLIER, "Multiplier applied to the house bonus when there's a fourth choice match", 0.05));
        this.settingsMap.put(
                BLACKLIST_BONUS, new Setting<Double>(BLACKLIST_BONUS, "The Matchmaking bonus applied grouped with a blacklisted player", -5.0));
        this.settingsMap.put(
                BUDDY_BONUS, new Setting<Double>(BUDDY_BONUS, "The matchmaking bonus for playing with a buddy.", 5.0));
        this.settingsMap.put(
                MAIN_CHARACTER_MULTIPLIER, new Setting<Double>(MAIN_CHARACTER_MULTIPLIER, "Multiplier applied to when attempting to match a main character", 2.0));
        this.settingsMap.put(
                RECENCY_GRUDGE, new Setting<Double>(RECENCY_GRUDGE, "For how long in weeks the system maintains a matchmaking grudge between players", 12.0));
        this.settingsMap.put(
                MAX_REUNION_BONUS, new Setting<Double>(MAX_REUNION_BONUS, "Maximum Matchmaking bonus applies to players who have not played together in a while. Increases linearly up to the max when there is no Recency Grudge", 10.0));
        this.settingsMap.put(
                AMBER_PRIORITIES, new Setting<List>(AMBER_PRIORITIES, "The house priorities for Amber characters", amberPriorities));
        this.settingsMap.put(
                AVENTURINE_PRIORITIES, new Setting<List>(AVENTURINE_PRIORITIES, "The house priorities for Aventurine characters", aventurinePriorities));
        this.settingsMap.put(
                GARNET_PRIORITIES, new Setting<List>(GARNET_PRIORITIES, "The house priorities for Garnet characters", garnetPriorities));
        this.settingsMap.put(
                OPAL_PRIORITIES, new Setting<List>(OPAL_PRIORITIES, "The house priorities for Opal characters", opalPriorities));
        this.settingsMap.put(
                SHEETS_ID, new Setting<String>(SHEETS_ID, "The google sheets ID to read and write from\nhttps://docs.google.com/spreadsheets/d/[SHEETS_ID]/edit", "1YDOjqklvoJOfdV1nvA8IqyPpjqGrCMbP24VCLfC_OrU"));
        this.settingsMap.put(
                RECAP_DEADLINE, new Setting<Integer>(RECAP_DEADLINE, "The number of weeks after the session the deadline will be set", 8));
        this.settingsMap.put(
                RECAP_SHEET_NAME, new Setting<String>(RECAP_SHEET_NAME, "The name of the google sheets tab for Recaps, has to be spelled perfectly!", "First Semester"));
        this.settingsMap.put(
                PLAYER_DATA_SHEET_NAME, new Setting<String>(PLAYER_DATA_SHEET_NAME, "The name of the google sheets tab for Player Data, has to be spelled perfectly!", "PlayerData"));
        this.settingsMap.put(
                SETTINGS_DATA_SHEET_NAME, new Setting<String>(SETTINGS_DATA_SHEET_NAME, "The name of the google sheets tab for Settings Data, has to be spelled perfectly!", "SettingsData"));
        this.settingsMap.put(
                GARNET_SHEET_NAME, new Setting<String>(GARNET_SHEET_NAME, "The name of the google sheets tab for Garnet, has to be spelled perfectly!", "Alluring Garnet"));
        this.settingsMap.put(
                AMBER_SHEET_NAME, new Setting<String>(AMBER_SHEET_NAME, "The name of the google sheets tab for Amber, has to be spelled perfectly!", "Ancient Amber"));
        this.settingsMap.put(
                AVENTURINE_SHEET_NAME, new Setting<String>(AVENTURINE_SHEET_NAME, "The name of the google sheets tab for Aventurine, has to be spelled perfectly!", "Sharp Aventurine"));
        this.settingsMap.put(
                OPAL_SHEET_NAME, new Setting<String>(OPAL_SHEET_NAME, "The name of the google sheets tab for Garnet, has to be spelled perfectly!", "Shifting Opal"));
        this.settingsMap.put(
                DISCORD_WEB_HOOK, new Setting<String>(DISCORD_WEB_HOOK, "Discord webhook URL", ""));

    }
}

