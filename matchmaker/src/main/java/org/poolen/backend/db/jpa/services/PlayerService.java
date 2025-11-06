package org.poolen.backend.db.jpa.services;

import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.interfaces.IPlayerService;
import org.poolen.backend.db.jpa.entities.CharacterEntity;
import org.poolen.backend.db.jpa.entities.PlayLogEntity;
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
import java.util.stream.Stream;

@Service
public class PlayerService implements IPlayerService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);

    private final PlayerRepository playerRepository;
    private final CharacterRepository characterRepository;
    private final CharacterService characterService;
    @Autowired
    public PlayerService(PlayerRepository playerRepository, CharacterRepository characterRepository, @Lazy CharacterService characterService) {
        this.playerRepository = playerRepository;
        this.characterRepository = characterRepository;
        this.characterService = characterService;
        logger.info("PlayerService initialised.");
    }

    @Override
    @Transactional
    public Player save(Player player) {
        if (player == null) {
            logger.warn("Save attempt failed: Provided player is null.");
            return null;
        }
        logger.info("Saving player '{}' (UUID: {}).", player.getName(), player.getUuid());

        PlayerEntity entity = playerRepository.findByUuid(player.getUuid());
        if (entity == null) {
            logger.debug("No existing entity found. Creating a new PlayerEntity for UUID: {}", player.getUuid());
            entity = new PlayerEntity();
        } else {
            logger.debug("Found existing PlayerEntity. Updating it for UUID: {}", player.getUuid());
        }

        try {
            updateEntity(entity, player);
            PlayerEntity savedEntity = playerRepository.save(entity);
            logger.info("Successfully saved player '{}' (UUID: {}).", savedEntity.getName(), savedEntity.getUuid());
            return toDomainObject(savedEntity);
        } catch (Exception e) {
            logger.error("Failed to save player with UUID: {}", player.getUuid(), e);
            throw e; // Re-throw to maintain transactional behaviour
        }
    }

    @Override
    @Transactional
    public Set<Player> saveAll(Set<Player> players) {
        if (players == null || players.isEmpty()) {
            logger.warn("saveAll called with a null or empty set of players. No action taken.");
            return new HashSet<>();
        }
        logger.info("Saving a batch of {} players.", players.size());

        // Step 1: Gather ALL related UUIDs across all players
        Set<UUID> playerUuidsToSave = players.stream().map(Player::getUuid).collect(Collectors.toSet());

        Set<UUID> allFriendUuids = players.stream()
                .flatMap(p -> Stream.of(p.getBlacklist(), p.getBuddylist(), p.getDmBlacklist())
                        .filter(Objects::nonNull).flatMap(Set::stream))
                .collect(Collectors.toSet());

        Set<UUID> allCharacterUuids = players.stream()
                .map(Player::getCharacters).filter(Objects::nonNull).flatMap(Set::stream)
                .map(Character::getUuid).collect(Collectors.toSet());
        logger.trace("Gathered {} players to save, {} unique friend UUIDs, and {} unique character UUIDs.",
                playerUuidsToSave.size(), allFriendUuids.size(), allCharacterUuids.size());

        // Step 2: Pre-fetch all entities needed for the entire batch
        Set<UUID> allPlayerUuidsToFetch = new HashSet<>(playerUuidsToSave);
        allPlayerUuidsToFetch.addAll(allFriendUuids);

        final Map<UUID, PlayerEntity> allPlayersMap;
        if (!allPlayerUuidsToFetch.isEmpty()) {
            allPlayersMap = playerRepository.findAllByUuidInWithDetails(allPlayerUuidsToFetch).stream()
                    .collect(Collectors.toMap(PlayerEntity::getUuid, Function.identity()));
            logger.debug("Pre-fetched {} total PlayerEntities from the database.", allPlayersMap.size());
        } else {
            allPlayersMap = new HashMap<>();
            logger.debug("No player UUIDs to pre-fetch.");
        }

        final Map<UUID, CharacterEntity> allCharactersMap;
        if (!allCharacterUuids.isEmpty()) {
            allCharactersMap = characterRepository.findAllByUuidInWithDetails(allCharacterUuids).stream()
                    .collect(Collectors.toMap(CharacterEntity::getUuid, Function.identity()));
            logger.debug("Pre-fetched {} total CharacterEntities from the database.", allCharactersMap.size());
        } else {
            allCharactersMap = new HashMap<>();
            logger.debug("No character UUIDs to pre-fetch.");
        }

        // Step 3: Map domain objects to entities, using pre-fetched data
        try {
            List<PlayerEntity> entitiesToSave = players.stream().map(player -> {
                PlayerEntity entity = allPlayersMap.getOrDefault(player.getUuid(), new PlayerEntity());
                updateEntity(entity, player, allPlayersMap, allCharactersMap); // Use the private worker
                return entity;
            }).collect(Collectors.toList());

            List<PlayerEntity> savedEntities = playerRepository.saveAll(entitiesToSave);
            logger.info("Successfully saved a batch of {} player entities.", savedEntities.size());

            return savedEntities.stream().map(this::toDomainObject).collect(Collectors.toCollection(HashSet::new));
        } catch (Exception e) {
            logger.error("Failed during the 'saveAll' operation for players.", e);
            throw e; // Re-throw to maintain transactional behaviour
        }
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<Player> findByUuid(UUID uuid) {
        logger.debug("Attempting to find player by UUID: {}", uuid);
        PlayerEntity entity = playerRepository.findByUuid(uuid);
        if (entity == null) {
            logger.warn("No player found with UUID: {}", uuid);
            return Optional.empty();
        }
        logger.debug("Successfully found player '{}' with UUID: {}", entity.getName(), uuid);
        return Optional.ofNullable(toDomainObject(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Player> findAll() {
        logger.info("Finding all players with full details.");
        Set<Player> foundPlayers = playerRepository.findAllWithDetails().stream()
                .map(this::toDomainObject)
                .collect(Collectors.toCollection(HashSet::new));
        logger.info("Found {} players in total.", foundPlayers.size());
        return foundPlayers;
    }

    @Override
    public Player toDomainObject(PlayerEntity entity) {
        return toDomainObject(entity, true);
    }

    @Override
    public Player toDomainObjectShallow(PlayerEntity entity) {
        if (entity == null) {
            logger.trace("toDomainObjectShallow called with null entity. Returning null.");
            return null;
        }
        logger.trace("Mapping shallow PlayerEntity (UUID: {}) to domain object.", entity.getUuid());

        Player player = new Player(entity.getUuid(), entity.getName(), entity.isDungeonMaster());
        player.setLastSeen(entity.getLastSeen());

        player.setBlacklist(entity.getBlacklist().stream().map(PlayerEntity::getUuid).collect(Collectors.toCollection(HashSet::new)));
        player.setBuddylist(entity.getBuddylist().stream().map(PlayerEntity::getUuid).collect(Collectors.toCollection(HashSet::new)));
        player.setDmBlacklist(entity.getDmBlacklist().stream().map(PlayerEntity::getUuid).collect(Collectors.toCollection(HashSet::new)));

        Map<UUID, java.time.LocalDate> playerLog = entity.getPlayerLog().stream()
                .collect(Collectors.toMap(
                        logEntry -> logEntry.getPlayedWith().getUuid(),
                        PlayLogEntity::getLastPlayedDate,
                        (date1, date2) -> date1.isAfter(date2) ? date1 : date2 // Keep the most recent date
                ));
        player.setPlayerLog(playerLog);

        return player;
    }


    private Player toDomainObject(PlayerEntity entity, boolean includeCharacters) {
        logger.trace("Mapping PlayerEntity (UUID: {}) to domain object. IncludeCharacters: {}",
                entity != null ? entity.getUuid() : "null", includeCharacters);
        if (entity == null) {
            return null;
        }

        Player player = toDomainObjectShallow(entity);

        if (includeCharacters) {
            logger.trace("... mapping {} characters for player {}.", entity.getCharacters().size(), entity.getUuid());
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
        logger.trace("Updating single PlayerEntity (UUID: {}) from domain object (UUID: {}).",
                entity != null ? entity.getUuid() : "null",
                player != null ? player.getUuid() : "null");
        if (entity == null || player == null) {
            logger.warn("UpdateEntity failed: entity or player is null.");
            return;
        }

        Set<UUID> friendUuids = Stream.of(player.getBlacklist(), player.getBuddylist(), player.getDmBlacklist())
                .filter(Objects::nonNull).flatMap(Set::stream).collect(Collectors.toSet());
        Map<UUID, PlayerEntity> friendsMap = friendUuids.isEmpty() ? new HashMap<>() :
                playerRepository.findAllByUuidInWithDetails(friendUuids).stream()
                        .collect(Collectors.toMap(PlayerEntity::getUuid, Function.identity()));
        logger.trace("... pre-fetched {} related friend entities for update.", friendsMap.size());

        Set<UUID> characterUuids = player.getCharacters() == null ? new HashSet<>() :
                player.getCharacters().stream().map(Character::getUuid).collect(Collectors.toSet());
        Map<UUID, CharacterEntity> charactersMap = characterUuids.isEmpty() ? new HashMap<>() :
                characterRepository.findAllByUuidInWithDetails(characterUuids).stream()
                        .collect(Collectors.toMap(CharacterEntity::getUuid, Function.identity()));
        logger.trace("... pre-fetched {} related character entities for update.", charactersMap.size());

        updateEntity(entity, player, friendsMap, charactersMap);
    }

    private void updateEntity(PlayerEntity entity, Player player, Map<UUID, PlayerEntity> playersMap, Map<UUID, CharacterEntity> charactersMap) {
        logger.trace("... performing private entity update for player UUID: {}", player.getUuid());
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
        logger.trace("... updating character relationships for player: {}", player.getName());
        Set<CharacterEntity> managedCharacters = playerEntity.getCharacters();

        if (player.getCharacters() == null || player.getCharacters().isEmpty()) {
            logger.trace("... player has no characters. Clearing relationship set.");
            managedCharacters.clear();
            return;
        }

        Set<CharacterEntity> updatedChars = new HashSet<>();
        for (Character character : player.getCharacters()) {
            CharacterEntity charEntity = allCharactersMap.getOrDefault(character.getUuid(), new CharacterEntity());

            // Manually map character fields here, *except* for the player relationship
            charEntity.setUuid(character.getUuid());
            charEntity.setName(character.getName());
            charEntity.setHouse(character.getHouse());
            charEntity.setMain(character.isMain());
            charEntity.setRetired(character.isRetired());
            charEntity.setPlayer(playerEntity); // Set the back-reference

            updatedChars.add(charEntity);
        }

        managedCharacters.clear();
        managedCharacters.addAll(updatedChars);
        logger.trace("... player's character set updated to {} characters.", updatedChars.size());
    }

    private void updatePlayerSet(Set<UUID> uuidSet, Set<PlayerEntity> entitySet, Map<UUID, PlayerEntity> relatedPlayersMap) {
        entitySet.clear();
        if (uuidSet != null) {
            uuidSet.stream()
                    .map(relatedPlayersMap::get)
                    .filter(Objects::nonNull) // Filter out any UUIDs that didn't correspond to a fetched player
                    .forEach(entitySet::add);
        }
    }
}
