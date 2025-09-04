package org.poolen.backend.db.entities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

public class Player {
    private UUID uuid;
    private String name;
    private ArrayList<Character> characters;
    private boolean isDungeonMaster;

    public Player(String name, boolean isDungeonMaster) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.isDungeonMaster = isDungeonMaster;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addCharacter(Character character) {
        this.characters.add(character);
    }

    public void removeCharacter(Character charToRemove) {
        // Chill dude, people have two characters, linear search will be fiine!
        Iterator<Character> iterator = this.characters.iterator();
        while (iterator.hasNext()) {
            Character character = iterator.next();
            if (character.getUuid().equals(charToRemove.getUuid())) {
                iterator.remove();
                return;
            }
        }
    }

    public ArrayList<Character> getCharacters() {
        return characters;
    }

    public void setCharacters(ArrayList<Character> characters) {
        this.characters = characters;
    }

    public boolean isDungeonMaster() {
        return isDungeonMaster;
    }

    public void setDungeonMaster(boolean dungeonMaster) {
        isDungeonMaster = dungeonMaster;
    }
}
