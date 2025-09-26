package org.poolen.backend.db.entities;

import org.poolen.backend.db.interfaces.ISettings;

public class Setting<T> {
    ISettings name;
    String description;
    T settingValue;

    public Setting(ISettings name, String description, T settingValue) {
        this.name = name;
        this.settingValue = settingValue;
        this.description = description;
    }

    public ISettings getName() {
        return name;
    }

    public void setName(ISettings name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public T getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(T settingValue) {
        this.settingValue = settingValue;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
