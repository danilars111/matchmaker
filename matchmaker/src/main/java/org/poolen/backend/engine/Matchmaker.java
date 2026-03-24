package org.poolen.backend.engine;

import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.backend.db.store.SettingsStore;
import org.poolen.backend.db.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static org.poolen.backend.db.constants.Settings.MatchmakerPrioritySettings.*;
import static org.poolen.backend.db.constants.Settings.MatchmakerBonusSettings.*;
import static org.poolen.backend.db.constants.Settings.MatchmakerMultiplierSettings.*;

@Service
@Lazy
public class Matchmaker {

    private static final Logger logger = LoggerFactory.getLogger(Matchmaker.class);

    private List<Group> groups;
    private List<Player> players;

    private boolean randomVariance = false;

    private final SettingsStore settingsStore;
    private final double HOUSE_MATCH_BONUS;
    private final double HOUSE_DEFAULT_SCORE;
    private final double BUDDY_MATCH_BONUS;
    private final double BLACKLIST_MATCH_BONUS;
    private final double RECENCY_GRUDGE_PERIOD;
    private final double MAX_REUNION_MATCH_BONUS;
    private final double MAIN_CHARACTER_MATCH_MULTIPLIER;
    private final double HOUSE_SECOND_CHOICE_MATCH_MULTIPLIER;
    private final double HOUSE_THIRD_CHOICE_MATCH_MULTIPLIER;
    private final double HOUSE_FOURTH_CHOICE_MATCH_MULTIPLIER;
    private final double RANDOM_VARIANCE_PERCENTAGE;

    private static final double MAX_INITIAL_SCORE = 1000.0;
    private final Map<House, List<House>> housePriorityMap = new EnumMap<>(House.class);

    public Matchmaker(Store store) {
        logger.info("Matchmaker initialising...");
        this.settingsStore = store.getSettingsStore();

        housePriorityMap.put(House.GARNET, (List<House>) settingsStore.getSetting(GARNET_PRIORITIES).getSettingValue());
        housePriorityMap.put(House.AMBER, (List<House>) settingsStore.getSetting(AMBER_PRIORITIES).getSettingValue());
        housePriorityMap.put(House.AVENTURINE, (List<House>) settingsStore.getSetting(AVENTURINE_PRIORITIES).getSettingValue());
        housePriorityMap.put(House.OPAL, (List<House>) settingsStore.getSetting(OPAL_PRIORITIES).getSettingValue());

        HOUSE_MATCH_BONUS = (double) settingsStore.getSetting(HOUSE_BONUS).getSettingValue();
        HOUSE_DEFAULT_SCORE = 1.0;
        BUDDY_MATCH_BONUS = (double) settingsStore.getSetting(BUDDY_BONUS).getSettingValue();
        BLACKLIST_MATCH_BONUS = (double) settingsStore.getSetting(BLACKLIST_BONUS).getSettingValue();
        RECENCY_GRUDGE_PERIOD = (double) settingsStore.getSetting(RECENCY_GRUDGE).getSettingValue();
        MAX_REUNION_MATCH_BONUS = (double) settingsStore.getSetting(MAX_REUNION_BONUS).getSettingValue();
        MAIN_CHARACTER_MATCH_MULTIPLIER = (double) settingsStore.getSetting(MAIN_CHARACTER_MULTIPLIER).getSettingValue();
        HOUSE_SECOND_CHOICE_MATCH_MULTIPLIER = (double) settingsStore.getSetting(HOUSE_SECOND_CHOICE_MULTIPLIER).getSettingValue();
        HOUSE_THIRD_CHOICE_MATCH_MULTIPLIER = (double) settingsStore.getSetting(HOUSE_THIRD_CHOICE_MULTIPLIER).getSettingValue();
        HOUSE_FOURTH_CHOICE_MATCH_MULTIPLIER = (double) settingsStore.getSetting(HOUSE_FOURTH_CHOICE_MULTIPLIER).getSettingValue();
        RANDOM_VARIANCE_PERCENTAGE = (double) settingsStore.getSetting(VARIANCE_PERCENTAGE).getSettingValue();
    }

    public List<Group> match() {
        if (players == null || groups == null || players.isEmpty() || groups.isEmpty()) {
            logger.warn("Matchmaking aborted: No players or no groups provided.");
            return this.groups;
        }

        // --- Respecting Boundaries: Filtering ---
        // We find which players are already cozy in a locked group and leave them alone.
        Set<UUID> playersInLockedGroups = groups.stream()
                .filter(Group::isLocked)
                .flatMap(g -> g.getParty().keySet().stream())
                .collect(Collectors.toSet());

        List<Player> activePlayers = players.stream()
                .filter(p -> !playersInLockedGroups.contains(p.getUuid()))
                .collect(Collectors.toList());

        List<Group> activeGroups = groups.stream()
                .filter(g -> !g.isLocked())
                .collect(Collectors.toList());

        if (activePlayers.isEmpty() || activeGroups.isEmpty()) {
            logger.info("Nothing to match! All players are locked in or no unlocked groups available.");
            return this.groups;
        }

        logger.info("Matchmaking started for {} available players and {} unlocked groups.",
                activePlayers.size(), activeGroups.size());

        runOptimalHouseMatch(activePlayers, activeGroups);
        applyHolisticSwaps(activeGroups);

        return this.groups;
    }

