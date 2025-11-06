package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Setting;
import org.poolen.backend.db.interfaces.ISettingService;
import org.poolen.backend.db.interfaces.ISettings;
import org.poolen.backend.db.jpa.entities.SettingEntity;
import org.poolen.backend.db.jpa.repository.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SettingService implements ISettingService {

    private static final Logger logger = LoggerFactory.getLogger(SettingService.class);

    private final SettingRepository settingRepository;

    @Autowired
    public SettingService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
        logger.info("SettingService initialised.");
    }

    @Override
    @Transactional
    public Setting<?> save(Setting<?> domainObject) {
        if (domainObject == null) {
            logger.warn("Save attempt failed: Provided setting is null.");
            return null;
        }
        String settingName = ((Enum<?>) domainObject.getName()).name();
        logger.info("Saving setting '{}'.", settingName);

        SettingEntity entity = settingRepository.findByName(settingName);
        if (entity == null) {
            logger.debug("No existing entity found. Creating a new SettingEntity for: {}", settingName);
            entity = new SettingEntity();
        } else {
            logger.debug("Found existing SettingEntity. Updating it for: {}", settingName);
        }

        try {
            updateEntity(entity, domainObject);
            SettingEntity savedEntity = settingRepository.save(entity);
            logger.info("Successfully saved setting '{}'.", savedEntity.getName());
            return toDomainObject(savedEntity);
        } catch (Exception e) {
            logger.error("Failed to save setting: {}", settingName, e);
            throw e; // Re-throw to maintain transactional behaviour
        }
    }

    @Override
    @Transactional
    public Set<Setting<?>> saveAll(Set<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            logger.warn("saveAll called with a null or empty set of settings. No action taken.");
            return new HashSet<>();
        }
        logger.info("Saving a batch of {} settings.", settings.size());

        Set<String> settingNames = settings.stream()
                .map(s -> ((Enum<?>) s.getName()).name())
                .collect(Collectors.toSet());
        logger.trace("Extracting {} setting names for batch save.", settingNames.size());

        Map<String, SettingEntity> existingSettingsMap = settingRepository.findAllByNameIn(settingNames).stream()
                .collect(Collectors.toMap(SettingEntity::getName, Function.identity()));
        logger.debug("Found {} existing setting entities from the database.", existingSettingsMap.size());

        try {
            List<SettingEntity> entitiesToSave = settings.stream().map(setting -> {
                SettingEntity entity = existingSettingsMap.getOrDefault(((Enum<?>) setting.getName()).name(), new SettingEntity());
                updateEntity(entity, setting);
                return entity;
            }).collect(Collectors.toList());

            List<SettingEntity> savedEntities = settingRepository.saveAll(entitiesToSave);
            logger.info("Successfully saved a batch of {} setting entities.", savedEntities.size());

            return savedEntities.stream()
                    .map(this::toDomainObject)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Exception e) {
            logger.error("Failed during the 'saveAll' operation for settings.", e);
            throw e; // Re-throw to maintain transactional behaviour
        }
    }

    @Override
    public Optional<Setting<?>> findByUuid(UUID uuid) {
        logger.warn("Unsupported operation: findByUuid called on SettingService. Settings are found by name.");
        // Settings are identified by name, not UUID.
        throw new UnsupportedOperationException("Settings cannot be found by UUID.");
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Setting<?>> findAll() {
        logger.info("Finding all settings.");
        Set<Setting<?>> foundSettings = settingRepository.findAll().stream()
                .map(this::toDomainObject)
                .filter(Objects::nonNull) // Filter out any settings we couldn't parse
                .collect(Collectors.toCollection(HashSet::new));
        logger.info("Found and mapped {} settings in total.", foundSettings.size());
        return foundSettings;
    }

    @Override
    public Setting<?> toDomainObject(SettingEntity entity) {
        if (entity == null) {
            logger.trace("toDomainObject called with null entity. Returning null.");
            return null;
        }
        logger.trace("Mapping SettingEntity (Name: {}) to domain object.", entity.getName());

        String name = entity.getName();
        String value = entity.getSettingValue();
        String description = entity.getDescription();

        ISettings settingEnum = ISettings.find(name);
        if (settingEnum == null) {
            logger.warn("Unknown setting name from database: {}. This setting will be skipped.", name);
            return null;
        }

        // --- Type Conversion Magic! ---
        if (settingEnum instanceof Settings.MatchmakerBonusSettings ||
                settingEnum instanceof Settings.MatchmakerMultiplierSettings ||
                settingEnum == Settings.PersistenceSettings.RECAP_DEADLINE) {
            try {
                // All numeric settings are stored as Double for consistency
                return new Setting<>(settingEnum, description, Double.parseDouble(value));
            } catch (NumberFormatException e) {
                logger.warn("Could not parse double setting: {} with value: {}. Using default.", name, value, e);
                return new Setting<>(settingEnum, description, 0.0); // Default value
            }
        }
        if (settingEnum instanceof Settings.MatchmakerPrioritySettings) {
            // Value is a string like "[GARNET, OPAL, AVENTURINE]"
            String listAsString = value.replace("[", "").replace("]", "");
            if (listAsString.isEmpty()) {
                logger.debug("Setting '{}' is an empty list.", name);
                return new Setting<>(settingEnum, description, new ArrayList<House>());
            }
            try {
                List<House> houseList = Arrays.stream(listAsString.split(",\\s*"))
                        .map(String::trim)
                        .map(House::valueOf)
                        .collect(Collectors.toList());
                return new Setting<>(settingEnum, description, houseList);
            } catch (Exception e) {
                logger.warn("Could not parse House list setting: {} with value: {}. Using default.", name, value, e);
                return new Setting<>(settingEnum, description, new ArrayList<House>()); // Default value
            }
        }

        // For other PersistenceSettings, the value is already a String.
        // This is our default case.
        logger.trace("Mapping setting '{}' as a simple String value.", name);
        return new Setting<>(settingEnum, description, value);
    }

    @Override
    public void updateEntity(SettingEntity entity, Setting<?> domainObject) {
        logger.trace("Updating single SettingEntity (Name: {}) from domain object (Name: {}).",
                entity != null ? entity.getName() : "null",
                domainObject != null ? ((Enum<?>) domainObject.getName()).name() : "null");
        if (entity == null || domainObject == null) {
            logger.warn("UpdateEntity failed: entity or domainObject is null.");
            return;
        }
        entity.setName(((Enum<?>) domainObject.getName()).name());
        entity.setDescription(domainObject.getDescription());
        // Convert any value type to its String representation for storage.
        if (domainObject.getSettingValue() instanceof List) {
            // Custom string representation for lists to avoid default toString() issues
            List<?> list = (List<?>) domainObject.getSettingValue();
            String listString = list.stream().map(item -> {
                if(item instanceof Enum) {
                    return ((Enum<?>)item).name();
                }
                return String.valueOf(item);
            }).collect(Collectors.joining(", "));
            String finalValue = "[" + listString + "]";
            entity.setSettingValue(finalValue);
            logger.trace("... converting List to String value: {}", finalValue);
        } else {
            String finalValue = String.valueOf(domainObject.getSettingValue());
            entity.setSettingValue(finalValue);
            logger.trace("... converting value to String: {}", finalValue);
        }
    }
}
