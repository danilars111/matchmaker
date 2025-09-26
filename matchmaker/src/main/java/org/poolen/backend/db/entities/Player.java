package org.poolen.backend.db.entities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Player {
    private UUID uuid;
    private String name;
    private ArrayList<Character> characters = new ArrayList<>();

    private Set<UUID> buddylist = new HashSet<>();
    private Set<UUID> blacklist = new HashSet<>();
    private Set<UUID> DmBlacklist = new HashSet<>();
    private Map<UUID, LocalDate> playerLog = new HashMap<>();

    private boolean isDungeonMaster;
    private LocalDate lastSeen;


    public Player(String name, boolean isDungeonMaster) {
        this(UUID.randomUUID(), name, isDungeonMaster);
    }

    public Player(UUID uuid, String name, boolean isDungeonMaster) {
        this.uuid = uuid;
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

    public Set<UUID> getBuddylist() {
        return buddylist;
    }

    public void setBuddylist(Set<UUID> buddylist) {
        this.buddylist = buddylist;
    }

    public Set<UUID> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(Set<UUID> blacklist) {
        this.blacklist = blacklist;
    }
    public void blacklist(Player player) {
        // First, check if we've already blacklisted this player.
        // If we have, our work is done and we can stop!
        if (this.blacklist.contains(player.getUuid())) {
            return;
        }

        // If not, add them to our list and the log...
        this.blacklist.add(player.getUuid());
        this.playerLog.put(player.getUuid(), LocalDate.now());

        // ...and now we can safely tell them to blacklist us back!
        player.blacklist(this);
    }

    public void unblacklist(Player player) {
        // First, check if we've actually blacklisted this player.
        // If we have, our work is done and we can stop!
        if (!this.blacklist.contains(player.getUuid())) {
            return;
        }

        // If not, remove them to our list...
        this.blacklist.remove(player.getUuid());

        // ...and now we can safely tell them to unblacklist us back!
        player.unblacklist(this);
    }

    public LocalDate getLastSeen() {
        return lastSeen;
    }

    public Set<UUID> getDmBlacklist() {
        return DmBlacklist;
    }

    public void setDmBlacklist(Set<UUID> dmBlacklist) {
        DmBlacklist = dmBlacklist;
    }
    public void blacklistDm(Player player) {
        this.DmBlacklist.add(player.getUuid());
    }

    public Map<UUID, LocalDate> getPlayerLog() {
        return playerLog;
    }

    public void setPlayerLog(Map<UUID, LocalDate> playerLog) {
        this.playerLog = playerLog;
    }

    public void setLastSeen(LocalDate lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean hasCharacters() {
        return !characters.isEmpty();
    }
    public boolean hasEmptyCharacterSlot() {
        return characters.stream().filter(c -> !c.isRetired()).count() < 2;
    }
    public Character getMainCharacter() {
        return characters.stream()
                .filter(Character::isMain)
                .findFirst()
                .orElse(null);
    }

    public void updatePlayerLog(Group group) {
        for (Player player : group.getParty().values()) {
            if (player.equals(this)) { continue; }
            this.playerLog.put(player.getUuid(), group.getDate());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) {return false; }
        Player player = (Player) o;
        return Objects.equals(this.uuid, player.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, name, characters, buddylist, blacklist, DmBlacklist, playerLog, isDungeonMaster, lastSeen);
    }
}
