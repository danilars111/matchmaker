package org.poolen.backend.db.entities;

import org.poolen.backend.db.constants.House;

import java.util.UUID;

public class Character {
    private UUID uuid;
    private String name;
    private House house;
    private Player player;
    private boolean isMain;
    private boolean isRetired;

    public Character(String name, House house) {
        this(UUID.randomUUID(), name, house);
    }

    public Character(UUID uuid, String name, House house) {
        this.uuid = uuid;
        this.name = name;
        this.house = house;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public House getHouse() {
        return house;
    }

    public void setHouse(House house) {
        this.house = house;
    }

    public boolean isMain() {
        return isMain;
    }

    public void setMain(boolean main) {
        this.isMain = main;

        if (this.player != null && main) {
            // Demote any other character that thinks it's the main.
            for (Character otherChar : this.player.getCharacters()) {
                if (otherChar != this && otherChar.isMain()) {
                    otherChar.setMain(false); // Recursion is fine here, as it will just flip the boolean.
                }
            }
            // Now, ensure this character is at the front of the line!
            if(player.getCharacters().contains(this)) {
                this.player.getCharacters().remove(this);
                this.player.getCharacters().add(0, this);
            }
        }
    }
    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public boolean isRetired() {
        return isRetired;
    }

    public void setRetired(boolean retired) {
        this.isRetired = retired;
        if(this.isMain) {
            this.isMain = false;
        }
    }
}
