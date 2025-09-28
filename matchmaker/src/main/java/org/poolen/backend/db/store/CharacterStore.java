package org.poolen.backend.db.store;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.jpa.services.CharacterService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CharacterStore {

    // The single, final instance of our class.
    private static final CharacterStore INSTANCE = new CharacterStore();
    private Map<UUID, Character> characterMap;

    // Private constructor to prevent additional instances and to enforce
    // singleton
    public CharacterStore()
    {
        this.characterMap = new HashMap<>();
    }

    protected static CharacterStore getInstance() {
        return INSTANCE;
    }
    public void init(Set<Character> characters) {
       characters.forEach(this::addCharacter);
    }

    public List<Character> getCharactersByHouse(House house) {
        return this.characterMap.values().stream()
                .filter(character -> character.getHouse().equals(house))
                .collect(Collectors.toList());
    }

    public Set<Character> getAllCharacters() {
        return new HashSet<>(characterMap.values());
    }

    public Character getCharacterByUuid(UUID uuid) {
        return this.characterMap.get(uuid);
    }

    public void addCharacter(Character character) {
        this.characterMap.put(character.getUuid(), character);
        //service.save(character);
    }

    public void addCharacter(List<Character> characters) {
        characters.forEach(this::addCharacter);
    }

    public void removeCharacter(Character character) { this.characterMap.remove(character.getUuid()); }

    public void clear() { this.characterMap.clear(); }
}
