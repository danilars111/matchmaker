package org.poolen.backend.db.interfaces;

import org.poolen.backend.db.constants.Settings;

public interface ISettings {
    /**
     * A helper to find the correct Enum constant from its String name.
     * This is necessary because the settings are split across multiple inner enums.
     *
     * @param name The String name of the setting (e.g., "HOUSE_BONUS").
     * @return The ISettings enum constant, or null if not found.
     */
    static ISettings find(String name) {
        // Check in each inner enum class within Settings
        for (Class<?> clazz : Settings.class.getClasses()) {
            if (clazz.isEnum() && ISettings.class.isAssignableFrom(clazz)) {
                for (Object enumConstant : clazz.getEnumConstants()) {
                    if (((Enum<?>) enumConstant).name().equals(name)) {
                        return (ISettings) enumConstant;
                    }
                }
            }
        }
        return null;
    }
}
