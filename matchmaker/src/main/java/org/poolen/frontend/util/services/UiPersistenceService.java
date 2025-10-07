package org.poolen.frontend.util.services;

import javafx.stage.Window;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.persistence.StorePersistenceService;
import org.poolen.frontend.util.interfaces.UiUpdater;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class UiPersistenceService {
    private final UiTaskExecutor uiTaskExecutor;
    private final StorePersistenceService storePersistenceService;

    @Autowired
    public UiPersistenceService(UiTaskExecutor uiTaskExecutor, StorePersistenceService storePersistenceService) {
        this.uiTaskExecutor = uiTaskExecutor;
        this.storePersistenceService = storePersistenceService;
    }

    /**
     * A helper method to find all data, but designed to be called from
     * a background thread and provide progress updates using the new UiUpdater.
     * @param updater The updater to update the UI message.
     */
    public void findAllWithProgress(UiUpdater updater) {
        updater.updateStatus("Loading character data...");
        storePersistenceService.findCharacters();
        updater.updateStatus("Loading player data...");
        storePersistenceService.findPlayers();
        updater.updateStatus("Loading settings...");
        storePersistenceService.findSettings();
    }

    public void saveAll(Window owner, Runnable onDataChanged) {
        uiTaskExecutor.execute(
                owner,
                "Saving data...",
                "Data successfully saved!",
                (updater) -> {
                    updater.updateStatus("Saving player data...");
                    storePersistenceService.saveAllPlayers();
                    updater.updateStatus("Saving character data...");
                    storePersistenceService.saveAllCharacters();
                    updater.updateStatus("Saving settings...");
                    storePersistenceService.saveAllSettings();
                    return "unused"; // Result is handled by the success message
                },
                (result) -> {
                    if (onDataChanged != null) onDataChanged.run();
                }
        );
    }

    public void saveCharacters(Player player, Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Saving data...",
                "Data successfully saved!",
                (updater) -> {
                    updater.updateStatus("Saving characters for %s...".formatted(player.getName()));
                    player.getCharacters().forEach(character ->
                            storePersistenceService.saveCharacter(character.getUuid()));
                    return "unused";
                },
                (result) -> {} // No action needed on success
        );
    }

    public void deleteCharacter(Character character, Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Deleting Character...",
                "Character deleted!",
                (updater) -> {
                    updater.updateStatus("Deleting %s...".formatted(character.getName()));
                    storePersistenceService.deleteCharacter(character.getUuid());
                    return "unused";
                },
                (result) -> {}
        );
    }

    public void savePlayer(Player player, Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Saving Player...",
                "Player saved!",
                (updater) -> {
                    updater.updateStatus("Saving %s...".formatted(player.getName()));
                    storePersistenceService.savePlayer(player.getUuid());
                    return "unused";
                },
                (result) -> {}
        );
    }

    public void deletePlayer(Player player, Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Deleting Player...",
                "Player deleted!",
                (updater) -> {
                    updater.updateStatus("Deleting %s...".formatted(player.getName()));
                    storePersistenceService.deletePlayer(player.getUuid());
                    return "unused";
                },
                (result) -> {}
        );
    }

    public void saveSettings(Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Saving settings...",
                "Settings saved!",
                (updater) -> {
                    storePersistenceService.saveAllSettings();
                    return "unused";
                },
                (result) -> {}
        );
    }

    public void findAll(Window owner, Runnable onDataChanged) {
        uiTaskExecutor.execute(
                owner,
                "Loading data...",
                "Data loaded!",
                (updater) -> {
                    findAllWithProgress(updater); // Use our new helper!
                    return "unused";
                },
                (result) -> {
                    if (onDataChanged != null) onDataChanged.run();
                }
        );
    }
}

