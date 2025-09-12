package org.poolen.frontend.gui.components.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.poolen.backend.db.constants.House;
import org.poolen.backend.db.entities.Group;
import org.poolen.frontend.gui.components.views.tables.GroupTableView;
import org.poolen.frontend.gui.interfaces.PlayerMoveHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A reusable view component that displays multiple groups or a suggestion prompt.
 */
public class GroupDisplayView extends ScrollPane {

    private final GridPane groupGrid;
    private final StackPane contentPane;
    private final Button suggestButton;
    private final Button createSuggestedButton; // Our beautiful new button!
    private final VBox suggestionDisplayBox;
    private final VBox suggestionContainer;
    private Consumer<Group> onGroupEditHandler;
    private Consumer<Group> onGroupDeleteHandler;
    private PlayerMoveHandler onPlayerMoveHandler;
    private Runnable onSuggestionRequestHandler;
    private Consumer<List<House>> onSuggestedGroupsCreateHandler; // Our new handler!
    private List<Group> currentGroups = new ArrayList<>();
    private List<House> currentSuggestions = new ArrayList<>();
    private static final double ESTIMATED_CARD_WIDTH = 350.0;
    private int lastColumnCount = -1;

    public GroupDisplayView() {
        super();
        this.groupGrid = new GridPane();
        groupGrid.setHgap(10);
        groupGrid.setVgap(10);
        groupGrid.setPadding(new Insets(10));

        // --- New Suggestion UI ---
        suggestButton = new Button("Suggest Groups");
        suggestButton.setStyle("-fx-font-size: 14px; -fx-background-color: #4CAF50; -fx-text-fill: white;");
        suggestButton.setOnAction(e -> {
            if (onSuggestionRequestHandler != null) {
                onSuggestionRequestHandler.run();
            }
        });

        createSuggestedButton = new Button("Create Suggested Groups");
        createSuggestedButton.setStyle("-fx-font-size: 14px; -fx-background-color: #008CBA; -fx-text-fill: white;");
        createSuggestedButton.setVisible(false); // We hide it until we have suggestions!
        createSuggestedButton.setOnAction(e -> {
            if (onSuggestedGroupsCreateHandler != null && !currentSuggestions.isEmpty()) {
                onSuggestedGroupsCreateHandler.accept(currentSuggestions);
            }
        });

        suggestionDisplayBox = new VBox(5);
        suggestionDisplayBox.setAlignment(Pos.CENTER);
        suggestionDisplayBox.setPadding(new Insets(10));

        suggestionContainer = new VBox(20, suggestButton, suggestionDisplayBox, createSuggestedButton);
        suggestionContainer.setAlignment(Pos.CENTER);
        suggestionContainer.setPadding(new Insets(20));

        // --- Content Pane to switch between views ---
        contentPane = new StackPane(groupGrid, suggestionContainer);

        this.setContent(contentPane);
        this.setFitToWidth(true);

        this.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > 0) {
                updateGridLayout(newVal.doubleValue());
            }
        });
    }

    public void updateGroups(List<Group> groups) {
        this.currentGroups = groups;
        if (groups.isEmpty()) {
            groupGrid.setVisible(false);
            suggestionContainer.setVisible(true);
            suggestionDisplayBox.getChildren().clear();
            createSuggestedButton.setVisible(false); // We hide the button when there are no groups.
        } else {
            groupGrid.setVisible(true);
            suggestionContainer.setVisible(false);
            this.lastColumnCount = -1; // Force a redraw
            updateGridLayout(this.getWidth());
        }
    }

    public void displaySuggestions(List<House> suggestedThemes) {
        this.currentSuggestions = suggestedThemes;
        suggestionDisplayBox.getChildren().clear();
        if (suggestedThemes.isEmpty()) {
            suggestionDisplayBox.getChildren().add(new Label("Not enough players or DMs to make suggestions."));
            createSuggestedButton.setVisible(false);
        } else {
            Label title = new Label("Suggested Group Themes:");
            title.setStyle("-fx-font-weight: bold; -fx-underline: true;");
            suggestionDisplayBox.getChildren().add(title);
            for (House theme : suggestedThemes) {
                Label themeLabel = new Label("• " + theme.toString());
                themeLabel.setStyle("-fx-font-size: 14px;");
                suggestionDisplayBox.getChildren().add(themeLabel);
            }
            createSuggestedButton.setVisible(true); // We show our beautiful new button!
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
            if (onPlayerMoveHandler != null) groupCard.setOnPlayerMove(onPlayerMoveHandler);
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

    public void setOnSuggestionRequest(Runnable handler) {
        this.onSuggestionRequestHandler = handler;
    }

    public void setOnSuggestedGroupsCreate(Consumer<List<House>> handler) {
        this.onSuggestedGroupsCreateHandler = handler;
    }
}

