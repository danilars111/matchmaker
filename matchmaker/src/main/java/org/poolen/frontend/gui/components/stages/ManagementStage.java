package org.poolen.frontend.gui.components.stages;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.poolen.backend.db.entities.Player;
import org.poolen.frontend.gui.components.tabs.PlayerManagementTab;
import org.poolen.frontend.gui.components.tabs.SettingsTab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A dedicated pop-up window for all management tasks, organized into detachable tabs.
 */
public class ManagementStage extends Stage {

    // A list to keep track of all our beautiful detached windows!
    private static final List<Stage> detachedStages = new ArrayList<>();

    public ManagementStage(Map<UUID, Player> attendingPlayers, Runnable onUpdate) {
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Management");

        TabPane tabPane = new TabPane();

        // Create our beautiful tabs
        Tab playerTab = new PlayerManagementTab(attendingPlayers, onUpdate);
        Tab characterTab = new Tab("Character Management");
        characterTab.setContent(new Label("Group management will go here!"));
        Tab groupTab = new Tab("Group Management");
        groupTab.setContent(new Label("Group management will go here!"));
        Tab settingsTab = new SettingsTab();
        Tab persistenceTab = new Tab("Persistence");
        persistenceTab.setContent(new Label("Persistence will go here!"));

        // Make them all detachable using the simpler button method
        makeTabDetachable(playerTab);
        makeTabDetachable(characterTab);
        makeTabDetachable(groupTab);
        makeTabDetachable(settingsTab);
        makeTabDetachable(persistenceTab);

        tabPane.getTabs().addAll(playerTab, characterTab, groupTab, settingsTab, persistenceTab);

        // We add a listener for when the main window is asked to close.
        this.setOnCloseRequest(event -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "Are you sure you want to close the management window? This will close all detached tabs as well.",
                    ButtonType.YES, ButtonType.CANCEL);

            // --- The New Positioning Logic! ---
            // This tells the alert box which window it belongs to.
            confirmation.initOwner(this);
            // ---------------------------------

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    // Close all the detached child windows first.
                    new ArrayList<>(detachedStages).forEach(Stage::close);
                } else {
                    // If the user clicks cancel, we consume the event to stop the window from closing.
                    event.consume();
                }
            });
        });

        Scene scene = new Scene(tabPane, 800, 500);
        setScene(scene);
    }

    /**
     * A beautiful, robust helper to make any tab detachable with a button.
     * @param tab The tab to make detachable.
     */
    private void makeTabDetachable(Tab tab) {
        tab.setClosable(false);

        Label title = new Label(tab.getText());
        Button detachButton = new Button("↗");
        detachButton.setStyle("-fx-font-size: 8px; -fx-padding: 2 4 2 4;");
        HBox header = new HBox(5, title, detachButton);
        header.setAlignment(Pos.CENTER_LEFT);

        tab.tabPaneProperty().addListener((obs, oldTabPane, newTabPane) -> {
            if (newTabPane != null) {
                detachButton.visibleProperty().bind(Bindings.size(newTabPane.getTabs()).greaterThan(1));
            } else {
                detachButton.visibleProperty().unbind();
            }
        });

        tab.setGraphic(header);
        tab.setText(null);

        detachButton.setOnAction(event -> {
            TabPane parent = tab.getTabPane();
            if (parent != null && parent.getTabs().size() > 1) {
                int originalIndex = parent.getTabs().indexOf(tab);
                parent.getTabs().remove(tab);

                Stage detachedStage = new Stage();
                detachedStage.setTitle(title.getText());

                StackPane contentPane = new StackPane();
                if (tab.getContent() != null) {
                    contentPane.getChildren().add(tab.getContent());
                }
                detachedStage.setScene(new Scene(contentPane, 800, 500));

                double parentX = ManagementStage.this.getX();
                double parentY = ManagementStage.this.getY();
                detachedStage.setX(parentX + 30);
                detachedStage.setY(parentY + 60);

                detachedStages.add(detachedStage);

                detachedStage.setOnCloseRequest(closeEvent -> {
                    detachedStages.remove(detachedStage);
                    if (!parent.getTabs().contains(tab)) {
                        int insertionIndex = Math.min(originalIndex, parent.getTabs().size());
                        parent.getTabs().add(insertionIndex, tab);
                    }
                });

                detachedStage.show();
            }
        });
    }
}
