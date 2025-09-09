package org.poolen.backend.db.store;

import org.poolen.backend.db.entities.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerStore {

    // The single, final instance of our class.
    private static final PlayerStore INSTANCE = new PlayerStore();

    private Map<UUID, Player> playerMap;

    // Private constructor to prevent additional instances and to enforce
    // singleton
    private PlayerStore() {
        this.playerMap = new HashMap<>();
    }

    public static PlayerStore getInstance() {
        return INSTANCE;
    }

    public List<Player> getDungeonMasters() {
        return this.playerMap.values().stream()
                .filter(player -> player.isDungeonMaster())
                .collect(Collectors.toList());
    }

    public List<Player> getAllPlayers() {
        return this.playerMap.values().stream().toList();
    }

    public Player getPlayerByUuid(UUID uuid) {
        return this.playerMap.get(uuid);
    }

    public void addPlayer(Player player) {
        this.playerMap.put(player.getUuid(), player);
    }

    public void addPlayer(List<Player> players) {
        players.forEach(this::addPlayer);
    }

    public void removePlayer(Player player) {
        this.playerMap.remove(player.getUuid());
    }
}
