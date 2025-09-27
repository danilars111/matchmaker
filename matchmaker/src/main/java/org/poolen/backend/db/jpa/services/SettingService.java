package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Setting;
import org.poolen.backend.db.interfaces.ISettingService;
import org.poolen.backend.db.interfaces.ISettings;
import org.poolen.backend.db.jpa.entities.SettingEntity;
import org.poolen.backend.db.jpa.repository.SettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SettingService implements ISettingService {

    private final SettingRepository settingRepository;

    @Autowired
    public SettingService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Override
    @Transactional
    public Setting<?> save(Setting<?> domainObject) {
        SettingEntity entity = settingRepository.findByName(((Enum<?>) domainObject.getName()).name());
        if (entity == null) {
            entity = new SettingEntity();
        }
        updateEntity(entity, domainObject);
        SettingEntity savedEntity = settingRepository.save(entity);
        return toDomainObject(savedEntity);
    }

    @Override
    @Transactional
    public Set<Setting<?>> saveAll(Set<Setting<?>> settings) {
        if (settings == null || settings.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> settingNames = settings.stream()
                .map(s -> ((Enum<?>) s.getName()).name())
                .collect(Collectors.toSet());

        Map<String, SettingEntity> existingSettingsMap = settingRepository.findAllByNameIn(settingNames).stream()
                .collect(Collectors.toMap(SettingEntity::getName, Function.identity()));

        List<SettingEntity> entitiesToSave = settings.stream().map(setting -> {
            SettingEntity entity = existingSettingsMap.getOrDefault(((Enum<?>) setting.getName()).name(), new SettingEntity());
            updateEntity(entity, setting);
            return entity;
        }).collect(Collectors.toList());

        List<SettingEntity> savedEntities = settingRepository.saveAll(entitiesToSave);

        return savedEntities.stream()
                .map(this::toDomainObject)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Optional<Setting<?>> findByUuid(UUID uuid) {
        // Settings are identified by name, not UUID.
        throw new UnsupportedOperationException("Settings cannot be found by UUID.");
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Setting<?>> findAll() {
        return settingRepository.findAll().stream()
                .map(this::toDomainObject)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Setting<?> toDomainObject(SettingEntity entity) {
        if (entity == null) {
            return null;
        }

        String name = entity.getName();
        String value = entity.getSettingValue();
        String description = entity.getDescription();

        ISettings settingEnum = ISettings.find(name);
        if (settingEnum == null) {
            System.err.println("Unknown setting name from database: " + name);
            return null;
        }

        // --- Type Conversion Magic! ---
        if (settingEnum instanceof Settings.MatchmakerBonusSettings ||
                settingEnum instanceof Settings.MatchmakerMultiplierSettings ||
                settingEnum == Settings.PersistenceSettings.RECAP_DEADLINE) {
            try {
                return new Setting<>(settingEnum, description, Double.parseDouble(value));
            } catch (NumberFormatException e) {
                System.err.println("Could not parse double setting: " + name + " with value: " + value);
                return new Setting<>(settingEnum, description, 0.0); // Default value
            }
        }
        if (settingEnum instanceof Settings.MatchmakerPrioritySettings) {
            // Value is a string like "[GARNET, OPAL, AVENTURINE]"
            String listAsString = value.replace("[", "").replace("]", "");
            if (listAsString.isEmpty()) {
                return new Setting<>(settingEnum, description, new ArrayList<House>());
            }
            try {
                List<House> houseList = Arrays.stream(listAsString.split(",\\s*"))
                        .map(String::trim)
                        .map(House::valueOf)
                        .collect(Collectors.toList());
                return new Setting<>(settingEnum, description, houseList);
            } catch (Exception e) {
                System.err.println("Could not parse House list setting: " + name + " with value: " + value);
                return new Setting<>(settingEnum, description, new ArrayList<House>()); // Default value
            }
        }

        // For other PersistenceSettings, the value is already a String.
        // This is our default case.
        return new Setting<>(settingEnum, description, value);
    }

    @Override
    public void updateEntity(SettingEntity entity, Setting<?> domainObject) {
        if (entity == null || domainObject == null) {
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
            entity.setSettingValue("[" + listString + "]");
        } else {
            entity.setSettingValue(String.valueOf(domainObject.getSettingValue()));
        }
    }
}

