package org.poolen.frontend.gui.components.views.tables.rosters;

import javafx.scene.control.TableRow;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.views.tables.rosters.BaseRosterTableView;
import org.poolen.frontend.gui.interfaces.PlayerUpdateListener;

/**
 * The new abstract base class for our player tables.
 * It contains all the common functionality and delegates specific
 * implementation details (like columns, filters, and styling) to its children.
 */
public abstract class PlayerRosterTableView extends BaseRosterTableView<Player>{

    public PlayerRosterTableView() {
        super();
        this.searchField.setPromptText("Search by name or UUID...");
    }

    /**
     * This beautiful logic for colouring the rows is now neatly contained in its own
     * override, keeping it separate from the double-click logic which now lives
     * in the parent class. So much tidier, my love!
     */
    @Override
    protected void styleRow(TableRow<Player> row, Player player, boolean empty) {
        // Always clear the style first to handle recycling
        row.setStyle("");

        if (player != null && !empty) {
            // Priority 1: Player has no characters at all (needs attention)
            if (player.getCharacters().isEmpty()) {
                row.setStyle("-fx-background-color: #FFEBEE;"); // Light Red
                // Priority 2: Player has characters, but no main (needs attention)
            } else if (player.getMainCharacter() == null) {
                row.setStyle("-fx-background-color: #FFF8E1;"); // Light Amber
                // Priority 3: Player has exactly one character (just for info)
            } else if (player.getCharacters().size() == 1) {
                row.setStyle("-fx-background-color: #E3F2FD;"); // Light Blue
            }
        }
    }

/*    *//**
     * The listener method for when another part of the app updates a player.
     * This is also common, so it lives here.
     *//*
    @Override
    public void onPlayerUpdate() {
        // A player's character list might have changed, which affects our row coloring.
        // We just need to refresh the visual state of the table, not reload all the data.
        table.refresh();
    }*/

    public Player getSelectedPlayer() {
        return getSelectedItem();
    }
}

