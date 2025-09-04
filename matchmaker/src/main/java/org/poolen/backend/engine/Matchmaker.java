package org.poolen.backend.engine;


import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;

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
        // 1. Prepare your data
        List<Player> playerList = new ArrayList<>(this.attendingPlayers.values());
        List<Object> slotList = new ArrayList<>(); // A simple list of all available group slots
        for (Group group : this.groups) {
            for (int i = 0; i < 4; i++) { // Assuming party size of 4
                slotList.add(new Object()); // You could create a proper Slot class later
            }
        }

        int numPlayers = playerList.size();
        int numSlots = slotList.size();

        // The solver requires numPlayers <= numSlots. We can add dummy slots if needed.

        // 2. Create the solver instance
        LinearSumAssignment assignment = new LinearSumAssignment();

        double maxScore = 10.0; // Your highest possible score (e.g., houseBonus)

        // 3. Build the cost matrix
        for (int i = 0; i < numPlayers; i++) {
            for (int j = 0; j < numSlots; j++) {
                Player player = playerList.get(i);
                int groupIndex = j / 4; // Figure out which group this slot belongs to
                Group group = this.groups.get(groupIndex);

                // Calculate the score, then invert it to a cost
                double score = this.calculateScore(player, group, true); // Use your existing score logic!
                double cost = maxScore - score;
                assignment.addArcWithCost(i, j, (long) cost);
            }
        }

        // 4. Solve the assignment problem
        if (assignment.solve() == LinearSumAssignment.Status.OPTIMAL) {
            System.out.println("Total cost = " + assignment.getOptimalCost());

            // 5. Build the final groups from the results
            for (int i = 0; i < numPlayers; i++) {
                int slotIndex = assignment.getRightMate(i);
                if (slotIndex != -1) { // -1 means the player was unassigned
                    Player player = playerList.get(i);
                    int groupIndex = slotIndex / 4;
                    this.groups.get(groupIndex).addPartyMember(player);
                }
            }
        } else {
            System.out.println("No solution found.");
        }

        return this.groups;
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
