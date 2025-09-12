package org.poolen.frontend.gui.components.views;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import org.poolen.backend.db.entities.Group;
import org.poolen.frontend.gui.components.views.tables.GroupTableView;

import java.util.List;
import java.util.function.Consumer;

/**
 * A reusable view component that displays multiple groups in a grid layout.
 */
public class GroupDisplayView extends ScrollPane {

    private final GridPane groupGrid;
    private Consumer<Group> onGroupEditHandler;

    public GroupDisplayView() {
        super();
        this.groupGrid = new GridPane();
        groupGrid.setHgap(10);
        groupGrid.setVgap(10);
        groupGrid.setPadding(new Insets(10));

        this.setContent(groupGrid);
        this.setFitToWidth(true);
    }

    /**
     * Clears the current display and populates the grid with a new list of groups.
     * @param groups The list of groups to display.
     */
    public void updateGroups(List<Group> groups) {
        groupGrid.getChildren().clear();
        int col = 0;
        int row = 0;
        final int MAX_COLS = 2; // We can have two beautiful tables side-by-side

        for (Group group : groups) {
            GroupTableView groupCard = new GroupTableView();
            groupCard.setGroup(group);
            // We tell each card what to do when its edit button is clicked.
            if (onGroupEditHandler != null) {
                groupCard.setOnEditAction(onGroupEditHandler);
            }
            // --- Our beautiful alignment fix! ---
            GridPane.setValignment(groupCard, VPos.TOP);
            groupGrid.add(groupCard, col, row);

            col++;
            if (col >= MAX_COLS) {
                col = 0;
                row++;
            }
        }
    }

    /**
     * Sets the master handler for when any group's edit button is clicked.
     * @param handler The action to perform, accepting the group to be edited.
     */
    public void setOnGroupEdit(Consumer<Group> handler) {
        this.onGroupEditHandler = handler;
    }
}

