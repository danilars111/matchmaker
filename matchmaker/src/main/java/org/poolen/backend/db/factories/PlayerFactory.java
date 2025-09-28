package org.poolen.backend.db.factories;

import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;
import org.poolen.backend.db.store.Store;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A factory for creating Player objects, ensuring consistent creation logic.
 * This follows the Singleton pattern to ensure there's only one factory.
 */
@Service
@Lazy
public class PlayerFactory {
    private final PlayerStore playerStore;

    // Private constructor to enforce the singleton pattern
    @Autowired
    private PlayerFactory(Store store) {
        this.playerStore = store.getPlayerStore();
    }

    /**
     * Creates a new Player with a given name and DM status and randomly generated UUID.
     * @param name The name of the new player.
     * @param isDungeonMaster True if the player is a Dungeon Master.
     * @return The newly created Player object.
     */
    public Player create(String name, boolean isDungeonMaster) {
        return create(null, name, isDungeonMaster);
    }

    /**
     * Creates a new Player with a given name and DM status with a set UUID.
     * This should only be used when creating objects from DB data
     * @param uuid The uuid of the new player.
     * @param name The name of the new player.
     * @param isDungeonMaster True if the player is a Dungeon Master.
     * @return The newly created Player object.
     */
    public Player create(UUID uuid, String name, boolean isDungeonMaster) {
        Player newPlayer = uuid == null ? new Player(name, isDungeonMaster) : new Player(uuid, name, isDungeonMaster);
        playerStore.addPlayer(newPlayer);
        return newPlayer;
    }
}
