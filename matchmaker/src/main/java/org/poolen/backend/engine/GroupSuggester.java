package org.poolen.backend.engine;

import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroupSuggester {

    private final List<Player> dungeonMasters;
    private final List<Player> playersToMatch;
    private static final double MAX_SCORE = 10.0;
    private static final double DEFAULT_SCORE = 1.0;

    public GroupSuggester(Collection<Player> attendees) {
        this.dungeonMasters = attendees.stream()
                .filter(Player::isDungeonMaster)
                .collect(Collectors.toList());
        this.playersToMatch = attendees.stream()
                .filter(p -> !p.isDungeonMaster())
                .collect(Collectors.toList());
    }

    public List<House> suggestGroupThemes() {
        if (dungeonMasters.isEmpty() || playersToMatch.isEmpty()) {
            return new ArrayList<>();
        }

        List<House> allPossibleHouses = Stream.of(House.values()).collect(Collectors.toList());
        int numGroupsToSuggest = dungeonMasters.size();

        // Generate all possible combinations of house themes for the given number of groups
        List<List<House>> themeCombinations = getCombinations(allPossibleHouses, numGroupsToSuggest);

        List<House> bestCombination = new ArrayList<>();
        long minCost = Long.MAX_VALUE;

        // Simulate matchmaking for each combination to find the one with the lowest potential cost
        for (List<House> combination : themeCombinations) {
            long currentCost = calculateTotalCost(this.playersToMatch, combination);
            if (currentCost < minCost) {
                minCost = currentCost;
                bestCombination = combination;
            }
        }

        System.out.println("Suggestion found with a minimum potential cost of: " + minCost);
        return bestCombination;
    }

    private long calculateTotalCost(List<Player> players, List<House> themeCombination) {
        if (players.isEmpty() || themeCombination.isEmpty()) {
            return Long.MAX_VALUE;
        }

        // --- Step 1: Calculate Dynamic Group Sizes (Mirrors Matchmaker logic) ---
        int numPlayers = players.size();
        int numGroups = themeCombination.size();
        int baseSize = numPlayers / numGroups;
        int remainder = numPlayers % numGroups;

        List<Integer> groupSizes = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            groupSizes.add(baseSize + (i < remainder ? 1 : 0));
        }
        int totalSlots = groupSizes.stream().mapToInt(Integer::intValue).sum();
        // --------------------------------------------------------------------

        if (numPlayers > totalSlots) {
            return Long.MAX_VALUE; // Safeguard
        }

        // --- Step 2: Build the Slot-to-Group Map (Mirrors Matchmaker logic) ---
        List<Integer> slotToGroupMap = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            for (int j = 0; j < groupSizes.get(i); j++) {
                slotToGroupMap.add(i);
            }
        }

        LinearSumAssignment assignment = new LinearSumAssignment();
        long totalCost = Long.MAX_VALUE;
        try {
            for (int i = 0; i < numPlayers; i++) {
                for (int j = 0; j < totalSlots; j++) {
                    Player player = players.get(i);
                    int groupIndex = slotToGroupMap.get(j);
                    House house = themeCombination.get(groupIndex);
                    double score = calculateScoreForHouse(player, house);
                    double cost = MAX_SCORE - score;
                    assignment.addArcWithCost(i, j, (long) cost);
                }
            }

            if (assignment.solve() == LinearSumAssignment.Status.OPTIMAL) {
                totalCost = assignment.getOptimalCost();
            }
        } finally {
            assignment.delete();
        }
        return totalCost;
    }

    private double calculateScoreForHouse(Player player, House house) {
        for (Character character : player.getCharacters()) {
            if (character != null && character.getHouse().equals(house)) {
                return MAX_SCORE;
            }
        }
        return DEFAULT_SCORE;
    }

    // Helper method to generate combinations with repetitions
    private List<List<House>> getCombinations(List<House> elements, int k) {
        List<List<House>> combinations = new ArrayList<>();
        generateCombinationsRecursive(elements, k, new ArrayList<>(), combinations);
        return combinations;
    }

    private void generateCombinationsRecursive(List<House> elements, int k, List<House> current, List<List<House>> combinations) {
        if (current.size() == k) {
            combinations.add(new ArrayList<>(current));
            return;
        }
        for (House element : elements) {
            current.add(element);
            generateCombinationsRecursive(elements, k, current, combinations);
            current.remove(current.size() - 1);
        }
    }
}
