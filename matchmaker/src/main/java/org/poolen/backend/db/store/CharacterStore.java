package org.poolen.backend.db.store;

import jakarta.annotation.PostConstruct;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.jpa.services.CharacterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CharacterStore {

    // The single, final instance of our class.
    //private static final CharacterStore INSTANCE = new CharacterStore();

    private Map<UUID, Character> characterMap;
    private final CharacterService service;

    // Private constructor to prevent additional instances and to enforce
    // singleton
    @Autowired
    public CharacterStore(CharacterService service)
    {
        this.characterMap = new HashMap<>();
        this.service = service;
    }
    public void init() {
        service.findAll().forEach(this::addCharacter);
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
        //service.save(character);
    }

    public void addCharacter(List<Character> characters) {
        characters.forEach(this::addCharacter);
    }

    public void removeCharacter(Character character) { this.characterMap.remove(character.getUuid()); }

    public void clear() { this.characterMap.clear(); }

    public void saveAll() {
        characterMap.values().forEach(service::save);
    }
}
