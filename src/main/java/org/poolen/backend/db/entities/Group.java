package org.poolen.backend.db.entities;

import org.poolen.backend.db.constants.House;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Group {
    private Player dungeonMaster;
    private List<Player> party;
    private House house;

    public Group(Player dungeonMaster, House house) {
        this.dungeonMaster = dungeonMaster;
        this.house = house;
        this.party = new ArrayList<>();
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
}
