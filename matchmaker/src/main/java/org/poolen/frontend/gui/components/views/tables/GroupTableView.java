package org.poolen.frontend.gui.components.views.tables;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
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
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.util.Callback;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.interfaces.PlayerMoveHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A beautiful, self-contained and collapsible "card" for displaying all the details of a single group.
 */
public class GroupTableView extends TitledPane {

    private static final DataFormat PLAYER_TRANSFER_FORMAT = new DataFormat("application/x-player-transfer");
    private static final String UNASSIGNED_PLACEHOLDER = "Unassigned";

    private final TableView<Player> partyTable;
    private final Button editButton;
    private final Button deleteButton;
    private Group currentGroup;
    private PlayerMoveHandler onPlayerMoveHandler;
    private BiFunction<Group, Player, Boolean> onDmUpdateRequestHandler;

    private final Label dmNameLabel;
    private final Label themesLabel;
    private final Label partySizeLabel;
    private final ComboBox<Object> dmComboBox;
    private boolean isUpdatingComboBox = false;

    public GroupTableView() {
        super();

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
        dmComboBox = new ComboBox<>();
        dmComboBox.setMaxWidth(Double.MAX_VALUE);
        setupDmComboBoxCellFactory();

        dmComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isUpdatingComboBox || newVal == null || oldVal == newVal) return;
            if (newVal instanceof Player && oldVal instanceof Player && ((Player) newVal).getUuid().equals(((Player) oldVal).getUuid())) return;

            Player selectedPlayer = (newVal instanceof Player) ? (Player) newVal : null;
            if (onDmUpdateRequestHandler != null) {
                boolean success = onDmUpdateRequestHandler.apply(this.currentGroup, selectedPlayer);
                if (!success) {
                    isUpdatingComboBox = true;
                    Platform.runLater(() -> {
                        dmComboBox.setValue(oldVal);
                        isUpdatingComboBox = false;
                    });
                }
            }
        });

        editButton = new Button("Edit");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox infoHeader = new HBox(5, dmComboBox, headerSpacer, editButton);
        infoHeader.setAlignment(Pos.CENTER_LEFT);
        infoHeader.setPadding(new Insets(5, 10, 0, 10));

        // --- Party Roster Table ---
        partyTable = new TableView<>();
        setupDragAndDrop();

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
        partyTable.setItems(FXCollections.observableArrayList(group.getParty().values()));

        if (group.getDungeonMaster() == null) {
            dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        } else {
            dmComboBox.setValue(group.getDungeonMaster());
        }
    }

    public void setDmList(Map<UUID, Player> dmingPlayers, Set<Player> allAssignedDms) {
        Player currentDmForThisGroup = currentGroup != null ? currentGroup.getDungeonMaster() : null;

        Object selectedDm = dmComboBox.getValue();
        ObservableList<Object> items = FXCollections.observableArrayList();
        items.add(UNASSIGNED_PLACEHOLDER);

        List<Player> sortedDms = dmingPlayers.values().stream()
                .sorted(Comparator.comparing(Player::getName))
                .toList();

        List<Player> availableDms = sortedDms.stream()
                .filter(dm -> !allAssignedDms.contains(dm) || dm.equals(currentDmForThisGroup))
                .toList();

        List<Player> busyDms = sortedDms.stream()
                .filter(dm -> allAssignedDms.contains(dm) && !dm.equals(currentDmForThisGroup))
                .toList();

        items.addAll(availableDms);
        if (!busyDms.isEmpty()) {
            items.add(new Separator());
            items.addAll(busyDms);
        }

        dmComboBox.setItems(items);
        if (selectedDm != null && items.contains(selectedDm)) {
            dmComboBox.setValue(selectedDm);
        } else if (currentDmForThisGroup != null) {
            dmComboBox.setValue(currentDmForThisGroup);
        } else {
            dmComboBox.setValue(UNASSIGNED_PLACEHOLDER);
        }
    }

    private void setupDmComboBoxCellFactory() {
        Callback<ListView<Object>, ListCell<Object>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof Separator) {
                    setText(null);
                    Region separatorLine = new Region();
                    separatorLine.setStyle("-fx-border-style: solid; -fx-border-width: 1 0 0 0; -fx-border-color: #c0c0c0;");
                    separatorLine.setMaxHeight(1);
                    setGraphic(separatorLine);
                    setPadding(new Insets(5, 0, 5, 0));
                    setDisable(true);
                } else {
                    setDisable(false);
                    setGraphic(null);
                    if (item.equals(UNASSIGNED_PLACEHOLDER)) {
                        setText(UNASSIGNED_PLACEHOLDER);
                        setFont(Font.font("System", FontPosture.ITALIC, 12));
                    } else { // It must be a Player
                        setText(((Player) item).getName());
                        setFont(Font.getDefault());
                        setStyle(""); // Reset styles
                    }
                }
            }
        };
        dmComboBox.setCellFactory(cellFactory);
        dmComboBox.setButtonCell(cellFactory.call(null));
    }

    private void setupDragAndDrop() {
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

        partyTable.setRowFactory(tv -> {
            TableRow<Player> row = new TableRow<>();
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent content = new ClipboardContent();
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

    public void setOnDmUpdateRequest(BiFunction<Group, Player, Boolean> handler) {
        this.onDmUpdateRequestHandler = handler;
    }
}

