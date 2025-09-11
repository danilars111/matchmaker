package org.poolen.backend.db.factories;

import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.PlayerStore;

/**
 * A factory for creating Player objects, ensuring consistent creation logic.
 * This follows the Singleton pattern to ensure there's only one factory.
 */
public class PlayerFactory {
    private static final PlayerStore playerStore = PlayerStore.getInstance();
    private static final PlayerFactory INSTANCE = new PlayerFactory();

    // Private constructor to enforce the singleton pattern
    private PlayerFactory() {}

    /**
     * Gets the single instance of the PlayerFactory.
     * @return The singleton instance.
     */
    public static PlayerFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new Player with a given name and DM status.
     * For now, it also assigns two random characters for convenience.
     * @param name The name of the new player.
     * @param isDungeonMaster True if the player is a Dungeon Master.
     * @return The newly created Player object.
     */
    public Player create(String name, boolean isDungeonMaster) {
        Player newPlayer = new Player(name, isDungeonMaster);
        playerStore.addPlayer(newPlayer);
        return newPlayer;
    }
}
