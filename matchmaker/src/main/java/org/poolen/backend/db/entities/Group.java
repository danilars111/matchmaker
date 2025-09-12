package org.poolen.backend.db.entities;

import org.poolen.backend.db.constants.House;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class Group {

    private UUID uuid;
    private Player dungeonMaster;
    private Map<UUID, Player> party;
    private List<House> houses;
    private LocalDate date;

    public Group(Player dungeonMaster, List<House> houses, LocalDate date) {
        this.uuid = UUID.randomUUID();
        this.dungeonMaster = dungeonMaster;
        this.houses = new ArrayList<>(houses); // Create a mutable copy
        this.date = date;
        this.party = new HashMap<>();
    }

    @Override
    public String toString() {
        String partyMembers = party.values().stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));

        // The toString method now elegantly lists all the themes!
        String houseThemes = houses.stream()
                .map(House::toString)
                .collect(Collectors.joining(" & "));

        return String.format(
                "DM: %s | Date: %s | House(s): %s\nParty Members: [%s]",
                dungeonMaster.getName(),
                date,
                houseThemes,
                partyMembers
        );
    }

    public Player getDungeonMaster() {
        return dungeonMaster;
    }

    public void setDungeonMaster(Player dungeonMaster) {
        this.dungeonMaster = dungeonMaster;
    }

    public void removeDungeonMaster() {
        setDungeonMaster(null);
    }

    public Map<UUID, Player> getParty() {
        return Collections.unmodifiableMap(party);
    }

    public void addPartyMember(Player player) {
        this.party.put(player.getUuid(), player);
    }

    public void removePartyMember(Player player) {
        this.party.remove(player.getUuid());
    }

    public List<House> getHouses() {
        return houses;
    }

    public void setHouses(List<House> houses) {
        this.houses = houses;
    }

    public void addHouse(House house) {
        if (!this.houses.contains(house)) {
            this.houses.add(house);
        }
    }

    public void removeHouse(House house) {
        this.houses.remove(house);
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void movePlayerTo(Player player, Group group) {
        this.removePartyMember(player);
        group.addPartyMember(player);
    }

    public void moveDungeonMasterTo(Player dm, Group group) {
        this.removeDungeonMaster();
        group.setDungeonMaster(dm);
    }
}

