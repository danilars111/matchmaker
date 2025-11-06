package org.poolen.frontend.util.services;

import javafx.stage.Window;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.persistence.StorePersistenceService;
import org.poolen.frontend.util.interfaces.UiUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class UiPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(UiPersistenceService.class);

    private final UiTaskExecutor uiTaskExecutor;
    private final StorePersistenceService storePersistenceService;

    @Autowired
    public UiPersistenceService(UiTaskExecutor uiTaskExecutor, StorePersistenceService storePersistenceService) {
        this.uiTaskExecutor = uiTaskExecutor;
        this.storePersistenceService = storePersistenceService;
        logger.info("UiPersistenceService initialised.");
    }

    /**
     * A helper method to find all data, but designed to be called from
     * a background thread and provide progress updates using the new UiUpdater.
     * @param updater The updater to update the UI message.
     */
    public void findAllWithProgress(UiUpdater updater) {
        logger.info("Finding all data with progress updates...");
        updater.updateStatus("Loading character data...");
        storePersistenceService.findCharacters();
        updater.updateStatus("Loading player data...");
        storePersistenceService.findPlayers();
        updater.updateStatus("Loading settings...");
        storePersistenceService.findSettings();
        logger.info("Finished finding all data.");
    }

    public void saveAllWithProgress(UiUpdater updater) {
        logger.info("Saving all data with progress updates...");
        updater.updateStatus("Saving player data...");
        storePersistenceService.saveAllPlayers();
        updater.updateStatus("Saving character data...");
        storePersistenceService.saveAllCharacters();
        updater.updateStatus("Saving settings...");
        storePersistenceService.saveAllSettings();
        logger.info("Finished saving all data.");
    }

    public void saveAll(Window owner, Runnable onDataChanged) {
        logger.info("Executing 'Save All' task via UiTaskExecutor.");
        uiTaskExecutor.execute(
                owner,
                "Saving data...",
                "Data successfully saved!",
                (updater) -> {
                    logger.debug("Background 'Save All' task started.");
                    saveAllWithProgress(updater); // Use our new helper!
                    logger.debug("Background 'Save All' task finished.");
                    return "unused"; // Result is handled by the success message
                },
                (result) -> {
                    if (onDataChanged != null) onDataChanged.run();
                },
                (error) -> {
                    // Default error handling is in UiTaskExecutor, add specific logic here if needed.
                    logger.error("Error during 'Save All' task execution.", error);
                }
        );
    }

    public void saveCharacters(Player player, Window owner) {
        logger.info("Executing 'Save Characters' task for player '{}' via UiTaskExecutor.", player.getName());
        uiTaskExecutor.execute(
                owner,
                "Saving data...",
                "Data successfully saved!",
                (updater) -> {
                    logger.debug("Background 'Save Characters' task started for player: {}", player.getName());
                    updater.updateStatus("Saving characters for %s...".formatted(player.getName()));
                    player.getCharacters().forEach(character ->
                            storePersistenceService.saveCharacter(character.getUuid()));
                    logger.debug("Background 'Save Characters' task finished.");
                    return "unused";
                },
                (result) -> {}, // No action needed on success
                (error) -> {
                    logger.error("Error during 'Save Characters' task for player '{}'.", player.getName(), error);
                }
        );
    }

    public void deleteCharacter(Character character, Window owner) {
        logger.info("Executing 'Delete Character' task for character '{}' via UiTaskExecutor.", character.getName());
        uiTaskExecutor.execute(
                owner,
                "Deleting Character...",
                "Character deleted!",
                (updater) -> {
                    logger.debug("Background 'Delete Character' task started for character: {}", character.getName());
                    updater.updateStatus("Deleting %s...".formatted(character.getName()));
                    storePersistenceService.deleteCharacter(character.getUuid());
                    logger.debug("Background 'Delete Character' task finished.");
                    return "unused";
                },
                (result) -> {},
                (error) -> {
                    logger.error("Error during 'Delete Character' task for character '{}'.", character.getName(), error);
                }
        );
    }

    public void savePlayer(Player player, Window owner) {
        logger.info("Executing 'Save Player' task for player '{}' via UiTaskExecutor.", player.getName());
        uiTaskExecutor.execute(
                owner,
                "Saving Player...",
                "Player saved!",
                (updater) -> {
                    logger.debug("Background 'Save Player' task started for player: {}", player.getName());
                    updater.updateStatus("Saving %s...".formatted(player.getName()));
                    storePersistenceService.savePlayer(player.getUuid());
                    logger.debug("Background 'Save Player' task finished.");
                    return "unused";
                },
                (result) -> {},
                (error) -> {
                    logger.error("Error during 'Save Player' task for player '{}'.", player.getName(), error);
                }
        );
    }

    public void deletePlayer(Player player, Window owner) {
        logger.info("Executing 'Delete Player' task for player '{}' via UiTaskExecutor.", player.getName());
        uiTaskExecutor.execute(
                owner,
                "Deleting Player...",
                "Player deleted!",
                (updater) -> {
                    logger.debug("Background 'Delete Player' task started for player: {}", player.getName());
                    updater.updateStatus("Deleting %s...".formatted(player.getName()));
                    storePersistenceService.deletePlayer(player.getUuid());
                    logger.debug("Background 'Delete Player' task finished.");
                    return "unused";
                },
                (result) -> {},
                (error) -> {
                    logger.error("Error during 'Delete Player' task for player '{}'.", player.getName(), error);
                }
        );
    }

    public void saveSettings(Window owner) {
        logger.info("Executing 'Save Settings' task via UiTaskExecutor.");
        uiTaskExecutor.execute(
                owner,
                "Saving settings...",
                "Settings saved!",
                (updater) -> {
                    logger.debug("Background 'Save Settings' task started.");
                    storePersistenceService.saveAllSettings();
                    logger.debug("Background 'Save Settings' task finished.");
                    return "unused";
                },
                (result) -> {},
                (error) -> {
                    logger.error("Error during 'Save Settings' task execution.", error);
                }
        );
    }

    public void findAll(Window owner, Runnable onDataChanged) {
        logger.info("Executing 'Find All' task via UiTaskExecutor.");
        uiTaskExecutor.execute(
                owner,
                "Loading data...",
                "Data loaded!",
                (updater) -> {
                    logger.debug("Background 'Find All' task started.");
                    findAllWithProgress(updater); // Use our new helper!
                    logger.debug("Background 'Find All' task finished.");
                    return "unused";
                },
                (result) -> {
                    if (onDataChanged != null) onDataChanged.run();
                },
                (error) -> {
                    logger.error("Error during 'Find All' task execution.", error);
                }
        );
    }
}
