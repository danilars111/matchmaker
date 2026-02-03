package org.poolen.frontend.util.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.factories.GroupFactory;
import org.poolen.backend.db.store.Store;
import org.poolen.util.AppDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(GroupPersistenceService.class);
    private final ObjectMapper objectMapper;
    private final Path savePath;
    private final ReentrantLock fileLock = new ReentrantLock();
    private final Store store;
    private final GroupFactory groupFactory;

    @Autowired
    public GroupPersistenceService(Store store, GroupFactory groupFactory) {
        this.store = store;
        this.groupFactory = groupFactory;

        // We set up the ObjectMapper to handle LocalDates and formatting prettily
        this.objectMapper = new ObjectMapper();
        // We removed findAndRegisterModules() because the dependency is missing.
        // We will handle dates manually in the DTO as Strings instead.
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Resolving the path exactly as requested
        Path appDataDir = AppDataHandler.getAppDataDirectory();
        if (appDataDir != null) {
            this.savePath = appDataDir.resolve("groups");
        } else {
            this.savePath = Path.of("groups_backup.json");
            logger.error("Could not resolve AppData directory. Falling back to local path: {}", this.savePath);
        }
    }

    /**
     * Saves the current list of groups to the hard drive using lightweight DTOs.
     * <p>
     * This creates a snapshot of the groups, converting Players to UUIDs,
     * ensuring we don't save redundant player data.
     * </p>
     *
     * @param groups The list of groups to save.
     */
    public void saveGroups(List<Group> groups) {
        if (groups == null) {
            return;
        }

        // 1. Convert to DTOs immediately (Snapshot)
        // We map to GroupDto to ensure we only save UUIDs and basic data.
        Map<UUID, GroupDto> groupMap = groups.stream()
                .map(GroupDto::new) // Convert Group -> GroupDto
                .collect(Collectors.toMap(dto -> dto.uuid, Function.identity(), (existing, replacement) -> replacement));

        // 2. Thread-safe file writing
        fileLock.lock();
        try {
            if (groupMap.isEmpty()) {
                logger.debug("Group list is empty. Deleting save file if it exists.");
                deleteSaveFile();
                return;
            }

            logger.debug("Saving {} groups to {}", groupMap.size(), savePath);
            objectMapper.writeValue(savePath.toFile(), groupMap);

        } catch (IOException e) {
            logger.error("Failed to save groups to disk! Recovery data might be lost.", e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Loads the groups from the hard drive and reconstructs them using the Store.
     * * @return A List of Groups, or an empty list if no file exists or loading fails.
     */
    public List<Group> loadGroups() {
        fileLock.lock();
        try {
            if (!hasSaveFile()) {
                return Collections.emptyList();
            }

            logger.debug("Loading groups from {}", savePath);
            // Read the DTOs first
            Map<UUID, GroupDto> groupDtoMap = objectMapper.readValue(savePath.toFile(), new TypeReference<Map<UUID, GroupDto>>() {});

            // Convert DTOs back to full Group objects
            return groupDtoMap.values().stream()
                    .map(this::reconstructGroup)
                    .filter(Objects::nonNull) // Just in case something went terribly wrong
                    .collect(Collectors.toList());

        } catch (IOException e) {
            logger.error("Failed to load groups from disk.", e);
            return Collections.emptyList();
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Reconstructs a full Group object from a DTO, resolving players via the Store.
     */
    private Group reconstructGroup(GroupDto dto) {
        // Resolve DM
        Player dm = null;
        if (dto.dmUuid != null) {
            dm = store.getPlayerStore().getPlayerByUuid(dto.dmUuid);
            // If DM isn't in store, dm remains null, which is fine
        }

        // Resolve Party Members
        List<Player> partyMembers = new ArrayList<>();
        if (dto.partyUuids != null) {
            for (UUID playerUuid : dto.partyUuids) {
                Player player = store.getPlayerStore().getPlayerByUuid(playerUuid);
                // "If the player does not exist in the store, we just skip adding that player"
                if (player != null) {
                    partyMembers.add(player);
                }
            }
        }

        // Ensure houses is not null for the factory
        List<House> houses = dto.houses != null ? dto.houses : new ArrayList<>();

        // Convert the string date back to LocalDate
        LocalDate groupDate = null;
        if (dto.date != null) {
            try {
                groupDate = LocalDate.parse(dto.date);
            } catch (Exception e) {
                logger.warn("Could not parse date '{}' for group {}. Using null.", dto.date, dto.uuid);
            }
        }

        // Use the Factory to create the group consistently
        try {
            return groupFactory.create(dto.uuid, dm, houses, groupDate, dto.location, partyMembers);
        } catch (IllegalArgumentException e) {
            logger.error("Failed to reconstruct group {}: {}", dto.uuid, e.getMessage());
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
                logger.debug("Recovery file deleted successfully.");
            }
        } catch (IOException e) {
            logger.warn("Failed to delete recovery file at {}", savePath, e);
        } finally {
            fileLock.unlock();
        }
    }

    public Path getSavePath() {
        return savePath;
    }

    /**
     * Internal DTO class to keep the JSON file clean and minimal.
     */
    private static class GroupDto {
        public UUID uuid;
        public UUID dmUuid;
        public List<UUID> partyUuids;
        public List<House> houses;
        public String date; // Changed to String to avoid Jackson JSR310 dependency issues
        public String location;

        // Default constructor for Jackson
        public GroupDto() {}

        // Constructor from Group entity
        public GroupDto(Group group) {
            this.uuid = group.getUuid();
            this.dmUuid = group.getDungeonMaster() != null ? group.getDungeonMaster().getUuid() : null;
            // Collect just the UUIDs from the party map
            this.partyUuids = group.getParty() != null
                    ? new ArrayList<>(group.getParty().keySet())
                    : new ArrayList<>();
            this.houses = group.getHouses();
            // Manually convert date to string (ISO-8601 format: YYYY-MM-DD)
            this.date = group.getDate() != null ? group.getDate().toString() : null;
            this.location = group.getLocation();
        }
    }
}
