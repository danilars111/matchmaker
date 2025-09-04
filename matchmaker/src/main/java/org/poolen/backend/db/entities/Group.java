package org.poolen.backend.db.entities;

import org.poolen.backend.db.constants.House;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class Group {
    private Player dungeonMaster;
    private List<Player> party;
    private House house;
    private Date date;

    public Group(Player dungeonMaster, House house, Date date) {
        this.dungeonMaster = dungeonMaster;
        this.house = house;
        this.date = date;
        this.party = new ArrayList<>();
    }

    @Override
    public String toString() {
        // Build a string with all the lovely details!
        String partyMembers = party.stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));

        return String.format(
                "DM: %s | Date: %s | House: %s\nParty Members: [%s]",
                dungeonMaster.getName(),
                date,
                house,
                partyMembers
        );
    }

    public Player getDungeonMaster() {
        return dungeonMaster;
    }

    public void setDungeonMaster(Player dungeonMaster) {
        this.dungeonMaster = dungeonMaster;
    }

    public List<Player> getParty() {
        return Collections.unmodifiableList(party);
    }

    public void addPartyMember(Player player) {
        this.party.add(player);
    }

    public void removePartyMember(Player player) {
        this.party.remove(player);
    }

    public House getHouse() {
        return house;
    }

    public void setHouse(House house) {
        this.house = house;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
