package org.poolen.frontend.gui.components.views.tables;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A beautiful, self-contained and collapsible "card" for displaying all the details of a single group.
 */
public class GroupTableView extends TitledPane {

    private final TableView<Player> partyTable;
    private final Label dateLabel;
    private final Button editButton;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private Group currentGroup;

    public GroupTableView() {
        super();

        // --- Information Header ---
        dateLabel = new Label();
        editButton = new Button("Edit");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox infoHeader = new HBox(5, dateLabel, spacer, editButton);
        infoHeader.setAlignment(Pos.CENTER_LEFT);
        infoHeader.setPadding(new Insets(5, 10, 0, 10));

        // --- Party Roster Table ---
        partyTable = new TableView<>();
        TableColumn<Player, Void> rowNumCol = new TableColumn<>("#");
        rowNumCol.setSortable(false);
        rowNumCol.setPrefWidth(40);
        rowNumCol.setMaxWidth(40);
        rowNumCol.setMinWidth(40);
        rowNumCol.setStyle("-fx-alignment: CENTER;");
        rowNumCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
            }
        });

        TableColumn<Player, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));

        partyTable.getColumns().addAll(rowNumCol, nameCol);
        partyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        partyTable.setPrefHeight(150);

        // --- Final Layout ---
        VBox contentBox = new VBox(10, infoHeader, partyTable);
        contentBox.setPadding(new Insets(10));
        contentBox.setStyle("-fx-background-color: #f9f9f9;");


        this.setContent(contentBox);
        this.setCollapsible(true);
        this.setExpanded(true);
    }

    /**
     * Populates the entire component with the details of a given group.
     * @param group The group to display.
     */
    public void setGroup(Group group) {
        this.currentGroup = group;
        // --- Set the title ---
        String themes = group.getHouses().stream().map(Enum::toString).collect(Collectors.joining(", "));
        String dmName = group.getDungeonMaster() != null ? group.getDungeonMaster().getName() : "N/A";
        this.setText("%s %s (%s)".formatted(dmName, themes.isEmpty() ? "N/A" : themes, group.getParty().size()));

        String date = group.getDate() != null ? group.getDate().format(dateFormatter) : "N/A";
        dateLabel.setText(date);

        // --- Populate the party table ---
        partyTable.setItems(FXCollections.observableArrayList(group.getParty().values()));
    }

    /**
     * Sets the action to be performed when the edit button is clicked.
     * @param onEdit A consumer that accepts the group to be edited.
     */
    public void setOnEditAction(Consumer<Group> onEdit) {
        editButton.setOnAction(e -> {
            if (currentGroup != null) {
                onEdit.accept(currentGroup);
            }
        });
    }
}

