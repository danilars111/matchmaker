package org.poolen.frontend.gui.components.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.views.tables.GroupTableView;
import org.poolen.frontend.gui.interfaces.PlayerMoveHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A reusable view component that displays multiple groups or a suggestion prompt.
 */
public class GroupDisplayView extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(GroupDisplayView.class);

    private final GridPane groupGrid;
    private final StackPane contentPane;
    private final Button suggestButton;
    private final Button createSuggestedButton;
    private final Button autoPopulateButton;
    private final Button expandAllButton;
    private final Button collapseAllButton;
    private final Button exportButton;
    private final DatePicker datePicker;
    private final VBox suggestionDisplayBox;
    private final VBox suggestionContainer;
    private final HBox header;
    private final HBox footer;
    private final ScrollPane gridScrollPane;
    private Consumer<Group> onGroupEditHandler;
    private Consumer<Group> onGroupDeleteHandler;
    private PlayerMoveHandler onPlayerMoveHandler;
    private Runnable onSuggestionRequestHandler;
    private Consumer<List<House>> onSuggestedGroupsCreateHandler;
    private Runnable onAutoPopulateHandler;
    private Runnable onExportRequestHandler;
    private BiFunction<Group, Player, Boolean> onDmUpdateRequestHandler;
    private BiFunction<Group, String, Boolean> onLocationUpdateRequestHandler; // Add this line
    private Consumer<LocalDate> onDateSelectedHandler;
    private List<Group> currentGroups = new ArrayList<>();
    private List<House> currentSuggestions = new ArrayList<>();
    private Map<UUID, Player> dmingPlayers;
    private Set<Player> allAssignedDms;
    private final List<GroupTableView> groupCards = new ArrayList<>();
    private static final double ESTIMATED_CARD_WIDTH = 200.0;
    private int lastColumnCount = -1;

    public GroupDisplayView() {
        super();
        this.groupGrid = new GridPane();
        groupGrid.setHgap(10);
        groupGrid.setVgap(10);
        groupGrid.setPadding(new Insets(10));

        // --- Suggestion UI ---
        suggestButton = new Button("Suggest Groups");
        suggestButton.setStyle("-fx-font-size: 14px; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        suggestButton.setOnAction(e -> {
            logger.info("Suggest Groups button clicked.");
            if (onSuggestionRequestHandler != null) onSuggestionRequestHandler.run();
        });

        createSuggestedButton = new Button("Create Suggested Groups");
        createSuggestedButton.setStyle("-fx-font-size: 14px; -fx-background-color: #008CBA; -fx-text-fill: white;");
        createSuggestedButton.setVisible(false);
        createSuggestedButton.setOnAction(e -> {
            if (onSuggestedGroupsCreateHandler != null && !currentSuggestions.isEmpty()) {
                logger.info("Create Suggested Groups button clicked with {} suggestions.", currentSuggestions.size());
                onSuggestedGroupsCreateHandler.accept(currentSuggestions);
            } else {
                logger.warn("Create Suggested Groups button clicked, but handler was null or no suggestions were present.");
            }
        });

        suggestionDisplayBox = new VBox(5);
        suggestionDisplayBox.setAlignment(Pos.CENTER);
        suggestionDisplayBox.setPadding(new Insets(10));
        suggestionContainer = new VBox(20, suggestButton, suggestionDisplayBox, createSuggestedButton);
        suggestionContainer.setAlignment(Pos.CENTER);
        suggestionContainer.setPadding(new Insets(20));

        // --- Header ---
        datePicker = new DatePicker();
        datePicker.getEditor().setDisable(true);
        datePicker.getEditor().setOpacity(1.0);
        datePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (onDateSelectedHandler != null && newDate != null) {
                logger.info("Date selected in DatePicker: {}", newDate);
                onDateSelectedHandler.accept(newDate);
            }
        });
        expandAllButton = new Button("Expand All");
        expandAllButton.setOnAction(e -> {
            logger.info("Expand All button clicked.");
            setAllCardsExpanded(true);
        });
        collapseAllButton = new Button("Collapse All");
        collapseAllButton.setOnAction(e -> {
            logger.info("Collapse All button clicked.");
            setAllCardsExpanded(false);
        });

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        header = new HBox(10, datePicker, headerSpacer, expandAllButton, collapseAllButton);
        header.setPadding(new Insets(10));
        header.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");


        // --- Footer ---
        autoPopulateButton = new Button("Auto-Populate Groups");
        autoPopulateButton.setStyle("-fx-font-size: 14px; -fx-background-color: #f44336; -fx-text-fill: white;");
        autoPopulateButton.setOnAction(e -> {
            logger.info("Auto-Populate Groups button clicked.");
            if (onAutoPopulateHandler != null) onAutoPopulateHandler.run();
        });
        exportButton = new Button("Export");
        exportButton.setStyle("-fx-font-size: 14px; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        exportButton.setOnAction(e -> {
            logger.info("Export button clicked.");
            if (onExportRequestHandler != null) onExportRequestHandler.run();
        });
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        footer = new HBox(10, autoPopulateButton, footerSpacer, exportButton);
        footer.setPadding(new Insets(10));
        footer.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");
        footer.setAlignment(Pos.CENTER_LEFT);

        // --- Center Content ---
        gridScrollPane = new ScrollPane(groupGrid);
        gridScrollPane.setFitToWidth(true);
        gridScrollPane.setStyle("-fx-background-color: transparent;");

        contentPane = new StackPane(gridScrollPane, suggestionContainer);

        // --- Assemble BorderPane ---
        this.setTop(header);
        this.setCenter(contentPane);
        this.setBottom(footer);

        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) updateGridLayout(newVal.doubleValue());
        });
        logger.info("GroupDisplayView initialised.");
    }

    public void updateGroups(List<Group> groups, Map<UUID, Player> dmingPlayers, Set<Player> allAssignedDms, LocalDate eventDate) {
        logger.info("Updating group display. Number of groups: {}. Event date: {}", groups.size(), eventDate);
        this.currentGroups = groups;
        this.dmingPlayers = dmingPlayers;
        this.allAssignedDms = allAssignedDms;
        this.datePicker.setValue(eventDate);

        if (groups.isEmpty()) {
            logger.debug("No groups to display. Showing suggestion container.");
            header.setVisible(false);
            footer.setVisible(false);
            gridScrollPane.setVisible(false);

            suggestionContainer.setVisible(true);
            suggestionDisplayBox.getChildren().clear();
            createSuggestedButton.setVisible(false);
        } else {
            logger.debug("Displaying {} groups. Showing group grid.", groups.size());
            header.setVisible(true);
            footer.setVisible(true);
            gridScrollPane.setVisible(true);

            suggestionContainer.setVisible(false);
            this.lastColumnCount = -1;
            updateGridLayout(this.getWidth());
        }
    }

    public void displaySuggestions(List<House> suggestedThemes) {
        logger.info("Displaying {} group theme suggestions.", suggestedThemes.size());
        this.currentSuggestions = suggestedThemes;
        suggestionDisplayBox.getChildren().clear();
        if (suggestedThemes.isEmpty()) {
            logger.debug("No suggestions to display.");
            suggestionDisplayBox.getChildren().add(new Label("Not enough players or DMs to make suggestions."));
            createSuggestedButton.setVisible(false);
        } else {
            logger.debug("Populating suggestion display box with themes.");
            Label title = new Label("Suggested Group Themes:");
            title.setStyle("-fx-font-weight: bold; -fx-underline: true;");
            suggestionDisplayBox.getChildren().add(title);
            for (House theme : suggestedThemes) {
                Label themeLabel = new Label("• " + theme.toString());
                themeLabel.setStyle("-fx-font-size: 14px;");
                suggestionDisplayBox.getChildren().add(themeLabel);
            }
            createSuggestedButton.setVisible(true);
        }
    }

    private void updateGridLayout(double currentWidth) {
        if (currentGroups == null || currentWidth <= 0) return;

        int newMaxCols = (int) (currentWidth / ESTIMATED_CARD_WIDTH);
        if (newMaxCols < 1) newMaxCols = 1;
        if (currentGroups.size() > 0) {
            newMaxCols = Math.min(newMaxCols, currentGroups.size());
        }
        if (newMaxCols == lastColumnCount) return;
        logger.debug("Updating grid layout. Current width: {}. New column count: {}.", currentWidth, newMaxCols);
        this.lastColumnCount = newMaxCols;

        groupCards.clear();
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
            groupCard.setGroup(group); // This will pass the group with the new location
            if (onGroupEditHandler != null) groupCard.setOnEditAction(onGroupEditHandler);
            if (onGroupDeleteHandler != null) groupCard.setOnDeleteAction(onGroupDeleteHandler);
            if (onPlayerMoveHandler != null) groupCard.setOnPlayerMove(onPlayerMoveHandler);
            if (onDmUpdateRequestHandler != null) groupCard.setOnDmUpdateRequest(onDmUpdateRequestHandler);
            if (onLocationUpdateRequestHandler != null) groupCard.setOnLocationUpdate(onLocationUpdateRequestHandler); // Add this line
            if (dmingPlayers != null && allAssignedDms != null) groupCard.setDmList(dmingPlayers, allAssignedDms);

            groupCard.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> updateExpandCollapseButtons());
            groupCards.add(groupCard);
            GridPane.setValignment(groupCard, VPos.TOP);
            groupGrid.add(groupCard, col, row);

            col++;
            if (col >= newMaxCols) {
                col = 0;
                row++;
            }
        }
        updateExpandCollapseButtons();
    }

    private void setAllCardsExpanded(boolean expanded) {
        logger.debug("Setting all group cards to expanded: {}.", expanded);
        groupCards.forEach(card -> card.setExpanded(expanded));
    }

    private void updateExpandCollapseButtons() {
        if (groupCards.isEmpty()) {
            expandAllButton.setDisable(true);
            collapseAllButton.setDisable(true);
            return;
        }
        boolean anyCollapsed = groupCards.stream().anyMatch(card -> !card.isExpanded());
        expandAllButton.setDisable(!anyCollapsed);

        boolean anyExpanded = groupCards.stream().anyMatch(TitledPane::isExpanded);
        collapseAllButton.setDisable(!anyExpanded);
        logger.trace("Expand/Collapse buttons updated. Any collapsed: {}. Any expanded: {}.", anyCollapsed, anyExpanded);
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

    public void setOnSuggestionRequest(Runnable handler) {
        this.onSuggestionRequestHandler = handler;
    }

    public void setOnSuggestedGroupsCreate(Consumer<List<House>> handler) {
        this.onSuggestedGroupsCreateHandler = handler;
    }

    public void setOnAutoPopulate(Runnable handler) {
        this.onAutoPopulateHandler = handler;
    }

    public void setOnDmUpdateRequest(BiFunction<Group, Player, Boolean> handler) {
        this.onDmUpdateRequestHandler = handler;
    }

    /**
     * Sets the handler for when a group's location is updated from its card.
     * @param handler The handler function.
     */
    public void setOnLocationUpdate(BiFunction<Group, String, Boolean> handler) {
        this.onLocationUpdateRequestHandler = handler;
    }

    public void setOnDateSelected(Consumer<LocalDate> handler) {
        this.onDateSelectedHandler = handler;
    }

    public void setOnExportRequest(Runnable handler) {
        this.onExportRequestHandler = handler;
    }
}
