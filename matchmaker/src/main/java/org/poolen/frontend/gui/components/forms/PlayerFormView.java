package org.poolen.frontend.gui.components.forms;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

/**
 * A reusable JavaFX component for creating a new player.
 * It contains the form fields and buttons, but no logic.
 */
public class PlayerFormView extends GridPane {

    private final TextField nameField;
    private final CheckBox dmCheckBox;
    private final Button createButton;
    private final Button cancelButton;

    public PlayerFormView() {
        super();
        setHgap(10);
        setVgap(10);
        setPadding(new Insets(20));

        nameField = new TextField();
        dmCheckBox = new CheckBox("Dungeon Master");
        createButton = new Button("Create Player");
        cancelButton = new Button("Cancel");

        createButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");

        add(new Label("Name:"), 0, 0);
        add(nameField, 1, 0);
        add(dmCheckBox, 1, 1);

        HBox buttonBox = new HBox(10, cancelButton, createButton);
        add(buttonBox, 1, 2);
    }

    // Public getters so the parent window can access the form data and buttons.
    public String getPlayerName() {
        return nameField.getText();
    }

    public boolean isDungeonMaster() {
        return dmCheckBox.isSelected();
    }



    public Button getCreateButton() {
        return createButton;
    }

    public Button getCancelButton() {
        return cancelButton;
    }
}
