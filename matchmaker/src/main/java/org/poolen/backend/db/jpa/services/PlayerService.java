package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.IService;
import org.poolen.backend.db.jpa.entities.PlayLogEntity;
import org.poolen.backend.db.jpa.entities.PlayerEntity;
import org.poolen.backend.db.jpa.repository.PlayLogRepository;
import org.poolen.backend.db.jpa.repository.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlayerService implements IService<Player, PlayerEntity> {

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

        PlayerEntity savedEntity = playerRepository.save(entity);
        return toDomainObject(savedEntity);
    }

    @Override
    public Optional<Player> findByUuid(UUID uuid) {
        PlayerEntity entity = playerRepository.findByUuid(uuid);
        return Optional.ofNullable(toDomainObject(entity));
    }

    @Override
    public Player toDomainObject(PlayerEntity entity) {
        if (entity == null) {
            return null;
        }

        Player player = new Player(entity.getUuid(), entity.getName(), entity.isDungeonMaster());
        player.setLastSeen(entity.getLastSeen());

        player.setBlacklist(entity.getBlacklist().stream().map(PlayerEntity::getUuid).collect(Collectors.toSet()));
        player.setBuddylist(entity.getBuddylist().stream().map(PlayerEntity::getUuid).collect(Collectors.toSet()));
        player.setDmBlacklist(entity.getDmBlacklist().stream().map(PlayerEntity::getUuid).collect(Collectors.toSet()));

        // Translate the CharacterEntity set to a Character set
        player.setCharacters(
                entity.getCharacters().stream()
                        .map(characterService::toDomainObject)
                        .collect(Collectors.toSet())
        );

        // Translate the PlayLogEntity set into the domain object's Map
        Map<UUID, java.time.LocalDate> playerLog = entity.getPlayerLog().stream()
                .collect(Collectors.toMap(
                        logEntry -> logEntry.getPlayedWith().getUuid(),
                        PlayLogEntity::getLastPlayedDate,
                        (date1, date2) -> date1.isAfter(date2) ? date1 : date2 // Handle duplicates, keep the latest date
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

        // Update the character relationship. Because the CharacterEntity owns the relationship,
        // we must save each character, which will update its 'player' field.
        entity.getCharacters().clear();
        if (player.getCharacters() != null) {
            for (Character character : player.getCharacters()) {
                // This save will handle the translation and linking of the player for us.
                characterService.save(character);
            }
        }

        // Update the PlayLog relationship
        entity.getPlayerLog().clear();
        if (player.getPlayerLog() != null) {
            for (Map.Entry<UUID, java.time.LocalDate> entry : player.getPlayerLog().entrySet()) {
                PlayerEntity playedWithEntity = playerRepository.findByUuid(entry.getKey());
                if (playedWithEntity != null) {
                    PlayLogEntity logEntity = new PlayLogEntity(entity, playedWithEntity, entry.getValue());
                    entity.getPlayerLog().add(logEntity);
                }
            }
        }
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

