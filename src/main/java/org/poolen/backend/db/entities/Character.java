package org.poolen.backend.db.entities;

import org.poolen.backend.db.constants.House;

import java.util.UUID;

public class Character {
    private UUID uuid;
    private String name;
    private House house;
    private boolean isMain;

    public Character(String name, House house) {
        this.uuid = UUID.randomUUID();
        this.name = name;
        this.house = house;
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
        isMain = main;
    }
}
