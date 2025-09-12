package org.poolen.frontend.gui.interfaces;

/**
 * A simple interface for components that need to be notified
 * when the list of players or their details have changed.
 */
@FunctionalInterface
public interface PlayerUpdateListener {
    void onPlayerUpdate();
}
