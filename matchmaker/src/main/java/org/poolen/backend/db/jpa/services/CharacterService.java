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

import java.util.Optional;
import java.util.UUID;

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
            entity.setUuid(character.getUuid());
        }

        updateEntity(entity, character);

        CharacterEntity savedEntity = characterRepository.save(entity);
        return toDomainObject(savedEntity);
    }

    @Override
    public Optional<Character> findByUuid(UUID uuid) {
        CharacterEntity entity = characterRepository.findByUuid(uuid);
        return Optional.ofNullable(toDomainObject(entity));
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

        entity.setName(character.getName());
        entity.setHouse(character.getHouse());
        entity.setMain(character.isMain());
        entity.setRetired(character.isRetired());

        UUID playerUuid = character.getPlayer().getUuid();

        if (playerUuid == null) {
            throw new IllegalArgumentException("A Character must have an associated Player UUID to be saved.");
        }

        PlayerEntity playerEntity = playerRepository.findByUuid(playerUuid);

        if (playerEntity == null) {
            throw new IllegalStateException("Attempted to save a character for a non-existent player with UUID: " + playerUuid);
        }

        entity.setPlayer(playerEntity);
    }
}

