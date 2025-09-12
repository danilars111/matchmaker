package org.poolen.frontend.gui.components.views;

import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.poolen.backend.db.entities.Group;
import org.poolen.frontend.gui.components.views.tables.GroupTableView;
import org.poolen.frontend.gui.interfaces.PlayerMoveHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A reusable view component that displays multiple groups in a responsive grid layout.
 */
public class GroupDisplayView extends ScrollPane {

    private final GridPane groupGrid;
    private Consumer<Group> onGroupEditHandler;
    private Consumer<Group> onGroupDeleteHandler;
    private PlayerMoveHandler onPlayerMoveHandler; // Our new handler!
    private List<Group> currentGroups = new ArrayList<>();
    private static final double ESTIMATED_CARD_WIDTH = 350.0;
    private int lastColumnCount = -1;

    public GroupDisplayView() {
        super();
        this.groupGrid = new GridPane();
        groupGrid.setHgap(10);
        groupGrid.setVgap(10);
        groupGrid.setPadding(new Insets(10));

        this.setContent(groupGrid);
        this.setFitToWidth(true);

        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) {
                updateGridLayout(newVal.doubleValue());
            }
        });
    }

    public void updateGroups(List<Group> groups) {
        this.currentGroups = groups;
        this.lastColumnCount = -1;
        updateGridLayout(this.getWidth());
    }

    private void updateGridLayout(double currentWidth) {
        if (currentGroups == null || currentWidth <= 0) return;

        int newMaxCols = (int) (currentWidth / ESTIMATED_CARD_WIDTH);
        if (newMaxCols < 1) newMaxCols = 1;
        if (currentGroups.size() > 0) {
            newMaxCols = Math.min(newMaxCols, currentGroups.size());
        }

        if (newMaxCols == lastColumnCount) return;
        this.lastColumnCount = newMaxCols;

        groupGrid.getChildren().clear();
        groupGrid.getColumnConstraints().clear();

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
            if (onGroupEditHandler != null) groupCard.setOnEditAction(onGroupEditHandler);
            if (onGroupDeleteHandler != null) groupCard.setOnDeleteAction(onGroupDeleteHandler);
            if (onPlayerMoveHandler != null) groupCard.setOnPlayerMove(onPlayerMoveHandler); // We pass it along!
            GridPane.setValignment(groupCard, VPos.TOP);
            groupGrid.add(groupCard, col, row);

            col++;
            if (col >= newMaxCols) {
                col = 0;
                row++;
            }
        }
    }

    public void setOnGroupEdit(Consumer<Group> handler) {
        this.onGroupEditHandler = handler;
    }

    public void setOnGroupDelete(Consumer<Group> handler) {
        this.onGroupDeleteHandler = handler;
    }

    public void setOnPlayerMove(PlayerMoveHandler handler) {
        this.onPlayerMoveHandler = handler;
    }
}
