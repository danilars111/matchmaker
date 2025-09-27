package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.IPlayerService;
import org.poolen.backend.db.jpa.entities.CharacterEntity;
import org.poolen.backend.db.jpa.entities.PlayLogEntity;
import org.poolen.backend.db.jpa.entities.PlayerEntity;
import org.poolen.backend.db.jpa.repository.PlayLogRepository;
import org.poolen.backend.db.jpa.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlayerService implements IPlayerService {

    private final PlayerRepository playerRepository;
    private final PlayLogRepository playLogRepository;
    private final CharacterService characterService;

    @Autowired
    public PlayerService(PlayerRepository playerRepository, PlayLogRepository playLogRepository, @Lazy CharacterService characterService) {
        this.playerRepository = playerRepository;
        this.playLogRepository = playLogRepository;
        this.characterService = characterService;
    }

    @Override
    @Transactional
    public Player save(Player player) {
        PlayerEntity entity = playerRepository.findByUuid(player.getUuid());
        if (entity == null) {
            entity = new PlayerEntity();
            entity.setUuid(player.getUuid());
        }

        updateEntity(entity, player);
        PlayerEntity savedPlayer = playerRepository.save(entity);

        if (player.getCharacters() != null) {
            Map<UUID, CharacterEntity> existingChars = savedPlayer.getCharacters().stream()
                    .collect(Collectors.toMap(CharacterEntity::getUuid, c -> c));
            Set<CharacterEntity> updatedChars = new java.util.HashSet<>();

            for (Character character : player.getCharacters()) {
                CharacterEntity charEntity = existingChars.getOrDefault(character.getUuid(), new CharacterEntity());

                charEntity.setUuid(character.getUuid());
                charEntity.setName(character.getName());
                charEntity.setHouse(character.getHouse());
                charEntity.setMain(character.isMain());
                charEntity.setRetired(character.isRetired());
                charEntity.setPlayer(savedPlayer);
                updatedChars.add(charEntity);
            }

            savedPlayer.getCharacters().clear();
            savedPlayer.getCharacters().addAll(updatedChars);
        } else {
            savedPlayer.getCharacters().clear();
        }

        savedPlayer.getPlayerLog().clear();
        if (player.getPlayerLog() != null) {
            for (Map.Entry<UUID, java.time.LocalDate> entry : player.getPlayerLog().entrySet()) {
                PlayerEntity playedWithEntity = playerRepository.findByUuid(entry.getKey());
                if (playedWithEntity != null) {
                    PlayLogEntity logEntity = new PlayLogEntity(savedPlayer, playedWithEntity, entry.getValue());
                    savedPlayer.getPlayerLog().add(logEntity);
                }
            }
        }

        PlayerEntity finalEntity = playerRepository.save(savedPlayer);
        return toDomainObject(finalEntity);
    }

    @Override
    public Optional<Player> findByUuid(UUID uuid) {
        PlayerEntity entity = playerRepository.findByUuid(uuid);
        return Optional.ofNullable(toDomainObject(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Player> findAll() {
        return playerRepository.findAllWithDetails().stream()
                .map(this::toDomainObject)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Player toDomainObject(PlayerEntity entity) {
        if (entity == null) {
            return null;
        }
        Player player = toDomainObjectShallow(entity);

        if (player != null) {
            player.setCharacters(
                    entity.getCharacters().stream()
                            .map(characterService::toDomainObject) // Recursive call
                            .collect(Collectors.toCollection(HashSet::new))
            );
        }
        return player;
    }

    @Override
    public Player toDomainObjectShallow(PlayerEntity entity) {
        if (entity == null) {
            return null;
        }

        Player player = new Player(entity.getUuid(), entity.getName(), entity.isDungeonMaster());
        player.setLastSeen(entity.getLastSeen());

        player.setBlacklist(entity.getBlacklist().stream().map(PlayerEntity::getUuid).collect(Collectors.toCollection(HashSet::new)));
        player.setBuddylist(entity.getBuddylist().stream().map(PlayerEntity::getUuid).collect(Collectors.toCollection(HashSet::new)));
        player.setDmBlacklist(entity.getDmBlacklist().stream().map(PlayerEntity::getUuid).collect(Collectors.toCollection(HashSet::new)));

        Map<UUID, java.time.LocalDate> playerLog = entity.getPlayerLog().stream()
                .collect(Collectors.toMap(
                        logEntry -> logEntry.getPlayedWith().getUuid(),
                        PlayLogEntity::getLastPlayedDate,
                        (date1, date2) -> date1.isAfter(date2) ? date1 : date2
                ));
        player.setPlayerLog(playerLog);

        return player;
    }

    @Override
    public void updateEntity(PlayerEntity entity, Player player) {
        if (entity == null || player == null) {
            return;
        }

        entity.setName(player.getName());
        entity.setDungeonMaster(player.isDungeonMaster());
        entity.setLastSeen(player.getLastSeen());

        updatePlayerSet(player.getBlacklist(), entity.getBlacklist());
        updatePlayerSet(player.getBuddylist(), entity.getBuddylist());
        updatePlayerSet(player.getDmBlacklist(), entity.getDmBlacklist());
    }

    private void updatePlayerSet(Set<UUID> uuidSet, Set<PlayerEntity> entitySet) {
        entitySet.clear();
        if (uuidSet != null) {
            uuidSet.stream()
                    .map(playerRepository::findByUuid)
                    .filter(Objects::nonNull)
                    .forEach(entitySet::add);
        }
    }
}

