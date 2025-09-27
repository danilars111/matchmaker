package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.IPlayerService;
import org.poolen.backend.db.interfaces.IService;
import org.poolen.backend.db.jpa.entities.CharacterEntity;
import org.poolen.backend.db.jpa.entities.PlayerEntity;
import org.poolen.backend.db.jpa.repository.CharacterRepository;
import org.poolen.backend.db.jpa.repository.PlayerRepository;
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

    private final CharacterRepository characterRepository;
    private final PlayerRepository playerRepository;
    private final IPlayerService playerService;

    @Autowired
    public CharacterService(CharacterRepository characterRepository, PlayerRepository playerRepository, @Lazy IPlayerService playerService) {
        this.characterRepository = characterRepository;
        this.playerRepository = playerRepository;
        this.playerService = playerService;
    }

    @Override
    @Transactional
    public Character save(Character character) {
        CharacterEntity entity = characterRepository.findByUuid(character.getUuid());
        if (entity == null) {
            entity = new CharacterEntity();
        }

        updateEntity(entity, character);

        CharacterEntity savedEntity = characterRepository.save(entity);
        return toDomainObject(savedEntity);
    }

    @Override
    @Transactional
    public Set<Character> saveAll(Set<Character> characters) {
        if (characters == null || characters.isEmpty()) {
            return new HashSet<>();
        }

        Set<UUID> characterUuids = characters.stream()
                .map(Character::getUuid)
                .collect(Collectors.toSet());

        Map<UUID, CharacterEntity> existingCharsMap = characterRepository.findAllByUuidInWithDetails(characterUuids).stream()
                .collect(Collectors.toMap(CharacterEntity::getUuid, Function.identity()));

        Set<UUID> playerUuids = characters.stream()
                .map(c -> c.getPlayer().getUuid())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, PlayerEntity> playersMap = playerRepository.findAllByUuidInWithDetails(playerUuids).stream()
                .collect(Collectors.toMap(PlayerEntity::getUuid, Function.identity()));

        List<CharacterEntity> entitiesToSave = characters.stream().map(character -> {
            CharacterEntity entity = existingCharsMap.getOrDefault(character.getUuid(), new CharacterEntity());
            updateEntity(entity, character, playersMap); // Use the private worker
            return entity;
        }).collect(Collectors.toList());

        List<CharacterEntity> savedEntities = characterRepository.saveAll(entitiesToSave);

        return savedEntities.stream()
                .map(this::toDomainObject)
                .collect(Collectors.toCollection(HashSet::new));
    }


    @Override
    public Optional<Character> findByUuid(UUID uuid) {
        CharacterEntity entity = characterRepository.findByUuid(uuid);
        return Optional.ofNullable(toDomainObject(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Character> findAll() {
        return characterRepository.findAllWithDetails().stream()
                .map(this::toDomainObject)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Character toDomainObject(CharacterEntity entity) {
        if (entity == null) {
            return null;
        }

        Character character = new Character(entity.getUuid(), entity.getName(), entity.getHouse());
        character.setMain(entity.isMain());
        character.setRetired(entity.isRetired());

        if (entity.getPlayer() != null) {
            Player player = playerService.toDomainObjectShallow(entity.getPlayer());
            character.setPlayer(player);
        }

        return character;
    }

    @Override
    public void updateEntity(CharacterEntity entity, Character character) {
        if (entity == null || character == null) {
            return;
        }

        UUID playerUuid = character.getPlayer().getUuid();

        if (playerUuid == null) {
            throw new IllegalArgumentException("A Character must have an associated Player UUID to be saved.");
        }

        PlayerEntity playerEntity = playerRepository.findByUuid(playerUuid);
        Map<UUID, PlayerEntity> playerMap = new HashMap<>();
        if(playerEntity != null) {
            playerMap.put(playerUuid, playerEntity);
        }

        updateEntity(entity, character, playerMap);
    }

    private void updateEntity(CharacterEntity entity, Character character, Map<UUID, PlayerEntity> playersMap) {
        entity.setUuid(character.getUuid());
        entity.setName(character.getName());
        entity.setHouse(character.getHouse());
        entity.setMain(character.isMain());
        entity.setRetired(character.isRetired());

        UUID playerUuid = character.getPlayer().getUuid();

        if (playerUuid == null) {
            throw new IllegalArgumentException("A Character must have an associated Player UUID to be saved.");
        }

        PlayerEntity playerEntity = playersMap.get(playerUuid);

        if (playerEntity == null) {
            throw new IllegalStateException("Attempted to save a character for a non-existent player with UUID: " + playerUuid);
        }

        entity.setPlayer(playerEntity);
    }
}

