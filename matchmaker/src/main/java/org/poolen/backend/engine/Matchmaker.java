package org.poolen.backend.engine;

import com.google.ortools.graph.LinearSumAssignment;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Character;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.poolen.backend.db.constants.Settings.MatchmakerPrioritySettings.AMBER_PRIORITIES;
import static org.poolen.backend.db.constants.Settings.MatchmakerPrioritySettings.AVENTURINE_PRIORITIES;
import static org.poolen.backend.db.constants.Settings.MatchmakerPrioritySettings.OPAL_PRIORITIES;
import static org.poolen.backend.db.constants.Settings.MatchmakerPrioritySettings.GARNET_PRIORITIES;
import static org.poolen.backend.db.constants.Settings.MatchmakerBonusSettings.HOUSE_BONUS;
import static org.poolen.backend.db.constants.Settings.MatchmakerBonusSettings.BUDDY_BONUS;
import static org.poolen.backend.db.constants.Settings.MatchmakerBonusSettings.BLACKLIST_BONUS;
import static org.poolen.backend.db.constants.Settings.MatchmakerBonusSettings.MAX_REUNION_BONUS;
import static org.poolen.backend.db.constants.Settings.MatchmakerBonusSettings.RECENCY_GRUDGE;
import static org.poolen.backend.db.constants.Settings.MatchmakerMultiplierSettings.HOUSE_SECOND_CHOICE_MULTIPLIER;
import static org.poolen.backend.db.constants.Settings.MatchmakerMultiplierSettings.HOUSE_FOURTH_CHOICE_MULTIPLIER;
import static org.poolen.backend.db.constants.Settings.MatchmakerMultiplierSettings.HOUSE_THIRD_CHOICE_MULTIPLIER;
import static org.poolen.backend.db.constants.Settings.MatchmakerMultiplierSettings.MAIN_CHARACTER_MULTIPLIER;

@Service
@Lazy
public class Matchmaker {

    private static final Logger logger = LoggerFactory.getLogger(Matchmaker.class);

    private List<Group> groups;
    private List<Player> players;

    private SettingsStore settingsStore;
    // --- Scoring Weights ---
    // The House Score is now part of this balanced system.
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


    // Constants for the initial assignment pass
    private static final double MAX_INITIAL_SCORE = 1000.0;

    // --- House Priority Map ---
    // This defines the "second best" choices for autofilling.
    private final Map<House, List<House>> housePriorityMap = new EnumMap<>(House.class);



    public Matchmaker(Store store) {
        logger.info("Matchmaker initialising...");
        this.settingsStore = store.getSettingsStore();

        logger.debug("Loading settings and populating house priority map...");
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

        logger.debug("Matchmaker settings loaded:");
        logger.debug("HOUSE_MATCH_BONUS: {}", HOUSE_MATCH_BONUS);
        logger.debug("BUDDY_MATCH_BONUS: {}", BUDDY_MATCH_BONUS);
        logger.debug("BLACKLIST_MATCH_BONUS: {}", BLACKLIST_MATCH_BONUS);
        logger.debug("RECENCY_GRUDGE_PERIOD: {}", RECENCY_GRUDGE_PERIOD);
        logger.debug("MAX_REUNION_MATCH_BONUS: {}", MAX_REUNION_MATCH_BONUS);
        logger.debug("MAIN_CHARACTER_MATCH_MULTIPLIER: {}", MAIN_CHARACTER_MATCH_MULTIPLIER);
        logger.debug("HOUSE_SECOND_CHOICE_MATCH_MULTIPLIER: {}", HOUSE_SECOND_CHOICE_MATCH_MULTIPLIER);
        logger.debug("HOUSE_THIRD_CHOICE_MATCH_MULTIPLIER: {}", HOUSE_THIRD_CHOICE_MATCH_MULTIPLIER);
        logger.debug("HOUSE_FOURTH_CHOICE_MATCH_MULTIPLIER: {}", HOUSE_FOURTH_CHOICE_MATCH_MULTIPLIER);
        logger.info("Matchmaker initialised with all settings.");
    }

