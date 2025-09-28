package org.poolen.backend.db.store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Store {
    private final CharacterStore characterStore;
    private final PlayerStore playerStore;
    private final SettingsStore settingsStore;

    @Autowired
    private Store() {
        this.characterStore = CharacterStore.getInstance();
        this.playerStore = PlayerStore.getInstance();
        this.settingsStore = SettingsStore.getInstance();
    }

    public CharacterStore getCharacterStore() {
        return characterStore;
    }

    public PlayerStore getPlayerStore() {
        return playerStore;
    }

    public SettingsStore getSettingsStore() {
        return settingsStore;
    }


}
