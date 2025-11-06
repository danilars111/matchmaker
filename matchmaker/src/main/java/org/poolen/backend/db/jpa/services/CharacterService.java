package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.IPlayerService;
import org.poolen.backend.db.interfaces.IService;
import org.poolen.backend.db.jpa.entities.CharacterEntity;
import org.poolen.backend.db.jpa.entities.PlayerEntity;
import org.poolen.backend.db.jpa.repository.CharacterRepository;
import org.poolen.backend.db.jpa.repository.PlayerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
public class CharacterService implements IService<Character, CharacterEntity> {

    private static final Logger logger = LoggerFactory.getLogger(CharacterService.class);

    private final CharacterRepository characterRepository;
    private final PlayerRepository playerRepository;
    private final IPlayerService playerService;

    @Autowired
    public CharacterService(CharacterRepository characterRepository, PlayerRepository playerRepository, @Lazy IPlayerService playerService) {
        this.characterRepository = characterRepository;
        this.playerRepository = playerRepository;
        this.playerService = playerService;
        logger.info("CharacterService initialised.");
    }

    @Override
    @Transactional
    public Character save(Character character) {
        if (character == null) {
            logger.warn("Save attempt failed: Provided character is null.");
            return null;
        }
        logger.info("Saving character '{}' (UUID: {}).", character.getName(), character.getUuid());

        CharacterEntity entity = characterRepository.findByUuid(character.getUuid());
        if (entity == null) {
            logger.debug("No existing entity found. Creating a new CharacterEntity for UUID: {}", character.getUuid());
            entity = new CharacterEntity();
        } else {
            logger.debug("Found existing CharacterEntity. Updating it for UUID: {}", character.getUuid());
        }

        try {
            updateEntity(entity, character);
            CharacterEntity savedEntity = characterRepository.save(entity);
            logger.info("Successfully saved character '{}' (UUID: {}).", savedEntity.getName(), savedEntity.getUuid());
            return toDomainObject(savedEntity);
        } catch (Exception e) {
            logger.error("Failed to save character with UUID: {}", character.getUuid(), e);
            throw e; // Re-throw to maintain transactional behaviour
        }
    }

