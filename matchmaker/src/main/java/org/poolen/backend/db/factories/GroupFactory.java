package org.poolen.backend.db.factories;

import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * A factory for creating Group objects, ensuring consistent creation logic.
 * This follows the Singleton pattern.
 */
public class GroupFactory {

    private static final GroupFactory INSTANCE = new GroupFactory();

    // Private constructor to enforce the singleton pattern
    private GroupFactory() {}

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
        return new Group(dungeonMaster, houses, date);
    }

    /**
     * Creates a new group with a pre-filled party and one or more house themes for the upcoming Friday.
     * @param dungeonMaster The player who will be the DM.
     * @param houses A list of house themes for the group.
     * @param party A list of players to add to the group.
     * @return The newly created Group object.
     */
    public Group create(Player dungeonMaster, List<House> houses, List<Player> party) {
        return create(dungeonMaster, houses, getNextFriday(), party);
    }

    /**
     * Creates a new group with a pre-filled party and one or more house themes for a specific date.
     * @param dungeonMaster The player who will be the DM.
     * @param houses A list of house themes for the group.
     * @param date The specific date for the session.
     * @param party A list of players to add to the group.
     * @return The newly created Group object.
     * @throws IllegalArgumentException if the DM is also in the party list.
     */
    public Group create(Player dungeonMaster, List<House> houses, LocalDate date, List<Player> party) {
        if (party.stream().anyMatch(p -> p.getUuid().equals(dungeonMaster.getUuid()))) {
            throw new IllegalArgumentException("A Dungeon Master cannot be a player in their own group, you cheeky thing!");
        }

        Group newGroup = new Group(dungeonMaster, houses, date);
        party.forEach(newGroup::addPartyMember);

        return newGroup;
    }


    /**
     * A beautiful little helper to calculate the date of the next upcoming Friday.
     * @return A LocalDate object for the next Friday.
     */
    private LocalDate getNextFriday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
    }
}

