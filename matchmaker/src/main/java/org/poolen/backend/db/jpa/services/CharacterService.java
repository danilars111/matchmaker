package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.IService;
import org.poolen.backend.db.jpa.entities.CharacterEntity;
import org.poolen.backend.db.jpa.entities.PlayerEntity;
import org.poolen.backend.db.jpa.repository.CharacterRepository;
import org.poolen.backend.db.jpa.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class CharacterService implements IService<Character, CharacterEntity> {

    private final CharacterRepository characterRepository;
    private final PlayerRepository playerRepository;
    // We inject PlayerService to handle the translation of Player objects.
    private final PlayerService playerService;
    @Autowired
    public CharacterService(CharacterRepository characterRepository, PlayerRepository playerRepository, PlayerService playerService) {
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

        // Translate the associated PlayerEntity back to a full Player domain object.
        if (entity.getPlayer() != null) {
            Player player = playerService.toDomainObject(entity.getPlayer());
            character.setPlayer(player);
        }

        return character;
    }

    @Override
    public void updateEntity(CharacterEntity entity, Character character) {
        if (entity == null || character == null) {
            return;
        }

        // Update simple fields
        entity.setName(character.getName());
        entity.setHouse(character.getHouse());
        entity.setMain(character.isMain());
        entity.setRetired(character.isRetired());

        // Find the associated PlayerEntity and link it.
        if (character.getPlayer() != null) {
            PlayerEntity playerEntity = playerRepository.findByUuid(character.getPlayer());
            entity.setPlayer(playerEntity);
        } else {
            entity.setPlayer(null);
        }
    }
}