    private void runOptimalHouseMatch(List<Player> activePlayers, List<Group> activeGroups) {
        int numPlayers = activePlayers.size();

        boolean hasOpenGroup = activeGroups.stream().anyMatch(g -> g.getMaxSize() == null);

        if (!hasOpenGroup) {
            int totalAvailableSlots = activeGroups.stream()
                    .mapToInt(g -> Math.max(0, g.getMaxSize() - g.getParty().size()))
                    .sum();

            if (numPlayers > totalAvailableSlots) {
                throw new IllegalStateException(String.format(
                        "Matchmaking aborted: Not enough seats! %d players for %d slots.",
                        numPlayers, totalAvailableSlots
                ));
            }
        }

        // Calculate slots available in each group
        List<Integer> groupSlots = new ArrayList<>();
        int totalAvailableSlots = 0;

        // We need to figure out how many slots to give to groups that don't have a maxSize set.
        List<Group> groupsWithoutSize = activeGroups.stream()
                .filter(g -> g.getMaxSize() == null)
                .collect(Collectors.toList());

        int definedSlots = activeGroups.stream()
                .filter(g -> g.getMaxSize() != null)
                .mapToInt(g -> Math.max(0, g.getMaxSize() - g.getParty().size()))
                .sum();

        int playersRemaining = Math.max(0, numPlayers - definedSlots);
        int baseSizeForOpenGroups = groupsWithoutSize.isEmpty() ? 0 : (playersRemaining / groupsWithoutSize.size());
        int remainderForOpenGroups = groupsWithoutSize.isEmpty() ? 0 : (playersRemaining % groupsWithoutSize.size());

        List<Integer> slotToGroupIndexMap = new ArrayList<>();
        for (int i = 0; i < activeGroups.size(); i++) {
            Group g = activeGroups.get(i);
            int capacity;
            if (g.getMaxSize() != null) {
                capacity = Math.max(0, g.getMaxSize() - g.getParty().size());
            } else {
                capacity = baseSizeForOpenGroups + (groupsWithoutSize.indexOf(g) < remainderForOpenGroups ? 1 : 0);
            }

            for (int j = 0; j < capacity; j++) {
                slotToGroupIndexMap.add(i);
                totalAvailableSlots++;
            }
        }

        if (numPlayers > totalAvailableSlots) {
            logger.error("Matchmaking catastrophe! We have {} players but only {} slots. Someone's going to be lonely!", numPlayers, totalAvailableSlots);
            // We'll proceed but some players will be ignored.
        }

        LinearSumAssignment assignment = new LinearSumAssignment();
        try {
            for (int i = 0; i < numPlayers; i++) {
                Player player = activePlayers.get(i);
                for (int j = 0; j < slotToGroupIndexMap.size(); j++) {
                    int groupIdx = slotToGroupIndexMap.get(j);
                    Group group = activeGroups.get(groupIdx);
                    double score = initialHouseScore(player, group);
                    double cost = MAX_INITIAL_SCORE - score;
                    assignment.addArcWithCost(i, j, (long) cost);
                }
            }

            if (assignment.solve() == LinearSumAssignment.Status.OPTIMAL) {
                for (int i = 0; i < numPlayers; i++) {
                    int slotIndex = assignment.getRightMate(i);
                    if (slotIndex != -1 && slotIndex < slotToGroupIndexMap.size()) {
                        int groupIdx = slotToGroupIndexMap.get(slotIndex);
                        activeGroups.get(groupIdx).addPartyMember(activePlayers.get(i));
                    }
                }
            }
        } finally {
            assignment.delete();
        }
    }

