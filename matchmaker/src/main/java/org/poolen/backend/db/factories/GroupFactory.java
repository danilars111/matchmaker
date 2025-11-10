package org.poolen.backend.db.factories;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A factory for creating Group objects, ensuring consistent creation logic.
 * This follows the Singleton pattern.
 */
public class GroupFactory {

    private static final Logger logger = LoggerFactory.getLogger(GroupFactory.class);
    private static final GroupFactory INSTANCE = new GroupFactory();

    // Private constructor to enforce the singleton pattern
    private GroupFactory() {
        logger.info("GroupFactory initialised (singleton).");
    }

    /**
     * Gets the single instance of the GroupFactory.
     * @return The singleton instance.
     */
    public static GroupFactory getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new, empty group with one or more house themes for the upcoming Friday.
     * @param dungeonMaster The player who will be the DM.
     * @param houses A list of house themes for the group.
     * @return The newly created Group object.
     */
    public Group create(Player dungeonMaster, List<House> houses) {
        String dmName = (dungeonMaster != null) ? dungeonMaster.getName() : "None";
        logger.info("Creating new group for DM '{}' with {} houses for the upcoming Friday.", dmName, houses.size());
        return create(dungeonMaster, houses, getNextFriday());
    }

    /**
     * Creates a new, empty group with one or more house themes for a specific date.
     * (Assumes the Group constructor has been updated to accept a List<House>)
     * @param dungeonMaster The player who will be the DM.
     * @param houses A list of house themes for the group.
     * @param date The specific date for the session.
     * @return The newly created Group object.
     */
    public Group create(Player dungeonMaster, List<House> houses, LocalDate date) {
        String dmName = (dungeonMaster != null) ? dungeonMaster.getName() : "None";
        logger.debug("Creating new group (DM: '{}', Houses: [{}], Date: {})",
                dmName, houses.stream().map(House::name).collect(Collectors.joining(", ")), date);
        // Note: Group constructor is not logged here, assuming it will be logged if we add logging to the Group entity itself.
        return new Group(dungeonMaster, houses, date, null);
    }

    /**
     * Creates a new group with a pre-filled party and one or more house themes for the upcoming Friday.
     * @param dungeonMaster The player who will be the DM.
     * @param houses A list of house themes for the group.
     * @param party A list of players to add to the group.
     * @return The newly created Group object.
     */
    public Group create(Player dungeonMaster, List<House> houses, String location, List<Player> party) {
        String dmName = (dungeonMaster != null) ? dungeonMaster.getName() : "None";
        logger.info("Creating new group for DM '{}' with {} houses and {} party members for the upcoming Friday.",
                dmName, houses.size(), party.size());
        return create(dungeonMaster, houses, getNextFriday(), location, party);
    }

    /**
     * Creates a new group with a pre-filled party and one or more house themes for a specific date and location.
     * @param dungeonMaster The player who will be the DM.
     * @param houses A list of house themes for the group.
     * @param date The specific date for the session.
     * @param party The specific location for the session.
     * @param party A list of players to add to the group.
     * @return The newly created Group object.
     * @throws IllegalArgumentException if the DM is also in the party list.
     */
    public Group create(Player dungeonMaster, List<House> houses, LocalDate date, String location, List<Player> party) {
        String dmName = (dungeonMaster != null) ? dungeonMaster.getName() : "None";
        logger.debug("Creating new group (DM: '{}', Houses: [{}], Date: {}, Party Size: {})",
                dmName, houses.stream().map(House::name).collect(Collectors.joining(", ")), date, party.size());

        if (dungeonMaster != null && party.stream().anyMatch(p -> p.getUuid().equals(dungeonMaster.getUuid()))) {
            String errorMsg = String.format("A Dungeon Master (%s) cannot be a player in their own group.", dmName);
            logger.warn(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        Group newGroup = new Group(dungeonMaster, houses, date, location);
        party.forEach(newGroup::addPartyMember);

        logger.info("Successfully created new group with UUID {} for DM '{}'.", newGroup.getUuid(), dmName);
        return newGroup;
    }


    /**
     * A beautiful little helper to calculate the date of the next upcoming Friday.
     * @return A LocalDate object for the next Friday.
     */
    private LocalDate getNextFriday() {
        logger.trace("Calculating next Friday's date.");
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    }
}
