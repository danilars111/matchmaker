package org.poolen.backend.db.store;

import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.jpa.services.PlayerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlayerStore {

    // The single, final instance of our class.
    private final Map<UUID, Player> playerMap;
    private final PlayerService service;

    // Private constructor to prevent additional instances and to enforce
    // singleton
    @Autowired
    private PlayerStore(PlayerService service) {
        this.playerMap = new HashMap<>();
        this.service = service;
    }

    public List<Player> getDungeonMasters() {
        return this.playerMap.values().stream()
                .filter(Player::isDungeonMaster)
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
        //this.service.save(player);
    }

    public void saveAll() {
        playerMap.values().forEach(service::save);
    }

    public void addPlayer(List<Player> players) {
        players.forEach(this::addPlayer);
    }

    public void removePlayer(Player player) {
        this.playerMap.remove(player.getUuid());
    }

    public void clear() { this.playerMap.clear(); }
}