    public List<Group> match() {
        logger.info("Matchmaking started for {} players and {} groups.",
                (players != null ? players.size() : 0), (groups != null ? groups.size() : 0));
        if (players == null || groups == null || players.isEmpty() || groups.isEmpty()) {
            logger.warn("Matchmaking aborted: No players or no groups provided.");
            return this.groups;
        }

        runOptimalHouseMatch();
        applyHolisticSwaps();

        logger.info("Matchmaking finished.");
        return this.groups;
    }

    private void runOptimalHouseMatch() {
        logger.info("Running optimal house match...");
        int numPlayers = this.players.size();
        int numGroups = this.groups.size();
        int baseSize = numPlayers / numGroups;
        int remainder = numPlayers % numGroups;
        logger.debug("Calculating group sizes. {} players, {} groups. Base size: {}, Remainder: {}", numPlayers, numGroups, baseSize, remainder);

        List<Integer> groupSizes = new ArrayList<>();
        for (int i = 0; i < numGroups; i++) {
            groupSizes.add(baseSize + (i < remainder ? 1 : 0));
        }
        int totalSlots = groupSizes.stream().mapToInt(Integer::intValue).sum();
        logger.debug("Total available slots: {}. Group sizes: {}", totalSlots, groupSizes);

        if (numPlayers > totalSlots) {
            logger.error("Matchmaking error: More players ({}) than available slots ({}).", numPlayers, totalSlots);
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
            logger.debug("Building cost matrix for {} players and {} total slots.", numPlayers, totalSlots);
            for (int i = 0; i < numPlayers; i++) {
                for (int j = 0; j < totalSlots; j++) {
                    Player player = this.players.get(i);
                    int groupIndex = slotToGroupMap.get(j);
                    Group group = this.groups.get(groupIndex);
                    double score = initialHouseScore(player, group);
                    double cost = MAX_INITIAL_SCORE - score;
                    logger.trace("Cost for Player '{}' -> Group '{}' (Slot {}): {} (Score: {})", player.getName(), group.getUuid(), j, cost, score);
                    assignment.addArcWithCost(i, j, (long) cost);
                }
            }

            LinearSumAssignment.Status solveStatus = assignment.solve();
            logger.info("LinearSumAssignment solve status: {}", solveStatus);
            if (solveStatus == LinearSumAssignment.Status.OPTIMAL) {
                logger.info("Optimal house match found. Assigning players to groups.");
                for (int i = 0; i < numPlayers; i++) {
                    int slotIndex = assignment.getRightMate(i);
                    if (slotIndex != -1) {
                        Player player = this.players.get(i);
                        int groupIndex = slotToGroupMap.get(slotIndex);
                        this.groups.get(groupIndex).addPartyMember(player);
                        logger.debug("Assigned player '{}' to group '{}' (slot index {}).", player.getName(), this.groups.get(groupIndex).getUuid(), slotIndex);
                    } else {
                        logger.warn("Player '{}' (index {}) was not assigned a slot.", this.players.get(i).getName(), i);
                    }
                }
            } else {
                logger.error("Optimal house match could not be found. Solve status: {}", solveStatus);
            }
        } finally {
            assignment.delete();
        }
    }

