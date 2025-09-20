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
    public void blacklist(Player player) {
        // First, check if we've already blacklisted this player.
        // If we have, our work is done and we can stop!
        if (this.blacklist.containsKey(player.getUuid())) {
            return;
        }

        // If not, add them to our list and the log...
        this.blacklist.put(player.getUuid(), player);
        this.playerLog.put(player.getUuid(), new Date());

        // ...and now we can safely tell them to blacklist us back!
        player.blacklist(this);
    }

    public void unblacklist(Player player) {
        // First, check if we've actually blacklisted this player.
        // If we have, our work is done and we can stop!
        if (!this.blacklist.containsKey(player.getUuid())) {
            return;
        }

        // If not, remove them to our list...
        this.blacklist.remove(player.getUuid());

        // ...and now we can safely tell them to unblacklist us back!
        player.unblacklist(this);
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
    public void blacklistDm(Player player) {
        this.DmBlacklist.put(player.getUuid(), player);
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
}
