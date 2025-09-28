package org.poolen.frontend.util.services;

import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.persistence.StorePersistenceService;
import org.poolen.web.google.SheetsServiceManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javafx.stage.Window;

import java.util.UUID;
import java.util.function.Consumer;

@Service
@Lazy
public class UiPersistenceService {
    UiTaskExecutor uiTaskExecutor;
    StorePersistenceService storePersistenceService;

    public UiPersistenceService(UiTaskExecutor uiTaskExecutor, StorePersistenceService storePersistenceService) {
        this.uiTaskExecutor = uiTaskExecutor;
        this.storePersistenceService = storePersistenceService;
    }

    /**
     * A helper method to find all data, but designed to be called from
     * a background thread and provide progress updates.
     * @param progressUpdater The consumer to update the UI message.
     */
    public void findAllWithProgress(Consumer<String> progressUpdater) {
        progressUpdater.accept("Loading character data...");
        storePersistenceService.findCharacters();
        progressUpdater.accept("Loading player data...");
        storePersistenceService.findPlayers();
        progressUpdater.accept("Loading settings...");
        storePersistenceService.findSettings();
    }

    public void saveAll(Window owner, Runnable onDataChanged) {
        uiTaskExecutor.execute(
                owner,
                "Saving data...",
                (progressUpdater) -> {
                    progressUpdater.accept("Saving player data...");
                    storePersistenceService.saveAllPlayers();
                    progressUpdater.accept("Saving character data...");
                    storePersistenceService.saveAllCharacters();
                    progressUpdater.accept("Saving settings...");
                    storePersistenceService.saveAllSettings();
                    return "Data successfully saved!";
                },
                (successMessage) -> {
                    if (onDataChanged != null) onDataChanged.run();
                    System.out.println("Success: " + successMessage);
                },
                (error) -> {
                    System.err.println("Error: " + error.getMessage());
                }
        );
    }

    public void saveCharacters(Player player, Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Saving data...",
                (progressUpdater) -> {
                    progressUpdater.accept("Saving characters for %s...".formatted(player.getName()));
                    player.getCharacters().forEach(character ->
                            storePersistenceService.saveCharacter(character.getUuid()));
                    return "Data successfully saved!";
                },
                (successMessage) -> {
                    System.out.println("Success: " + successMessage);
                },
                (error) -> {
                    System.err.println("Error: " + error.getMessage());
                }
        );
    }
    public void deleteCharacter(Character character, Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Saving Character...",
                (progressUpdater) -> {
                    progressUpdater.accept("Saving %s...".formatted(character.getName()));
                    storePersistenceService.deleteCharacter(character.getUuid());
                    return "Data successfully saved!";
                },
                (successMessage) -> {
                    System.out.println("Success: " + successMessage);
                },
                (error) -> {
                    System.err.println("Error: " + error.getMessage());
                }
        );
    }

    public void savePlayer(Player player, Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Saving Player...",
                (progressUpdater) -> {
                    progressUpdater.accept("Saving %s...".formatted(player.getName()));
                    storePersistenceService.savePlayer(player.getUuid());
                    return "Data successfully saved!";
                },
                (successMessage) -> {
                    System.out.println("Success: " + successMessage);
                },
                (error) -> {
                    System.err.println("Error: " + error.getMessage());
                }
        );
    }
    public void deletePlayer(Player player, Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Deleting Player...",
                (progressUpdater) -> {
                    progressUpdater.accept("Saving %s...".formatted(player.getName()));
                    storePersistenceService.deleteCharacter(player.getUuid());
                    return "Data successfully saved!";
                },
                (successMessage) -> {
                    System.out.println("Success: " + successMessage);
                },
                (error) -> {
                    System.err.println("Error: " + error.getMessage());
                }
        );
    }

    public void saveSettings(Window owner) {
        uiTaskExecutor.execute(
                owner,
                "Saving settings...",
                (progressUpdater) -> {
                    storePersistenceService.saveAllSettings();
                    return "Settings successfully saved!";
                },
                (successMessage) -> {
                    System.out.println("Success: " + successMessage);
                }
        );
    }

    public void findAll(Window owner, Runnable onDataChanged) {
        uiTaskExecutor.execute(
                owner,
                "Loading data...",
                (progressUpdater) -> {
                    findAllWithProgress(progressUpdater); // Use our new helper!
                    return "Data successfully loaded!";
                },
                (successMessage) -> {
                    if (onDataChanged != null) onDataChanged.run();
                    System.out.println("Success: " + successMessage);
                },
                (error) -> {
                    System.err.println("Error: " + error.getMessage());
                }
        );
    }
}

