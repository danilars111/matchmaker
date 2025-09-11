package org.poolen.backend.db.factories;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;

/**
 * A factory for creating Character objects and associating them with players.
 * It ensures business rules, like having only one main character, are followed.
 * This follows the Singleton pattern.
 */
public class CharacterFactory {

    private static final CharacterFactory INSTANCE = new CharacterFactory();

    // Private constructor to enforce the singleton pattern
    private CharacterFactory() {}

    /**
     * Gets the single instance of the CharacterFactory.
     * @return The singleton instance.
     */
    public static CharacterFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new character for a given player.
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

        // Rule 1: A player cannot have more than two active (non-retired) characters.
        long unretiredCount = player.getCharacters().stream().filter(c -> !c.isRetired()).count();
        if (unretiredCount >= 2) {
            throw new IllegalArgumentException("Player already has the maximum of 2 unretired characters, darling!");
        }

        // Rule 2: A player can only have one main character.
        if (isMain) {
            // We'll check both the isMain flag and the convention that the main character is at index 0.
            boolean hasMain = player.getCharacters().stream().anyMatch(Character::isMain);
            if (hasMain || (!player.getCharacters().isEmpty() && player.getCharacters().get(0).isMain())) {
                throw new IllegalArgumentException("Cannot create a new main character for a player who already has one, darling!");
            }
        }

        // A new character is never retired from the start.
        Character newCharacter = new Character(name, house);
        newCharacter.setPlayer(player);
        newCharacter.setMain(isMain);


        if (isMain) {
            // Add the main character to the very beginning of the list to maintain our convention.
            player.getCharacters().add(0, newCharacter);
        } else {
            // An alt character just gets added to the end.
            player.addCharacter(newCharacter);
        }

        return newCharacter;
    }
}

