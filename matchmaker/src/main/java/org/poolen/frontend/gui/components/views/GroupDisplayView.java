package org.poolen.frontend.gui.components.views;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.poolen.backend.db.entities.Group;
import org.poolen.frontend.gui.components.views.tables.GroupTableView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A reusable view component that displays multiple groups in a responsive grid layout.
 */
public class GroupDisplayView extends ScrollPane {

    private final GridPane groupGrid;
    private Consumer<Group> onGroupEditHandler;
    private Consumer<Group> onGroupDeleteHandler; // Our new handler!
    private List<Group> currentGroups = new ArrayList<>();
    private static final double ESTIMATED_CARD_WIDTH = 350.0; // A sensible estimate for a card's width
    private int lastColumnCount = -1; // We'll use this to prevent unnecessary, flashy redraws!

    public GroupDisplayView() {
        super();
        this.groupGrid = new GridPane();
        groupGrid.setHgap(10);
        groupGrid.setVgap(10);
        groupGrid.setPadding(new Insets(10));

        this.setContent(groupGrid);
        this.setFitToWidth(true);

        // We'll listen for width changes to magically reflow our grid!
        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) {
                updateGridLayout(newVal.doubleValue());
            }
        });
    }

    /**
     * Stores a new list of groups and triggers a layout update.
     * @param groups The list of groups to display.
     */
    public void updateGroups(List<Group> groups) {
        this.currentGroups = groups;
        // By resetting this, we force the grid layout to recalculate and redraw.
        this.lastColumnCount = -1;
        updateGridLayout(this.getWidth());
    }

    /**
     * A much cleverer method that only redraws the grid when the number of columns
     * needs to change, preventing that nasty flashing effect!
     * @param currentWidth The current width of this component.
     */
    private void updateGridLayout(double currentWidth) {
        if (currentGroups == null || currentWidth <= 0) return;

        // We calculate how many columns can fit, ensuring at least one!
        int newMaxCols = (int) (currentWidth / ESTIMATED_CARD_WIDTH);
        if (newMaxCols < 1) newMaxCols = 1;

        // We won't create more columns than we have groups!
        if (currentGroups.size() > 0) {
            newMaxCols = Math.min(newMaxCols, currentGroups.size());
        }

        // If the number of columns hasn't changed, we do nothing! No more flashing!
        if (newMaxCols == lastColumnCount) return;
        this.lastColumnCount = newMaxCols;

        groupGrid.getChildren().clear();
        groupGrid.getColumnConstraints().clear();

        // --- Our beautiful new resizing logic! ---
        // We tell each column that it's allowed to grow to fill any extra space.
        for (int i = 0; i < newMaxCols; i++) {
            ColumnConstraints colConst = new ColumnConstraints();
            colConst.setHgrow(Priority.ALWAYS);
            groupGrid.getColumnConstraints().add(colConst);
        }

        int col = 0;
        int row = 0;

        for (Group group : currentGroups) {
            GroupTableView groupCard = new GroupTableView();
            groupCard.setGroup(group);
            if (onGroupEditHandler != null) {
                groupCard.setOnEditAction(onGroupEditHandler);
            }
            if (onGroupDeleteHandler != null) {
                groupCard.setOnDeleteAction(onGroupDeleteHandler);
            }
            GridPane.setValignment(groupCard, VPos.TOP);
            groupGrid.add(groupCard, col, row);

            col++;
            if (col >= newMaxCols) { // We use our beautiful, dynamic number!
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

    /**
     * Sets the master handler for when any group's delete button is clicked.
     * @param handler The action to perform, accepting the group to be deleted.
     */
    public void setOnGroupDelete(Consumer<Group> handler) {
        this.onGroupDeleteHandler = handler;
    }
}

