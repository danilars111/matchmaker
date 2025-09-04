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
import java.util.stream.Collectors;

public class Matchmaker {
    private List<Group> groups;
    private Map<UUID, Player> attendingPlayers;


    private static final double MAX_SCORE = 10.0;
    private static final double DEFAULT_SCORE = 1.0;

    public Matchmaker(List<Group> groups, Map<UUID, Player> attendingPlayers) {
        this.groups = new ArrayList<>(groups);
        this.attendingPlayers = new HashMap<>(attendingPlayers);
    }

    public List<Group> match() {
        // First, filter out the DMs so we are only trying to match actual players
        List<Player> playersToMatch = this.attendingPlayers.values().stream()
                .filter(player -> !player.isDungeonMaster())
                .collect(Collectors.toList());

        if (playersToMatch.isEmpty() || groups.isEmpty()) {
            return this.groups;
        }

        // --- Step 1: Calculate Dynamic Group Sizes ---
        int numPlayers = playersToMatch.size();
        int numGroups = this.groups.size();
        int baseSize = numPlayers / numGroups;
        int remainder = numPlayers % numGroups;

        List<Integer> groupSizes = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            groupSizes.add(baseSize + (i < remainder ? 1 : 0));
        }
        System.out.println("Calculated group sizes: " + groupSizes);
        int totalSlots = groupSizes.stream().mapToInt(Integer::intValue).sum();
        // ---------------------------------------------


        // --- The Anti-Crash Safety Check ---
        if (numPlayers > totalSlots) {
            System.err.println("Error: More players than available slots. This should not happen with dynamic sizing.");
            return this.groups;
        }
        // ------------------------------------


        // --- Step 2: Build the Slot-to-Group Map ---
        List<Integer> slotToGroupMap = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            for (int j = 0; j < groupSizes.get(i); j++) {
                slotToGroupMap.add(i);
            }
        }
        // ------------------------------------------

        LinearSumAssignment assignment = new LinearSumAssignment();
        try {
            // Build the cost matrix using the total number of slots
            for (int i = 0; i < numPlayers; i++) {
                for (int j = 0; j < totalSlots; j++) {
                    Player player = playersToMatch.get(i);
                    // Use our new map to find the correct group for this slot
                    int groupIndex = slotToGroupMap.get(j);
                    Group group = this.groups.get(groupIndex);
                    double score = calculateScore(player, group);
                    double cost = MAX_SCORE - score;
                    assignment.addArcWithCost(i, j, (long) cost);
                }
            }

            // Solve and assign players
            if (assignment.solve() == LinearSumAssignment.Status.OPTIMAL) {
                System.out.println("Total cost = " + assignment.getOptimalCost());
                for (int i = 0; i < numPlayers; i++) {
                    int slotIndex = assignment.getRightMate(i);
                    if (slotIndex != -1) {
                        Player player = playersToMatch.get(i);
                        // Use the map again to place the player in the correct group
                        int groupIndex = slotToGroupMap.get(slotIndex);
                        this.groups.get(groupIndex).addPartyMember(player);
                    }
                }
            } else {
                System.out.println("No optimal solution found.");
            }
        } finally {
            assignment.delete();
        }

        return this.groups;
    }


    private double calculateScore(Player player, Group group) {
        for (Character character : player.getCharacters()) {
            if (character != null && character.getHouse().equals(group.getHouse())) {
                return MAX_SCORE;
            }
        }
        return DEFAULT_SCORE;
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
