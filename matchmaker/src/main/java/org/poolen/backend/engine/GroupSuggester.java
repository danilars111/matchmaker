package org.poolen.backend.engine;

import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.util.interfaces.UiUpdater;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroupSuggester {

    private static final Logger logger = LoggerFactory.getLogger(GroupSuggester.class);

    private final List<Player> dungeonMasters;
    private final List<Player> playersToMatch;
    private static final double MAX_SCORE = 10.0;
    private static final double DEFAULT_SCORE = 1.0;

    public GroupSuggester(Collection<Player> attendees, Collection<Player> dungeonMasters) {
        this.dungeonMasters = dungeonMasters.stream().toList();

        Set<Player> dmSet = new HashSet<>(this.dungeonMasters);

        this.playersToMatch = attendees.stream()
                // This filter now checks if an attendee is in our DM set.
                .filter(attendee -> !dmSet.contains(attendee))
                .collect(Collectors.toList());
        logger.info("GroupSuggester initialised with {} DMs and {} players to match.", this.dungeonMasters.size(), this.playersToMatch.size());
    }

    public List<House> suggestGroupThemes(UiUpdater updater) {
        updater.updateStatus("Suggesting group themes");
        logger.info("Suggesting group themes for {} DMs and {} players.", dungeonMasters.size(), playersToMatch.size());
        if (dungeonMasters.isEmpty() || playersToMatch.isEmpty()) {
            logger.warn("No dungeon masters or no players to match. Returning empty suggestion list.");
            return new ArrayList<>();
        }

        List<House> allPossibleHouses = Stream.of(House.values()).collect(Collectors.toList());
        int numGroupsToSuggest = dungeonMasters.size();
        logger.debug("Will suggest {} group themes from {} possible houses.", numGroupsToSuggest, allPossibleHouses.size());

        // Generate all possible combinations of house themes for the given number of groups
        List<List<House>> themeCombinations = getCombinations(allPossibleHouses, numGroupsToSuggest);
        logger.debug("Generated {} theme combinations to test.", themeCombinations.size());

        List<House> bestCombination = new ArrayList<>();
        long minCost = Long.MAX_VALUE;

        // Simulate matchmaking for each combination to find the one with the lowest potential cost
        for (List<House> combination : themeCombinations) {
            long currentCost = calculateTotalCost(this.playersToMatch, combination, updater);
            logger.trace("Calculated cost for theme combination {}: {}", combination, currentCost);
            if (currentCost < minCost) {

                updater.updateStatus("Optimal combination with cost %s".formatted(currentCost));
                minCost = currentCost;
                bestCombination = combination;
                if(currentCost == 0) {
                    break;
                }
            }
        }

        logger.info("Best theme combination found with a minimum potential cost of: {}. Combination: {}", minCost, bestCombination);
        return bestCombination;
    }

    private long calculateTotalCost(List<Player> players, List<House> themeCombination, UiUpdater updater) {
        if (players.isEmpty() || themeCombination.isEmpty()) {
            logger.warn("Cannot calculate cost: player list or theme combination is empty.");
            return Long.MAX_VALUE;
        }
        logger.debug("Calculating total cost for theme combination: {}", themeCombination);

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
        logger.trace("Calculated {} total slots with group sizes: {}", totalSlots, groupSizes);

        if (numPlayers > totalSlots) {
            logger.warn("Cannot calculate cost: More players ({}) than total slots ({}).", numPlayers, totalSlots);
            return Long.MAX_VALUE; // Safeguard
        }

        List<Integer> slotToGroupMap = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            for (int j = 0; j < groupSizes.get(i); j++) {
                slotToGroupMap.add(i);
            }
        }
        LinearSumAssignment assignment = new LinearSumAssignment();
        long totalCost = Long.MAX_VALUE;
        try {
            logger.trace("Building cost matrix for {} players and {} total slots.", numPlayers, totalSlots);
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
                logger.trace("Optimal assignment found for combination. Total cost: {}", totalCost);
            } else {
                logger.warn("Optimal assignment not found for combination {}. Solve status: {}", themeCombination, assignment.solve());
            }
        } finally {
            assignment.delete();
        }
        return totalCost;
    }

    private double calculateScoreForHouse(Player player, House house) {
        logger.trace("Calculating score for player '{}' and house '{}'.", player.getName(), house);
        for (Character character : player.getCharacters()) {
            if (character != null && character.getHouse().equals(house)) {
                logger.trace("... Found perfect match. Score: {}", MAX_SCORE);
                return MAX_SCORE;
            }
        }
        logger.trace("... No perfect match found. Score: {}", DEFAULT_SCORE);
        return DEFAULT_SCORE;
    }

    // Helper method to generate combinations with repetitions
    private List<List<House>> getCombinations(List<House> elements, int k) {
        logger.debug("Generating combinations for {} elements, k={}.", elements.size(), k);
        List<List<House>> combinations = new ArrayList<>();
        generateCombinationsRecursive(elements, k, new ArrayList<>(), combinations);
        return combinations;
    }

    private void generateCombinationsRecursive(List<House> elements, int k, List<House> current, List<List<House>> combinations) {
        if (current.size() == k) {
            combinations.add(new ArrayList<>(current));
            logger.trace("Generated combination: {}", current);
            return;
        }
        for (House element : elements) {
            current.add(element);
            generateCombinationsRecursive(elements, k, current, combinations);
            current.remove(current.size() - 1);
        }
    }
}
