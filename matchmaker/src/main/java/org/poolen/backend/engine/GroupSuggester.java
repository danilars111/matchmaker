package org.poolen.backend.engine;

import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GroupSuggester {
    private final List<Player> attendingPlayers;
    private final List<Player> availableDMs;
    private final List<House> allPossibleHouses;
    private static final double MAX_SCORE = 10.0; // The highest score a player can get.
    private static final double DEFAULT_SCORE = 1.0; // The score for a non-matching player.

    public GroupSuggester(List<Player> allAttendees) {
        Map<Boolean, List<Player>> partitionedAttendees = allAttendees.stream()
                .collect(Collectors.partitioningBy(Player::isDungeonMaster));

        this.availableDMs = partitionedAttendees.getOrDefault(true, List.of());
        this.attendingPlayers = partitionedAttendees.getOrDefault(false, List.of());
        this.allPossibleHouses = Arrays.stream(House.values()).toList();
    }

    /**
     * Suggests the optimal set of house themes by simulating the matchmaker for all
     * possible theme combinations and finding the one with the lowest potential cost.
     * @return A list of House themes that will result in the best possible player assignments.
     */
    public List<House> suggestGroupThemes() {
        int numberOfGroupsToForm = availableDMs.size();
        if (numberOfGroupsToForm == 0 || attendingPlayers.isEmpty()) {
            return List.of();
        }

        List<List<House>> allCombinations = generateCombinations(allPossibleHouses, numberOfGroupsToForm);

        long lowestCost = Long.MAX_VALUE;
        List<House> bestCombination = new ArrayList<>();

        // Iterate through every possible combination of group themes
        for (List<House> combination : allCombinations) {
            // Calculate the potential optimal cost for this combination
            long currentCost = calculatePotentialCost(combination);

            if (currentCost < lowestCost) {
                lowestCost = currentCost;
                bestCombination = combination;
            }
        }

        return bestCombination;
    }

    /**
     * Simulates a player assignment for a given combination of group themes and returns the optimal cost.
     * This uses the same logic as your Matchmaker class.
     * @param themeCombination A potential list of group themes, e.g., [AMBER, OPAL, OPAL].
     * @return The lowest possible assignment cost for this theme setup.
     */
    private long calculatePotentialCost(List<House> themeCombination) {
        int numPlayers = attendingPlayers.size();
        int numSlots = themeCombination.size() * 4; // Assuming 4 players per group

        LinearSumAssignment assignment = new LinearSumAssignment();

        for (int i = 0; i < numPlayers; i++) {
            Player player = attendingPlayers.get(i);
            for (int j = 0; j < numSlots; j++) {
                House groupHouse = themeCombination.get(j / 4);
                double score = calculateScoreForPlayer(player, groupHouse);
                double cost = MAX_SCORE - score;
                assignment.addArcWithCost(i, j, (long) cost);
            }
        }

        if (assignment.solve() == LinearSumAssignment.Status.OPTIMAL) {
            return assignment.getOptimalCost();
        }

        return Long.MAX_VALUE; // Return a high cost if no solution is found
    }

    private double calculateScoreForPlayer(Player player, House house) {
        for (Character character : player.getCharacters()) {
            if (character != null && character.getHouse().equals(house)) {
                return MAX_SCORE;
            }
        }
        return DEFAULT_SCORE;
    }

    /**
     * Helper method to generate all possible combinations (with repetition) of group themes.
     * @param houses The list of available house themes.
     * @param count The number of groups to form.
     * @return A list of all possible theme combinations.
     */
    private List<List<House>> generateCombinations(List<House> houses, int count) {
        List<List<House>> result = new ArrayList<>();
        generateCombinationsRecursive(houses, count, new ArrayList<>(), result);
        return result;
    }

    private void generateCombinationsRecursive(List<House> houses, int count, List<House> current, List<List<House>> result) {
        if (current.size() == count) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (House house : houses) {
            current.add(house);
            generateCombinationsRecursive(houses, count, current, result);
            current.remove(current.size() - 1);
        }
    }
}
