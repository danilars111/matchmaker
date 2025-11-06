package org.poolen.backend.db.factories;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.CharacterStore;
import org.poolen.backend.db.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * A factory for creating Character objects and associating them with players.
 * It ensures business rules, like having only one main character, are followed.
 * This follows the Singleton pattern.
 */
@Service
public class CharacterFactory {
    // private static final CharacterFactory INSTANCE = new CharacterFactory();
    private static final Logger logger = LoggerFactory.getLogger(CharacterFactory.class);
    CharacterStore store;


    // Private constructor to enforce the singleton pattern
    @Autowired
    public CharacterFactory(Store store) {
        this.store = store.getCharacterStore();
        logger.info("CharacterFactory initialised.");
    }

    /**
     * Gets the single instance of the CharacterFactory.
     * @return The singleton instance.
     */
/* public static CharacterFactory getInstance() {
        return INSTANCE;
    }*/

    /**
     * Creates a new character for a given player with a generated UUID
     * It enforces business rules, such as a player can only have one main character,
     * and a maximum of two non-retired characters.
     * @param player The player who will own the new character.
     * @param name The name of the new character.
     * @param house The house the new character belongs to.
     * @param isMain True if this should be the player's main character.
     * @return The newly created Character object.
     * @throws IllegalArgumentException if a business rule is violated.
     */
    public Character create(Player player, String name, House house, boolean isMain) {
        logger.info("Creating new character for player '{}' (Name: {}, House: {}, IsMain: {}).", player.getName(), name, house, isMain);
        return create(null, player, name, house, isMain);
    }

    /**
     * Creates a new character for a given player with a set UUID
     * It enforces business rules, such as a player can only have one main character,
     * and a maximum of two non-retired characters.
     * This should only be used when creating objects from DB data
     * @param uuid The UUID of the new character.
     * @param player The player who will own the new character.
     * @param name The name of the new character.
     * @param house The house the new character belongs to.
     * @param isMain True if this should be the player's main character.
     * @return The newly created Character object.
     * @throws IllegalArgumentException if a business rule is violated.
     */
    public Character create(UUID uuid, Player player, String name, House house, boolean isMain) {
        if (uuid != null) {
            logger.info("Creating character from data for player '{}' (UUID: {}, Name: {}, House: {}, IsMain: {}).",
                    player.getName(), uuid, name, house, isMain);
        }

        // Rule 1: A player cannot have more than two active (non-retired) characters.
        long unretiredCount = player.getCharacters().stream().filter(c -> !c.isRetired()).count();
        if (unretiredCount >= 2) {
            String errorMsg = String.format("Player %s (UUID: %s) already has the maximum of 2 unretired characters. Cannot create new character.",
                    player.getName(), player.getUuid());
            logger.warn(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        // Rule 2: A player can only have one main character.
        if (isMain) {
            // We'll check both the isMain flag and the convention that the main character is at index 0.
            boolean hasMain = player.getCharacters().stream().anyMatch(Character::isMain);
            if (hasMain) {
                String errorMsg = String.format("Player %s (UUID: %s) already has a main character. Cannot create a new main character.",
                        player.getName(), player.getUuid());
                logger.warn(errorMsg);
                throw new IllegalArgumentException(errorMsg);
            }
        }

        // A new character is never retired from the start.
        Character newCharacter = uuid == null ?  new Character(name, house) : new Character(uuid, name, house);
        newCharacter.setPlayer(player);
        newCharacter.setMain(isMain);

        player.addCharacter(newCharacter);

        store.addCharacter(newCharacter);
        logger.info("Successfully created and stored new character '{}' (UUID: {}) for player '{}'.",
                newCharacter.getName(), newCharacter.getUuid(), player.getName());
        return newCharacter;
    }
}
