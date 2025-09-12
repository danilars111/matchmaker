package org.poolen.frontend.gui.interfaces;

import org.poolen.backend.db.entities.Player;

/**
 * A simple functional interface for components that need to request permission
 * before finalizing the selection of a Dungeon Master.
 * It returns true if the selection is allowed, and false if it should be vetoed.
 */
@FunctionalInterface
public interface DmSelectRequestHandler {
    boolean onDmSelectRequest(Player selectedDm);
}
