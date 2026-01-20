package org.poolen.backend.db.persistence;

import org.poolen.backend.db.jpa.services.CharacterService;
import org.poolen.backend.db.jpa.services.PlayerService;
import org.poolen.backend.db.jpa.services.SettingService;
import org.poolen.backend.db.store.Store;
import org.poolen.frontend.util.interfaces.UiUpdater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StorePersistenceService {

    private final Store store;
    private final CharacterService characterService;
    private final PlayerService playerService;
    private final SettingService settingsService;

    @Autowired
    public StorePersistenceService(Store store, CharacterService characterService, PlayerService playerService, SettingService settingsService) {
        this.store = store;
        this.characterService = characterService;
        this.playerService = playerService;
        this.settingsService = settingsService;
    }
    public void saveAll() {
        saveAllSettings();
        saveAllPlayers();
        saveAllCharacters();
    }

    public void findAll() {
        store.getCharacterStore().clear();
        store.getPlayerStore().clear();
        store.getSettingsStore().clear();
        findSettings();
        findPlayers();
        findCharacters();

    }

    public void findSettings() {
        store.getSettingsStore().init(settingsService.findAll());
    }

    public void saveAllSettings() {
        settingsService.saveAll(store.getSettingsStore().getSettings());
    }

    public void findCharacters() {
        store.getCharacterStore().init(characterService.findAll());
    }
    public void saveCharacter(UUID uuid) {
        characterService.save(store.getCharacterStore().getCharacterByUuid(uuid));
    }
    public void saveAllCharacters() {
        characterService.saveAll(store.getCharacterStore().getAllCharacters());
    }

    public void deleteCharacter(UUID uuid) {

    }

    public void findPlayers() {
        store.getPlayerStore().init(playerService.findAll());
    }
    public void savePlayer(UUID uuid) {
        playerService.save(store.getPlayerStore().getPlayerByUuid(uuid));
    }
    public void saveAllPlayers() {
        playerService.saveAll(store.getPlayerStore().getAllPlayers());
    }


    public void deletePlayer(UUID uuid) {

    }
}