    private void applyHolisticSwaps(List<Group> activeGroups) {
        boolean improvementFound;
        do {
            improvementFound = false;
            for (int i = 0; i < activeGroups.size(); i++) {
                for (int j = i + 1; j < activeGroups.size(); j++) {
                    Group g1 = activeGroups.get(i);
                    Group g2 = activeGroups.get(j);

                    for (Player p1 : new ArrayList<>(g1.getParty().values())) {
                        for (Player p2 : new ArrayList<>(g2.getParty().values())) {
                            double currentTotalScore = calculateTotalScoreForGroup(g1) + calculateTotalScoreForGroup(g2);
                            double newTotalScore = calculateHypotheticalTotalScore(g1, p1, p2) + calculateHypotheticalTotalScore(g2, p2, p1);

                            if (newTotalScore > currentTotalScore) {
                                g1.removePartyMember(p1);
                                g2.removePartyMember(p2);
                                g1.addPartyMember(p2);
                                g2.addPartyMember(p1);
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

        Group hypotheticalGroup = new Group(null, originalGroup.getDungeonMaster(), originalGroup.getHouses(),
                originalGroup.getDate(), originalGroup.getMaxSize(), originalGroup.getLocation(), originalGroup.isLocked());
        hypotheticalParty.forEach(hypotheticalGroup::addPartyMember);

        return calculateTotalScoreForGroup(hypotheticalGroup);
    }

    private double calculateTotalScoreForGroup(Group group) {
        double totalScore = 0;
        List<Player> party = new ArrayList<>(group.getParty().values());

        for (int i = 0; i < party.size(); i++) {
            for (int j = i + 1; j < party.size(); j++) {
                Player p1 = party.get(i);
                Player p2 = party.get(j);
                double pairScore = 0;

                if (p1.getBlacklist().contains(p2.getUuid()) || p2.getBlacklist().contains(p1.getUuid())) pairScore += BLACKLIST_MATCH_BONUS;
                if (p1.getBuddylist().contains(p2.getUuid()) || p2.getBuddylist().contains(p1.getUuid())) pairScore += BUDDY_MATCH_BONUS;

                LocalDate lastPlayed = p1.getPlayerLog().get(p2.getUuid());
                if (lastPlayed != null) {
                    long weeksAgo = ChronoUnit.WEEKS.between(lastPlayed, LocalDate.now());
                    if (weeksAgo < RECENCY_GRUDGE_PERIOD) {
                        double reunionPenalty = MAX_REUNION_MATCH_BONUS * (1.0 - ((double) weeksAgo / RECENCY_GRUDGE_PERIOD));
                        pairScore += MAX_REUNION_MATCH_BONUS - reunionPenalty;
                    } else {
                        pairScore += MAX_REUNION_MATCH_BONUS;
                    }
                } else {
                    pairScore += MAX_REUNION_MATCH_BONUS;
                }
                totalScore += pairScore;
            }
        }

        if (group.getDungeonMaster() != null) {
            for (Player player : party) {
                if (player.getDmBlacklist().contains(group.getDungeonMaster().getUuid())) totalScore += BLACKLIST_MATCH_BONUS;
            }
        }

        for (Player player : party) totalScore += getTieredHouseScore(player, group);

        return totalScore;
    }

    private double getTieredHouseScore(Player player, Group group) {
        if (player.getCharacters().isEmpty()) return applyVariance(HOUSE_DEFAULT_SCORE);

        double bestScoreForPlayer = HOUSE_DEFAULT_SCORE;
        List<House> groupHouses = group.getHouses();

        for (var character : player.getCharacters()) {
            House playerHouse = character.getHouse();
            double bestScoreForThisCharacter;

            if (groupHouses.contains(playerHouse)) {
                bestScoreForThisCharacter = HOUSE_MATCH_BONUS;
            } else {
                double bestTieredScore = HOUSE_DEFAULT_SCORE;
                List<House> preferences = housePriorityMap.get(playerHouse);
                if (preferences != null) {
                    for (House groupHouse : groupHouses) {
                        double currentTieredScore;
                        int priorityIndex = preferences.indexOf(groupHouse);
                        switch (priorityIndex) {
                            case 0 -> currentTieredScore = HOUSE_MATCH_BONUS * HOUSE_SECOND_CHOICE_MATCH_MULTIPLIER;
                            case 1 -> currentTieredScore = HOUSE_MATCH_BONUS * HOUSE_THIRD_CHOICE_MATCH_MULTIPLIER;
                            case 2 -> currentTieredScore = HOUSE_MATCH_BONUS * HOUSE_FOURTH_CHOICE_MATCH_MULTIPLIER;
                            default -> currentTieredScore = HOUSE_DEFAULT_SCORE;
                        }
                        if (currentTieredScore > bestTieredScore) bestTieredScore = currentTieredScore;
                    }
                }
                bestScoreForThisCharacter = bestTieredScore;
            }

            if (character.isMain()) bestScoreForThisCharacter *= MAIN_CHARACTER_MATCH_MULTIPLIER;
            if (bestScoreForThisCharacter > bestScoreForPlayer) bestScoreForPlayer = bestScoreForThisCharacter;
        }

        return applyVariance(bestScoreForPlayer);
    }

    private double initialHouseScore(Player player, Group group) {
        return getTieredHouseScore(player, group);
    }

    /**
     * Applies random variance to a score if the feature is enabled.
     * The variance is a percentage range (+/-) of the original score.
     */
    private double applyVariance(double score) {
        if (!randomVariance) return score;

        // Convert percentage to decimal (e.g., 10.0 -> 0.10)
        double varianceDecimal = RANDOM_VARIANCE_PERCENTAGE / 100.0;

        // Generate a random factor between -varianceDecimal and +varianceDecimal
        // ThreadLocalRandom is better for concurrent environments
        double randomFactor = (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * varianceDecimal;

        return score * (1.0 + randomFactor);
    }

    public List<Group> getGroups() { return groups; }
    public void setGroups(List<Group> groups) { this.groups = groups; }
    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }
    public boolean isRandomVariance() { return randomVariance; }
    public void setRandomVariance(boolean randomVariance) { this.randomVariance = randomVariance; }
}