    private void applyHolisticSwaps() {
        logger.info("Applying holistic swaps to improve social scores...");
        boolean improvementFound;
        int iteration = 0;
        do {
            iteration++;
            logger.debug("Starting swap iteration {}.", iteration);
            improvementFound = false;
            for (int i = 0; i < groups.size(); i++) {
                for (int j = i + 1; j < groups.size(); j++) {
                    Group g1 = groups.get(i);
                    Group g2 = groups.get(j);
                    for (Player p1 : new ArrayList<>(g1.getParty().values())) {
                        for (Player p2 : new ArrayList<>(g2.getParty().values())) {
                            logger.trace("Checking swap between p1 '{}' (group {}) and p2 '{}' (group {}).", p1.getName(), g1.getUuid(), p2.getName(), g2.getUuid());

                            // Calculate the total "happiness" score of the two groups as they are now.
                            double currentTotalScore = calculateTotalScoreForGroup(g1) + calculateTotalScoreForGroup(g2);
                            // Calculate what the score would be if we swapped these two players.
                            double newTotalScore = calculateHypotheticalTotalScore(g1, p1, p2) + calculateHypotheticalTotalScore(g2, p2, p1);

                            logger.trace("Current score: {}. Hypothetical score: {}.", currentTotalScore, newTotalScore);
                            if (newTotalScore > currentTotalScore) {
                                g1.removePartyMember(p1);
                                g2.removePartyMember(p2);
                                g1.addPartyMember(p2);
                                g2.addPartyMember(p1);
                                logger.info("Holistic Swap: Swapped '{}' (from group {}) and '{}' (from group {}). Score improved from {} to {}.",
                                        p1.getName(), g1.getUuid(), p2.getName(), g2.getUuid(), currentTotalScore, newTotalScore);
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
        logger.info("Finished holistic swap iterations after {} loops. No further improvements found.", iteration);
    }

    private double calculateHypotheticalTotalScore(Group originalGroup, Player playerToRemove, Player playerToAdd) {
        logger.trace("Calculating hypothetical score for group '{}' with player '{}' added and '{}' removed.", originalGroup.getUuid(), playerToAdd.getName(), playerToRemove.getName());
        List<Player> hypotheticalParty = new ArrayList<>(originalGroup.getParty().values());
        hypotheticalParty.remove(playerToRemove);
        hypotheticalParty.add(playerToAdd);

        // --- The Fix! ---
        // The new Group constructor needs the list of houses from the original group.
        Group hypotheticalGroup = new Group(originalGroup.getDungeonMaster(), originalGroup.getHouses(), LocalDate.now(), null);
        hypotheticalParty.forEach(hypotheticalGroup::addPartyMember);

        return calculateTotalScoreForGroup(hypotheticalGroup);
    }

    private double calculateTotalScoreForGroup(Group group) {
        logger.trace("Calculating total score for group '{}' with {} members.", group.getUuid(), group.getParty().size());
        double totalScore = 0;
        List<Player> party = new ArrayList<>(group.getParty().values());

        // Part 1: Calculate Social Score between players
        for (int i = 0; i < party.size(); i++) {
            for (int j = i + 1; j < party.size(); j++) {
                Player p1 = party.get(i);
                Player p2 = party.get(j);
                double pairScore = 0;

                if (p1.getBlacklist().contains(p2.getUuid()) || p2.getBlacklist().contains(p1.getUuid())) {
                    pairScore += BLACKLIST_MATCH_BONUS;
                }
                if (p1.getBuddylist().contains(p2.getUuid()) || p2.getBuddylist().contains(p1.getUuid())) {
                    pairScore += BUDDY_MATCH_BONUS;
                }

                // Assuming p1.getPlayerLog() now returns Map<UUID, LocalDate>
                LocalDate lastPlayed = p1.getPlayerLog().get(p2.getUuid());
                if (lastPlayed != null) {
                    long weeksAgo = ChronoUnit.WEEKS.between(lastPlayed, LocalDate.now());
                    if (weeksAgo < RECENCY_GRUDGE_PERIOD) {
                        // Apply a sliding scale penalty. Max penalty for playing this week.
                        double reunionPenalty = MAX_REUNION_MATCH_BONUS * (1.0 - ((double) weeksAgo / RECENCY_GRUDGE_PERIOD));
                        pairScore += MAX_REUNION_MATCH_BONUS - reunionPenalty;
                        logger.trace("... (p1 '{}', p2 '{}'): Recency grudge applied. Weeks ago: {}. Penalty: {}", p1.getName(), p2.getName(), weeksAgo, reunionPenalty);
                    } else {
                        pairScore += MAX_REUNION_MATCH_BONUS;
                    }
                } else {
                    pairScore += MAX_REUNION_MATCH_BONUS; // Max bonus for never having played together
                }
                logger.trace("... (p1 '{}', p2 '{}'): Pair score: {}", p1.getName(), p2.getName(), pairScore);
                totalScore += pairScore;
            } // <-- End of the player-pair loop
        } // <-- End of the outer player loop

        // Part 2: Calculate DM Blacklist score (now runs just once per group!)
        if (group.getDungeonMaster() != null) {
            for (Player player : party) {
                if (player.getDmBlacklist().contains(group.getDungeonMaster().getUuid())) {
                    totalScore += BLACKLIST_MATCH_BONUS;
                    logger.trace("... (p '{}', DM '{}'): DM Blacklist bonus applied.", player.getName(), group.getDungeonMaster().getName());
                }
            }
        } else {
            logger.warn("Group {} has no DM. Skipping DM blacklist check.", group.getUuid());
        }


        // Part 3: Add House Preference Score
        for (Player player : party) {
            totalScore += getTieredHouseScore(player, group);
        }

        logger.trace("Total score for group '{}' calculated: {}", group.getUuid(), totalScore);
        return totalScore;
    }

    private double getTieredHouseScore(Player player, Group group) {
        if (player.getCharacters().isEmpty()) {
            logger.error("Player '{}' (UUID: {}) has no characters. Cannot calculate house score. Returning default.", player.getName(), player.getUuid());
            return HOUSE_DEFAULT_SCORE;
        }
        logger.trace("... Calculating tiered house score for player '{}'.", player.getName());

        double bestScoreForPlayer = HOUSE_DEFAULT_SCORE;
        List<House> groupHouses = group.getHouses(); // Get the list of themes for the group

        // Iterate through all of the player's characters to find their best possible score
        for (Character character : player.getCharacters()) {
            House playerHouse = character.getHouse();
            double bestScoreForThisCharacter = 0;
            logger.trace("... ... Checking character '{}' (House: {}).", character.getName(), playerHouse);

            // First, check if this character is a perfect match for ANY of the group's themes.
            boolean isPerfectMatch = groupHouses.contains(playerHouse);

            if (isPerfectMatch) {
                bestScoreForThisCharacter = HOUSE_MATCH_BONUS;
                logger.trace("... ... ... Perfect house match found. Score: {}", bestScoreForThisCharacter);
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
                                currentTieredScore = HOUSE_MATCH_BONUS * HOUSE_SECOND_CHOICE_MATCH_MULTIPLIER;
                                break;
                            case 1:
                                currentTieredScore = HOUSE_MATCH_BONUS * HOUSE_THIRD_CHOICE_MATCH_MULTIPLIER;
                                break;
                            case 2:
                                currentTieredScore = HOUSE_MATCH_BONUS * HOUSE_FOURTH_CHOICE_MATCH_MULTIPLIER;
                                break;
                            default:
                                currentTieredScore = HOUSE_DEFAULT_SCORE; // Changed from HOUSE_MATCH_BONUS, as default is 1.0
                                break;
                        }
                        if (currentTieredScore > bestTieredScore) {
                            bestTieredScore = currentTieredScore;
                            logger.trace("... ... ... Tiered match found: GroupHouse '{}', PrefIndex '{}', Score '{}'.", groupHouse, priorityIndex, currentTieredScore);
                        }
                    }
                }
                bestScoreForThisCharacter = bestTieredScore;
            }

            // Add the main character bonus if this is their first character
            if (character.isMain()) {
                bestScoreForThisCharacter += MAIN_CHARACTER_MATCH_MULTIPLIER;
                logger.trace("... ... ... Added main character bonus ({}). New score: {}", MAIN_CHARACTER_MATCH_MULTIPLIER, bestScoreForThisCharacter);
            }

            // The player's overall best score is the best they can get from any of their characters.
            if (bestScoreForThisCharacter > bestScoreForPlayer) {
                bestScoreForPlayer = bestScoreForThisCharacter;
            }
        }

        logger.trace("... Player '{}': Best score from all characters: {}.", player.getName(), bestScoreForPlayer);
        return bestScoreForPlayer;
    }

    private double initialHouseScore(Player player, Group group) {
        return getTieredHouseScore(player, group);
    }

    public List<Group> getGroups() {
        return groups;
    }

    public void setGroups(List<Group> groups) {
        if (groups != null) {
            logger.debug("Setting {} groups for matchmaker.", groups.size());
        }
        this.groups = groups;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        if (players != null) {
            logger.debug("Setting {} players for matchmaker.", players.size());
        }
        this.players = players;
    }
}
