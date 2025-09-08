package org.poolen.backend.engine;

import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.SettingsStore;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class HybridMatchmaker {
    private List<Group> groups;
    private final List<Player> players;

    private static SettingsStore settingsStore = SettingsStore.getInstance();
    // --- Scoring Weights ---
    // The House Score is now part of this balanced system.
    private static final double HOUSE_MATCH_BONUS = settingsStore.getSetting(Settings.HOUSE_BONUS);
    private static final double HOUSE_DEFAULT_SCORE = 1.0;
    private static final double BUDDY_BONUS = settingsStore.getSetting(Settings.BUDDY_BONUS);
    private static final double BLACKLIST_PENALTY = settingsStore.getSetting(Settings.BLACKLIST_BONUS);

    // Constants for the initial assignment pass
    private static final double MAX_INITIAL_SCORE = 100.0;
    private static final double DEFAULT_INITIAL_SCORE = 10.0;


    public HybridMatchmaker(List<Group> groups, Map<UUID, Player> attendingPlayers) {
        this.groups = new ArrayList<>(groups);
        this.players = attendingPlayers.values().stream()
                .filter(p -> !p.isDungeonMaster())
                .collect(Collectors.toList());
    }

    public List<Group> match() {
        if (players.isEmpty() || groups.isEmpty()) {
            return this.groups;
        }

        runOptimalHouseMatch();
        applyHolisticSwaps();

        return this.groups;
    }

    private void runOptimalHouseMatch() {
        int numPlayers = this.players.size();
        int numGroups = this.groups.size();
        int baseSize = numPlayers / numGroups;
        int remainder = numPlayers % numGroups;

        List<Integer> groupSizes = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            groupSizes.add(baseSize + (i < remainder ? 1 : 0));
        }
        int totalSlots = groupSizes.stream().mapToInt(Integer::intValue).sum();

        if (numPlayers > totalSlots) {
            System.err.println("Error: More players than available slots.");
            return;
        }

        List<Integer> slotToGroupMap = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            for (int j = 0; j < groupSizes.get(i); j++) {
                slotToGroupMap.add(i);
            }
        }

        LinearSumAssignment assignment = new LinearSumAssignment();
        try {
            for (int i = 0; i < numPlayers; i++) {
                for (int j = 0; j < totalSlots; j++) {
                    Player player = this.players.get(i);
                    int groupIndex = slotToGroupMap.get(j);
                    Group group = this.groups.get(groupIndex);
                    double score = initialHouseScore(player, group);
                    double cost = MAX_INITIAL_SCORE - score;
                    assignment.addArcWithCost(i, j, (long) cost);
                }
            }

            if (assignment.solve() == LinearSumAssignment.Status.OPTIMAL) {
                for (int i = 0; i < numPlayers; i++) {
                    int slotIndex = assignment.getRightMate(i);
                    if (slotIndex != -1) {
                        Player player = this.players.get(i);
                        int groupIndex = slotToGroupMap.get(slotIndex);
                        this.groups.get(groupIndex).addPartyMember(player);
                    }
                }
            }
        } finally {
            assignment.delete();
        }
    }

    private void applyHolisticSwaps() {
        boolean improvementFound;
        do {
            improvementFound = false;
            for (int i = 0; i < groups.size(); i++) {
                for (int j = i + 1; j < groups.size(); j++) {
                    Group g1 = groups.get(i);
                    Group g2 = groups.get(j);
                    for (Player p1 : new ArrayList<>(g1.getParty().values())) {
                        for (Player p2 : new ArrayList<>(g2.getParty().values())) {

                            // Calculate the total "happiness" score of the two groups as they are now.
                            double currentTotalScore = calculateTotalScoreForGroup(g1) + calculateTotalScoreForGroup(g2);
                            // Calculate what the score would be if we swapped these two players.
                            double newTotalScore = calculateHypotheticalTotalScore(g1, p1, p2) + calculateHypotheticalTotalScore(g2, p2, p1);

                            if (newTotalScore > currentTotalScore) {
                                g1.removePartyMember(p1);
                                g2.removePartyMember(p2);
                                g1.addPartyMember(p2);
                                g2.addPartyMember(p1);
                                System.out.println("Holistic Swap: Swapped " + p1.getName() + " and " + p2.getName() + ".");
                                improvementFound = true;
                                break;
                            }
                        }
                        if (improvementFound) break;
                    }
                    if (improvementFound) break;
                }
                if (improvementFound) break;
            }
        } while (improvementFound);
    }

    private double calculateHypotheticalTotalScore(Group originalGroup, Player playerToRemove, Player playerToAdd) {
        List<Player> hypotheticalParty = new ArrayList<>(originalGroup.getParty().values());
        hypotheticalParty.remove(playerToRemove);
        hypotheticalParty.add(playerToAdd);

        Group hypotheticalGroup = new Group(originalGroup.getDungeonMaster(), originalGroup.getHouse(), new Date());
        hypotheticalParty.forEach(hypotheticalGroup::addPartyMember);

        return calculateTotalScoreForGroup(hypotheticalGroup);
    }

    private double calculateTotalScoreForGroup(Group group) {
        double totalScore = 0;
        List<Player> party = new ArrayList<>(group.getParty().values());

        // Part 1: Calculate Social Score
        for (int i = 0; i < party.size(); i++) {
            for (int j = i + 1; j < party.size(); j++) {
                Player p1 = party.get(i);
                Player p2 = party.get(j);

                if (p1.getBlacklist().containsKey(p2.getUuid()) || p2.getBlacklist().containsKey(p1.getUuid())) {
                    totalScore += BLACKLIST_PENALTY;
                }
                if (p1.getBuddylist().containsKey(p2.getUuid()) || p2.getBuddylist().containsKey(p1.getUuid())) {
                    totalScore += BUDDY_BONUS;
                }
                if (p1.getRecencyLog().containsKey(p2.getUuid())) {
                    totalScore -= p1.getRecencyLog().get(p2.getUuid());
                }
                if (p2.getRecencyLog().containsKey(p1.getUuid())) {
                    totalScore -= p2.getRecencyLog().get(p1.getUuid());
                }
            }
        }
        for (Player player : party) {
            if (player.getDmBlacklist().containsKey(group.getDungeonMaster().getUuid())) {
                totalScore += BLACKLIST_PENALTY;
            }
        }

        // Part 2: Add House Preference Score
        for (Player player : party) {
            totalScore += houseScore(player, group);
        }

        return totalScore;
    }

    // This is for the swapping logic, using the balanced weights
    private double houseScore(Player player, Group group) {
        for (Character character : player.getCharacters()) {
            if (character != null && character.getHouse().equals(group.getHouse())) {
                return HOUSE_MATCH_BONUS;
            }
        }
        return HOUSE_DEFAULT_SCORE;
    }

    // This is for the initial, house-focused assignment only
    private double initialHouseScore(Player player, Group group) {
        for (Character character : player.getCharacters()) {
            if (character != null && character.getHouse().equals(group.getHouse())) {
                return MAX_INITIAL_SCORE;
            }
        }
        return DEFAULT_INITIAL_SCORE;
    }
}