    @Override
    @Transactional
    public Set<Character> saveAll(Set<Character> characters) {
        if (characters == null || characters.isEmpty()) {
            logger.warn("saveAll called with a null or empty set of characters. No action taken.");
            return new HashSet<>();
        }

        logger.info("Saving a batch of {} characters.", characters.size());

        Set<UUID> characterUuids = characters.stream()
                .map(Character::getUuid)
                .collect(Collectors.toSet());
        logger.trace("Extracting {} character UUIDs for batch save.", characterUuids.size());

        Map<UUID, CharacterEntity> existingCharsMap = characterRepository.findAllByUuidInWithDetails(characterUuids).stream()
                .collect(Collectors.toMap(CharacterEntity::getUuid, Function.identity()));
        logger.debug("Found {} existing character entities from the database.", existingCharsMap.size());

        Set<UUID> playerUuids = characters.stream()
                .map(c -> c.getPlayer().getUuid())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        logger.trace("Extracting {} unique player UUIDs from the character set.", playerUuids.size());

        Map<UUID, PlayerEntity> playersMap = playerRepository.findAllByUuidInWithDetails(playerUuids).stream()
                .collect(Collectors.toMap(PlayerEntity::getUuid, Function.identity()));
        logger.debug("Found {} associated player entities from the database.", playersMap.size());

        try {
            List<CharacterEntity> entitiesToSave = characters.stream().map(character -> {
                CharacterEntity entity = existingCharsMap.getOrDefault(character.getUuid(), new CharacterEntity());
                updateEntity(entity, character, playersMap); // Use the private worker
                return entity;
            }).collect(Collectors.toList());

            List<CharacterEntity> savedEntities = characterRepository.saveAll(entitiesToSave);
            logger.info("Successfully saved a batch of {} character entities.", savedEntities.size());

            return savedEntities.stream()
                    .map(this::toDomainObject)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Exception e) {
            logger.error("Failed during the 'saveAll' operation for characters.", e);
            throw e; // Re-throw to maintain transactional behaviour
        }
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<Character> findByUuid(UUID uuid) {
        logger.debug("Attempting to find character by UUID: {}", uuid);
        CharacterEntity entity = characterRepository.findByUuid(uuid);
        if (entity == null) {
            logger.warn("No character found with UUID: {}", uuid);
            return Optional.empty();
        }
        logger.debug("Successfully found character '{}' with UUID: {}", entity.getName(), uuid);
        return Optional.ofNullable(toDomainObject(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Character> findAll() {
        logger.info("Finding all characters with full details.");
        Set<Character> foundCharacters = characterRepository.findAllWithDetails().stream()
                .map(this::toDomainObject)
                .collect(Collectors.toCollection(HashSet::new));
        logger.info("Found {} characters in total.", foundCharacters.size());
        return foundCharacters;
    }

    @Override
    public Character toDomainObject(CharacterEntity entity) {
        if (entity == null) {
            logger.trace("toDomainObject called with null entity. Returning null.");
            return null;
        }
        logger.trace("Mapping CharacterEntity (UUID: {}) to domain object.", entity.getUuid());

        Character character = new Character(entity.getUuid(), entity.getName(), entity.getHouse());
        character.setMain(entity.isMain());
        character.setRetired(entity.isRetired());

        if (entity.getPlayer() != null) {
            logger.trace("... mapping shallow player (UUID: {}).", entity.getPlayer().getUuid());
            Player player = playerService.toDomainObjectShallow(entity.getPlayer());
            character.setPlayer(player);
        } else {
            logger.warn("CharacterEntity with UUID {} has a null player reference.", entity.getUuid());
        }

        return character;
    }

    @Override
    public void updateEntity(CharacterEntity entity, Character character) {
        logger.trace("Updating single CharacterEntity (UUID: {}) from domain object (UUID: {}).",
                entity != null ? entity.getUuid() : "null",
                character != null ? character.getUuid() : "null");
        if (entity == null || character == null) {
            logger.warn("UpdateEntity failed: entity or character is null.");
            return;
        }

        if (character.getPlayer() == null) {
            logger.error("Character with UUID {} has a null player. This is not allowed for saving.", character.getUuid());
            throw new IllegalArgumentException("A Character must have an associated Player to be saved.");
        }

        UUID playerUuid = character.getPlayer().getUuid();

        if (playerUuid == null) {
            logger.error("A Character must have an associated Player UUID to be saved. Character UUID: {}", character.getUuid());
            throw new IllegalArgumentException("A Character must have an associated Player UUID to be saved.");
        }

        PlayerEntity playerEntity = playerRepository.findByUuid(playerUuid);
        Map<UUID, PlayerEntity> playerMap = new HashMap<>();
        if(playerEntity != null) {
            playerMap.put(playerUuid, playerEntity);
        } else {
            logger.warn("Could not find PlayerEntity for UUID {} while updating single character {}.", playerUuid, character.getUuid());
        }

        updateEntity(entity, character, playerMap);
    }

    private void updateEntity(CharacterEntity entity, Character character, Map<UUID, PlayerEntity> playersMap) {
        logger.trace("... performing private entity update for character UUID: {}", character.getUuid());
        entity.setUuid(character.getUuid());
        entity.setName(character.getName());
        entity.setHouse(character.getHouse());
        entity.setMain(character.isMain());
        entity.setRetired(character.isRetired());

        if (character.getPlayer() == null) {
            logger.error("Character with UUID {} has a null player. This is not allowed for saving.", character.getUuid());
            throw new IllegalArgumentException("A Character must have an associated Player to be saved.");
        }

        UUID playerUuid = character.getPlayer().getUuid();

        if (playerUuid == null) {
            logger.error("A Character must have an associated Player UUID to be saved. Character UUID: {}", character.getUuid());
            throw new IllegalArgumentException("A Character must have an associated Player UUID to be saved.");
        }

        PlayerEntity playerEntity = playersMap.get(playerUuid);

        if (playerEntity == null) {
            logger.error("Attempted to save a character for a non-existent or non-pre-fetched player with UUID: {}", playerUuid);
            throw new IllegalStateException("Attempted to save a character for a non-existent player with UUID: " + playerUuid);
        }

        entity.setPlayer(playerEntity);
    }
}
