package org.poolen.frontend.gui.components.views.tables;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.interfaces.PlayerMoveHandler;

import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A beautiful, self-contained and collapsible "card" for displaying all the details of a single group.
 */
public class GroupTableView extends TitledPane {

    // --- A special format to carry our precious player data during the drag! ---
    private static final DataFormat PLAYER_TRANSFER_FORMAT = new DataFormat("application/x-player-transfer");

    private final TableView<Player> partyTable;
    private final Label dateLabel;
    private final Button editButton;
    private final Button deleteButton;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private Group currentGroup;
    private PlayerMoveHandler onPlayerMoveHandler;

    private final Label dmNameLabel;
    private final Label themesLabel;
    private final Label partySizeLabel;

    public GroupTableView() {
        super();

        // --- Our beautiful new title bar ---
        dmNameLabel = new Label();
        dmNameLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        themesLabel = new Label();
        themesLabel.setStyle("-fx-text-fill: #555;");
        partySizeLabel = new Label();
        partySizeLabel.setStyle("-fx-font-style: italic;");
        deleteButton = new Button("✖");
        deleteButton.setStyle("-fx-font-size: 12px; -fx-padding: 2 6 2 6; -fx-background-color: transparent; -fx-text-fill: #808080; -fx-font-weight: bold;");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox mainTitleInfo = new HBox(10, dmNameLabel, themesLabel, partySizeLabel);
        HBox titleBox = new HBox(10, mainTitleInfo, titleSpacer, deleteButton);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.prefWidthProperty().bind(this.widthProperty().subtract(40));

        this.setGraphic(titleBox);
        this.setText(null);

        // --- Information Header ---
        dateLabel = new Label();
        editButton = new Button("Edit");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox infoHeader = new HBox(5, dateLabel, headerSpacer, editButton);
        infoHeader.setAlignment(Pos.CENTER_LEFT);
        infoHeader.setPadding(new Insets(5, 10, 0, 10));

        // --- Party Roster Table ---
        partyTable = new TableView<>();
        setupDragAndDrop(); // We teach our table its beautiful new dance moves!

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

    public void setGroup(Group group) {
        this.currentGroup = group;
        String dmName = group.getDungeonMaster() != null ? group.getDungeonMaster().getName() : "N/A";
        dmNameLabel.setText(dmName);
        String themes = group.getHouses().stream()
                .map(house -> house.name().substring(0, 2))
                .collect(Collectors.joining(", "));
        if (themes.isEmpty()) themes = "No themes";
        themesLabel.setText("• " + themes);
        partySizeLabel.setText(group.getParty().size() + " players");
        String date = group.getDate() != null ? group.getDate().format(dateFormatter) : "N/A";
        dateLabel.setText(date);
        partyTable.setItems(FXCollections.observableArrayList(group.getParty().values()));
    }

    private void setupDragAndDrop() {
        // --- This card can now receive players! ---
        this.setOnDragOver(event -> {
            if (event.getGestureSource() != this && event.getDragboard().hasContent(PLAYER_TRANSFER_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        this.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasContent(PLAYER_TRANSFER_FORMAT)) {
                String[] data = ((String) db.getContent(PLAYER_TRANSFER_FORMAT)).split(":");
                UUID sourceGroupUuid = UUID.fromString(data[0]);
                UUID playerUuid = UUID.fromString(data[1]);

                if (onPlayerMoveHandler != null) {
                    onPlayerMoveHandler.onMove(sourceGroupUuid, playerUuid, this.currentGroup);
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        // --- Each row can now be dragged! ---
        partyTable.setRowFactory(tv -> {
            TableRow<Player> row = new TableRow<>();
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent content = new ClipboardContent();
                    // We put the source group and player UUIDs on the clipboard
                    content.put(PLAYER_TRANSFER_FORMAT, currentGroup.getUuid() + ":" + row.getItem().getUuid());
                    db.setContent(content);
                    event.consume();
                }
            });
            return row;
        });
    }

    public void setOnEditAction(Consumer<Group> onEdit) {
        editButton.setOnAction(e -> {
            if (currentGroup != null) onEdit.accept(currentGroup);
        });
    }

    public void setOnDeleteAction(Consumer<Group> onDelete) {
        deleteButton.setOnAction(e -> {
            if (currentGroup != null) onDelete.accept(currentGroup);
        });
    }

    public void setOnPlayerMove(PlayerMoveHandler handler) {
        this.onPlayerMoveHandler = handler;
    }
}
