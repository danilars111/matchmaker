package org.poolen.backend.db.entities;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class Player {
    private UUID uuid;
    private String name;
    private ArrayList<Character> characters = new ArrayList<>();

    private Map<UUID, Player> buddylist = new HashMap<>();
    private Map<UUID, Player> blacklist = new HashMap<>();
    private Map<UUID, Player> DmBlacklist = new HashMap<>();
    private Map<UUID, Date> playerLog = new HashMap<>();

    private boolean isDungeonMaster;
    private Date lastSeen;

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

    public Map<UUID, Player> getBuddylist() {
        return buddylist;
    }

    public void setBuddylist(Map<UUID, Player> buddylist) {
        this.buddylist = buddylist;
    }

    public Map<UUID, Player> getBlacklist() {
        return blacklist;
    }

    public void setBlacklist(Map<UUID, Player> blacklist) {
        this.blacklist = blacklist;
    }

    public Date getLastSeen() {
        return lastSeen;
    }

    public Map<UUID, Player> getDmBlacklist() {
        return DmBlacklist;
    }

    public void setDmBlacklist(Map<UUID, Player> dmBlacklist) {
        DmBlacklist = dmBlacklist;
    }

    public Map<UUID, Date> getPlayerLog() {
        return playerLog;
    }

    public void setPlayerLog(Map<UUID, Date> playerLog) {
        this.playerLog = playerLog;
    }

    public void setLastSeen(Date lastSeen) {
        this.lastSeen = lastSeen;

    }
}
