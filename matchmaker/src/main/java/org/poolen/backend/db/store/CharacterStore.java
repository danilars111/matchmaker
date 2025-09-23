package org.poolen.backend.db.store;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CharacterStore {

    // The single, final instance of our class.
    private static final CharacterStore INSTANCE = new CharacterStore();

    private Map<UUID, Character> characterMap;

    // Private constructor to prevent additional instances and to enforce
    // singleton
    private CharacterStore() {
        this.characterMap = new HashMap<>();
    }

    public static CharacterStore getInstance() {
        return INSTANCE;
    }

    public List<Character> getCharactersByHouse(House house) {
        return this.characterMap.values().stream()
                .filter(character -> character.getHouse().equals(house))
                .collect(Collectors.toList());
    }

    public List<Character> getAllCharacters() {
        return this.characterMap.values().stream().toList();
    }

    public Character getCharacterByUuid(UUID uuid) {
        return this.characterMap.get(uuid);
    }

    public void addCharacter(Character character) {
        this.characterMap.put(character.getUuid(), character);
    }

    public void addCharacter(List<Character> characters) {
        characters.forEach(this::addCharacter);
    }

    public void removeCharacter(Character character) { this.characterMap.remove(character.getUuid()); }

    public void clear() { this.characterMap.clear(); }
}
