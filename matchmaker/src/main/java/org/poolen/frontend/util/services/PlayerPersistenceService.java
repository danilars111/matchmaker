package org.poolen.frontend.util.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.Store;
import org.poolen.util.AppDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlayerPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerPersistenceService.class);
    private final ObjectMapper objectMapper;
    private final Path savePath;
    private final ReentrantLock fileLock = new ReentrantLock();
    private final Store store;

    @Autowired
    public PlayerPersistenceService(Store store) {
        this.store = store;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Resolving the path exactly as requested
        Path appDataDir = AppDataHandler.getAppDataDirectory();
        if (appDataDir != null) {
            this.savePath = appDataDir.resolve("persistance/players");
        } else {
            this.savePath = Path.of("players_backup.json");
            logger.error("Could not resolve AppData directory. Falling back to local path: {}", this.savePath);
        }
    }

    /**
     * Saves the current list of players to the hard drive using lightweight DTOs.
     * <p>
     * We only save the UUID and their current state (attending/DMing).
     * </p>
     *
     */
    public void savePlayers() {
        List<Player> players = store.getPlayerStore().getAttendingPlayers().values().stream().toList();

        if (players.isEmpty()) {
            return;
        }


        // 1. Convert to DTOs immediately (Snapshot)
        Map<UUID, PlayerDto> playerMap = players.stream()
                .map(PlayerDto::new)
                .collect(Collectors.toMap(dto -> dto.uuid, Function.identity(), (existing, replacement) -> replacement));

        // 2. Thread-safe file writing
        fileLock.lock();
        try {
            if (savePath.getParent() != null) {
                Files.createDirectories(savePath.getParent());
            }

            if (playerMap.isEmpty()) {
                logger.debug("Player list is empty. Deleting save file if it exists.");
                deleteSaveFile();
                return;
            }

            logger.trace("Saving state for {} players to {}", playerMap.size(), savePath);
            objectMapper.writeValue(savePath.toFile(), playerMap);

        } catch (IOException e) {
            logger.error("Failed to save player states to disk!", e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Loads the player states from disk and updates the players in the Store.
     * * @return A List of Players that were successfully updated, or empty list if failed.
     */
    public List<Player> loadPlayers() {
        fileLock.lock();
        try {
            if (!hasSaveFile()) {
                return Collections.emptyList();
            }

            logger.debug("Loading player states from {}", savePath);
            // Read the DTOs
            Map<UUID, PlayerDto> playerDtoMap = objectMapper.readValue(savePath.toFile(), new TypeReference<Map<UUID, PlayerDto>>() {});

            // Update existing players in the store with the saved state
            return playerDtoMap.values().stream()
                    .map(this::updatePlayerState)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            logger.error("Failed to load player states from disk.", e);
            return Collections.emptyList();
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Finds the player in the store and updates their attending/dming status.
     */
    private Player updatePlayerState(PlayerDto dto) {
        // We grab the rest off the store, just as you asked, sweetie!
        Player player = store.getPlayerStore().getPlayerByUuid(dto.uuid);

        if (player != null) {
            player.isAttending(dto.attending);
            player.isDming(dto.dming);
            return player;
        } else {
            logger.warn("Found saved state for player {} but they do not exist in the Store. Skipping.", dto.uuid);
            return null;
        }
    }

    public boolean hasSaveFile() {
        fileLock.lock();
        try {
            return Files.exists(savePath) && Files.isRegularFile(savePath);
        } finally {
            fileLock.unlock();
        }
    }

    public void deleteSaveFile() {
        fileLock.lock();
        try {
            boolean deleted = Files.deleteIfExists(savePath);
            if (deleted) {
                logger.debug("Player recovery file deleted successfully.");
            }
        } catch (IOException e) {
            logger.warn("Failed to delete player recovery file at {}", savePath, e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Internal DTO class.
     * We strictly only save what is necessary to restore the session state.
     */
    private static class PlayerDto {
        public UUID uuid;
        public boolean attending;
        public boolean dming;

        // Default constructor for Jackson
        public PlayerDto() {}

        // Constructor from Player entity
        public PlayerDto(Player player) {
            this.uuid = player.getUuid();
            this.attending = player.isAttending();
            this.dming = player.isDming();
        }
    }
}