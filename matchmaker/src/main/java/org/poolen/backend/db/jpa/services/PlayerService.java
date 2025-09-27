package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.IPlayerService;
import org.poolen.backend.db.jpa.entities.CharacterEntity;
import org.poolen.backend.db.jpa.entities.PlayLogEntity;
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
import java.util.stream.Stream;

@Service
public class PlayerService implements IPlayerService {

    private final PlayerRepository playerRepository;
    private final CharacterRepository characterRepository;
    private final CharacterService characterService;
    @Autowired
    public PlayerService(PlayerRepository playerRepository, CharacterRepository characterRepository, @Lazy CharacterService characterService) {
        this.playerRepository = playerRepository;
        this.characterRepository = characterRepository;
        this.characterService = characterService;
    }

    @Override
    @Transactional
    public Player save(Player player) {
        PlayerEntity entity = playerRepository.findByUuid(player.getUuid());
        if (entity == null) {
            entity = new PlayerEntity();
        }

        updateEntity(entity, player);
        PlayerEntity savedEntity = playerRepository.save(entity);

        return toDomainObject(savedEntity);
    }

    @Override
    @Transactional
    public Set<Player> saveAll(Set<Player> players) {
        if (players == null || players.isEmpty()) {
            return new HashSet<>();
        }

        // Step 1: Gather ALL related UUIDs across all players
        Set<UUID> playerUuidsToSave = players.stream().map(Player::getUuid).collect(Collectors.toSet());

        Set<UUID> allFriendUuids = players.stream()
                .flatMap(p -> Stream.of(p.getBlacklist(), p.getBuddylist(), p.getDmBlacklist())
                        .filter(Objects::nonNull).flatMap(Set::stream))
                .collect(Collectors.toSet());

        Set<UUID> allCharacterUuids = players.stream()
                .map(Player::getCharacters).filter(Objects::nonNull).flatMap(Set::stream)
                .map(Character::getUuid).collect(Collectors.toSet());

        // Step 2: Pre-fetch all entities needed for the entire batch
        Set<UUID> allPlayerUuidsToFetch = new HashSet<>(playerUuidsToSave);
        allPlayerUuidsToFetch.addAll(allFriendUuids);

        final Map<UUID, PlayerEntity> allPlayersMap;
        if (!allPlayerUuidsToFetch.isEmpty()) {
            allPlayersMap = playerRepository.findAllByUuidInWithDetails(allPlayerUuidsToFetch).stream()
                    .collect(Collectors.toMap(PlayerEntity::getUuid, Function.identity()));
        } else {
            allPlayersMap = new HashMap<>();
        }

        final Map<UUID, CharacterEntity> allCharactersMap;
        if (!allCharacterUuids.isEmpty()) {
            allCharactersMap = characterRepository.findAllByUuidInWithDetails(allCharacterUuids).stream()
                    .collect(Collectors.toMap(CharacterEntity::getUuid, Function.identity()));
        } else {
            allCharactersMap = new HashMap<>();
        }

        // Step 3: Map domain objects to entities, using pre-fetched data
        List<PlayerEntity> entitiesToSave = players.stream().map(player -> {
            PlayerEntity entity = allPlayersMap.getOrDefault(player.getUuid(), new PlayerEntity());
            updateEntity(entity, player, allPlayersMap, allCharactersMap); // Use the private worker
            return entity;
        }).collect(Collectors.toList());

        List<PlayerEntity> savedEntities = playerRepository.saveAll(entitiesToSave);

        return savedEntities.stream().map(this::toDomainObject).collect(Collectors.toCollection(HashSet::new));
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
        return toDomainObject(entity, true);
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


    private Player toDomainObject(PlayerEntity entity, boolean includeCharacters) {
        if (entity == null) {
            return null;
        }

        Player player = toDomainObjectShallow(entity);

        if (includeCharacters) {
            player.setCharacters(
                    entity.getCharacters().stream()
                            .map(characterService::toDomainObject)
                            .collect(Collectors.toCollection(HashSet::new))
            );
        }

        return player;
    }

    @Override
    public void updateEntity(PlayerEntity entity, Player player) {
        if (entity == null || player == null) return;

        Set<UUID> friendUuids = Stream.of(player.getBlacklist(), player.getBuddylist(), player.getDmBlacklist())
                .filter(Objects::nonNull).flatMap(Set::stream).collect(Collectors.toSet());
        Map<UUID, PlayerEntity> friendsMap = friendUuids.isEmpty() ? new HashMap<>() :
                playerRepository.findAllByUuidInWithDetails(friendUuids).stream()
                        .collect(Collectors.toMap(PlayerEntity::getUuid, Function.identity()));

        Set<UUID> characterUuids = player.getCharacters() == null ? new HashSet<>() :
                player.getCharacters().stream().map(Character::getUuid).collect(Collectors.toSet());
        Map<UUID, CharacterEntity> charactersMap = characterUuids.isEmpty() ? new HashMap<>() :
                characterRepository.findAllByUuidInWithDetails(characterUuids).stream()
                        .collect(Collectors.toMap(CharacterEntity::getUuid, Function.identity()));

        updateEntity(entity, player, friendsMap, charactersMap);
    }

    private void updateEntity(PlayerEntity entity, Player player, Map<UUID, PlayerEntity> playersMap, Map<UUID, CharacterEntity> charactersMap) {
        entity.setUuid(player.getUuid());
        entity.setName(player.getName());
        entity.setDungeonMaster(player.isDungeonMaster());
        entity.setLastSeen(player.getLastSeen());

        updatePlayerSet(player.getBlacklist(), entity.getBlacklist(), playersMap);
        updatePlayerSet(player.getBuddylist(), entity.getBuddylist(), playersMap);
        updatePlayerSet(player.getDmBlacklist(), entity.getDmBlacklist(), playersMap);

        updateCharacterRelationships(entity, player, charactersMap);
    }

    private void updateCharacterRelationships(PlayerEntity playerEntity, Player player, Map<UUID, CharacterEntity> allCharactersMap) {
        Set<CharacterEntity> managedCharacters = playerEntity.getCharacters();

        if (player.getCharacters() == null || player.getCharacters().isEmpty()) {
            managedCharacters.clear();
            return;
        }

        Set<CharacterEntity> updatedChars = new HashSet<>();
        for (Character character : player.getCharacters()) {
            CharacterEntity charEntity = allCharactersMap.getOrDefault(character.getUuid(), new CharacterEntity());

            charEntity.setUuid(character.getUuid());
            charEntity.setName(character.getName());
            charEntity.setHouse(character.getHouse());
            charEntity.setMain(character.isMain());
            charEntity.setRetired(character.isRetired());
            charEntity.setPlayer(playerEntity);

            updatedChars.add(charEntity);
        }

        managedCharacters.clear();
        managedCharacters.addAll(updatedChars);
    }

    private void updatePlayerSet(Set<UUID> uuidSet, Set<PlayerEntity> entitySet, Map<UUID, PlayerEntity> relatedPlayersMap) {
        entitySet.clear();
        if (uuidSet != null) {
            uuidSet.stream()
                    .map(relatedPlayersMap::get)
                    .filter(Objects::nonNull)
                    .forEach(entitySet::add);
        }
    }
}

