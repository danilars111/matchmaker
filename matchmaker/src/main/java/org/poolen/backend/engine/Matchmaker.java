package org.poolen.backend.engine;

import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.constants.Settings;
import org.poolen.backend.db.entities.Character;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.SettingsStore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class Matchmaker {
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
    private static final double MAX_INITIAL_SCORE = 1000.0;
    private static final double DEFAULT_INITIAL_SCORE = 10.0;

    // --- House Priority Map ---
    // This defines the "second best" choices for autofilling.
    private static final Map<House, List<House>> housePriorityMap = new EnumMap<>(House.class);
    static {
        housePriorityMap.put(House.GARNET, List.of(House.AMBER, House.OPAL, House.AVENTURINE));
        housePriorityMap.put(House.AMBER, List.of(House.GARNET, House.OPAL, House.AVENTURINE));
        housePriorityMap.put(House.OPAL, List.of(House.AMBER, House.AVENTURINE, House.GARNET));
        housePriorityMap.put(House.AVENTURINE, List.of(House.OPAL, House.AMBER,  House.GARNET));
    }


    public Matchmaker(List<Group> groups, Map<UUID, Player> attendingPlayers) {
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

        // --- The Fix! ---
        // The new Group constructor needs the list of houses from the original group.
        Group hypotheticalGroup = new Group(originalGroup.getDungeonMaster(), originalGroup.getHouses(), LocalDate.now());
        hypotheticalParty.forEach(hypotheticalGroup::addPartyMember);

        return calculateTotalScoreForGroup(hypotheticalGroup);
    }

    private double calculateTotalScoreForGroup(Group group) {
        double totalScore = 0;
        List<Player> party = new ArrayList<>(group.getParty().values());

        // Part 1: Calculate Social Score between players
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

                Date lastPlayed = p1.getPlayerLog().get(p2.getUuid());
                final double RECENCY_GRUDGE = settingsStore.getSettingsMap().get(Settings.RECENCY_GRUDGE);
                final double MAX_REUNION_BONUS = settingsStore.getSetting(Settings.MAX_REUNION_BONUS);
                if (lastPlayed != null) {
                    long weeksAgo = ChronoUnit.WEEKS.between(
                            lastPlayed.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                            LocalDate.now()
                    );
                    if (weeksAgo < RECENCY_GRUDGE) {
                        // Apply a sliding scale penalty. Max penalty for playing this week.
                        double reunionPenalty = MAX_REUNION_BONUS * (1.0 - ((double) weeksAgo / RECENCY_GRUDGE));
                        totalScore += MAX_REUNION_BONUS - reunionPenalty;
                    } else {
                        totalScore += MAX_REUNION_BONUS;
                    }
                } else {
                    totalScore += MAX_REUNION_BONUS;
                }
            } // <-- End of the player-pair loop
        } // <-- End of the outer player loop

        // Part 2: Calculate DM Blacklist score (now runs just once per group!)
        for (Player player : party) {
            if (player.getDmBlacklist().containsKey(group.getDungeonMaster().getUuid())) {
                totalScore += BLACKLIST_PENALTY;
            }
        }

        // Part 3: Add House Preference Score
        for (Player player : party) {
            totalScore += getTieredHouseScore(player, group);
        }

        return totalScore;
    }

    private double getTieredHouseScore(Player player, Group group) {
        if (player.getCharacters().isEmpty()) {
            throw new RuntimeException("Player's need to have a character");
        }

        double bestScoreForPlayer = HOUSE_DEFAULT_SCORE;
        List<House> groupHouses = group.getHouses(); // Get the list of themes for the group

        // Iterate through all of the player's characters to find their best possible score
        for (int i = 0; i < player.getCharacters().size(); i++) {
            Character character = player.getCharacters().get(i);
            House playerHouse = character.getHouse();

            double bestScoreForThisCharacter = 0;

            // First, check if this character is a perfect match for ANY of the group's themes.
            boolean isPerfectMatch = groupHouses.contains(playerHouse);

            if (isPerfectMatch) {
                bestScoreForThisCharacter = HOUSE_MATCH_BONUS;
            } else {
                // If not a perfect match, find the best possible tiered score.
                double bestTieredScore = HOUSE_DEFAULT_SCORE;
                List<House> preferences = housePriorityMap.get(playerHouse);
                if (preferences != null) {
                    // Check against each of the group's houses to find the best secondary match.
                    for (House groupHouse : groupHouses) {
                        double currentTieredScore = 0;
                        int priorityIndex = preferences.indexOf(groupHouse);
                        switch (priorityIndex) {
                            case 0:
                                currentTieredScore = HOUSE_MATCH_BONUS * settingsStore.getSetting(Settings.HOUSE_SECOND_CHOICE_MULTIPLIER);
                                break;
                            case 1:
                                currentTieredScore = HOUSE_MATCH_BONUS * settingsStore.getSetting(Settings.HOUSE_THIRD_CHOICE_MULTIPLIER);
                                break;
                            case 2:
                                currentTieredScore = HOUSE_MATCH_BONUS * settingsStore.getSetting(Settings.HOUSE_FOURTH_CHOICE_MULTIPLIER);
                                break;
                            default:
                                currentTieredScore = HOUSE_DEFAULT_SCORE;
                                break;
                        }
                        if (currentTieredScore > bestTieredScore) {
                            bestTieredScore = currentTieredScore;
                        }
                    }
                }
                bestScoreForThisCharacter = bestTieredScore;
            }

            // Add the main character bonus if this is their first character
            if (i == 0) {
                bestScoreForThisCharacter += settingsStore.getSetting(Settings.MAIN_CHARACTER_MULTIPLIER);
            }

            // The player's overall best score is the best they can get from any of their characters.
            if (bestScoreForThisCharacter > bestScoreForPlayer) {
                bestScoreForPlayer = bestScoreForThisCharacter;
            }
        }

        return bestScoreForPlayer;
    }

    private double initialHouseScore(Player player, Group group) {
        return getTieredHouseScore(player, group);
    }
}

