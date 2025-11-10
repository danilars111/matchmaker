package org.poolen.backend.db.entities;

import org.poolen.backend.db.constants.House;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private String location;

    public Group(Player dungeonMaster, List<House> houses, LocalDate date, String location) {
        this.uuid = UUID.randomUUID();
        this.dungeonMaster = dungeonMaster;
        this.houses = new ArrayList<>(houses); // Create a mutable copy
        this.date = date;
        this.location = location;
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

    /**
     * Generates a string formatted with Discord markdown to display group details.
     * @return A Discord-ready markdown string.
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG);

        String dmName = (dungeonMaster != null) ? dungeonMaster.getName() : "_Unassigned_";
        sb.append("**Dungeon Master:** ").append(dmName).append("\n");
        sb.append("**Location:** ").append(location).append("\n");

        String houseThemes = houses.stream()
                .map(house -> {
                    String name = house.name().toLowerCase().replace("_", " ");
                    return name.substring(0, 1).toUpperCase() + name.substring(1);
                })
                .collect(Collectors.joining(", "));
        if (houseThemes.isEmpty()) {
            houseThemes = "_None_";
        }
        sb.append("**Geode(s):** ").append(houseThemes).append("\n");

        sb.append("**Party Members (" + party.size() + "):**\n");
        if (party.isEmpty()) {
            sb.append("> _No players in this group yet._\n");
        } else {
            party.values().stream()
                    .sorted(Comparator.comparing(Player::getName))
                    .forEach(player -> sb.append("> • ").append(player.getName()).append("\n"));
        }

        return sb.toString();
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
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
