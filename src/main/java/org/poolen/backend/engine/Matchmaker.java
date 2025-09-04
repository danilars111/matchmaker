package org.poolen.backend.engine;

import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.store.CharacterStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Matchmaker {
    private List<Group> groups;
    private Map<UUID, Player> attendingPlayers;

    public Matchmaker(List<Group> groups, Map<UUID, Player> attendingPlayers) {
        this.groups = new ArrayList<>(groups);
        this.attendingPlayers = new HashMap<>(attendingPlayers);
    }

    public List<Group> Match() {
        // Create mutable copies of our data
        List<Group> groupsToFill = new ArrayList<>(this.groups);
        Map<UUID, Player> availablePlayers = new HashMap<>(this.attendingPlayers);

        // --- Pass 1: Match players to their preferred houses ---
        List<Group> perfectGroups = new ArrayList<>();
        int partySize = 4; // Assuming a fixed party size for now

        for (Group group : groupsToFill) {
            while (group.getParty().size() < partySize) {
                Player bestMatch = this.getBestMatch(group, availablePlayers, true); // True for strict house matching
                if (bestMatch != null) {
                    group.addPartyMember(bestMatch);
                    availablePlayers.remove(bestMatch.getUuid());
                } else {
                    break;
                }
            }
            perfectGroups.add(group);
        }

        // --- Pass 2: Fill remaining spots with anyone left over ---
        for (Group group : perfectGroups) {
            while (group.getParty().size() < partySize && !availablePlayers.isEmpty()) {
                Player bestMatch = this.getBestMatch(group, availablePlayers, false); // False for relaxed matching
                if (bestMatch != null) {
                    group.addPartyMember(bestMatch);
                    availablePlayers.remove(bestMatch.getUuid());
                } else {
                    break;
                }
            }
        }

        return perfectGroups;
    }

    private Player getBestMatch(Group group, Map<UUID, Player> availablePlayers, boolean strictMatching) {
        Player bestMatch = null;
        double highestScore = Double.MIN_VALUE;
        List<Player> tiedPlayers = new ArrayList<>();

        for (Player player : availablePlayers.values()) {
            double currentScore = this.calculateScore(player, group, strictMatching);

            if (currentScore > highestScore) {
                highestScore = currentScore;
                tiedPlayers.clear();
                tiedPlayers.add(player);
            } else if (currentScore == highestScore) {
                tiedPlayers.add(player);
            }
        }

        if (!tiedPlayers.isEmpty()) {
            int randomIndex = new Random().nextInt(tiedPlayers.size());
            bestMatch = tiedPlayers.get(randomIndex);
        }

        return bestMatch;
    }

    private double calculateScore(Player player, Group group, boolean strictMatching) {
        double houseBonus = 10.0;
        double defaultScore = 1.0;

        if (strictMatching) {
            for (Character character : player.getCharacters()) {
                if (character != null && character.getHouse().equals(group.getHouse())) {
                    return houseBonus;
                }
            }
            return defaultScore;
        } else {
            // In a more complex system, this pass would have its own weights.
            // For now, let's just make sure anyone can get a spot.
            return defaultScore;
        }
    }
    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = new ArrayList<>(groups);
    }

    public Map<UUID, Player> getAttendingPlayers() {
        return attendingPlayers;
    }

    public void setAttendingPlayers(Map<UUID, Player> attendingPlayers) {
        this.attendingPlayers = new HashMap<> (attendingPlayers);
    }
}
