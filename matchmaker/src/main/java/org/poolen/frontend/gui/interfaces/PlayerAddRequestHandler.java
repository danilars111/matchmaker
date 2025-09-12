package org.poolen.frontend.gui.interfaces;

import org.poolen.backend.db.entities.Player;

/**
 * A functional interface for handling a request to add a player to a group.
 * The handler is responsible for any logic (like checking for reassignments)
 * and should return true if the player was successfully added, or false if the action was cancelled.
 */
@FunctionalInterface
public interface PlayerAddRequestHandler {
    boolean onPlayerAddRequest(Player player);
}
