package org.poolen.backend.db.store;

import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.jpa.services.PlayerService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerStore {

    // The single, final instance of our class.
    private static final PlayerStore INSTANCE = new PlayerStore();

    private final Map<UUID, Player> playerMap;

    // Private constructor to prevent additional instances and to enforce
    // singleton
    private PlayerStore() {
        this.playerMap = new HashMap<>();
    }

    protected static PlayerStore getInstance() {
        return INSTANCE;
    }

    public void saveAll() {
        // service.saveAll(playerMap.values().stream().collect(Collectors.toSet()));
    }

    public void init(Set<Player> players) {
        players.forEach(this::addPlayer);
    }

    public List<Player> getDungeonMasters() {
        return this.playerMap.values().stream()
                .filter(Player::isDungeonMaster)
                .collect(Collectors.toList());
    }

    public Set<Player> getAllPlayers() {
        return new HashSet<>(playerMap.values());
    }

    public Player getPlayerByUuid(UUID uuid) {
        return this.playerMap.get(uuid);
    }

    public void addPlayer(Player player) {
        this.playerMap.put(player.getUuid(), player);
        //this.service.save(player);
    }

    public void addPlayer(Set<Player> players) {
        players.forEach(this::addPlayer);
    }

    public void removePlayer(Player player) {
        this.playerMap.remove(player.getUuid());
    }

    public void clear() { this.playerMap.clear(); }
}
