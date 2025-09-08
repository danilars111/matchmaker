package org.poolen.backend.db.store;


import java.util.HashMap;
import java.util.Map;

public class SettingsStore {

    // The single, final instance of our class.
    private static final SettingsStore INSTANCE = new SettingsStore();

    private Map<String, Double> settingsMap;

    // Private constructor to prevent additional instances and to enforce
    // singleton
    private SettingsStore() {
        this.settingsMap = new HashMap<>();
    }
    public static SettingsStore getInstance() {
        return INSTANCE;
    }

    public double getSetting(String setting){
        return this.settingsMap.get(setting);
    }
    public Map<String, Double> getSettingsMap() {
        return this.settingsMap;
    }
}
