package org.poolen.backend.db.interfaces.store;

import org.poolen.backend.db.store.SettingsStore;

public interface SettingStoreProvider {
    SettingsStore getSettingsStore();
}
