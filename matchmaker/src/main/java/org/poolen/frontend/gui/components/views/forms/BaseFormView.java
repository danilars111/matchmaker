package org.poolen.frontend.gui.components.views.forms;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.util.UUID;

/**
 * An abstract base class for creating reusable forms with common functionality and layout.
 * @param <T> The type of the entity the form will be editing (e.g., Player, Character).
 */
public abstract class BaseFormView<T> extends GridPane {

    protected final TextField uuidField;
    protected final Button actionButton;
    protected final Button cancelButton;
    protected final HBox mainActionsBox;
    protected T itemBeingEdited;

    public BaseFormView() {
        super();
        setHgap(10);
        setVgap(10);
        setPadding(new Insets(20));

        // --- Common UUID Field ---
        uuidField = new TextField();
        uuidField.setEditable(false);
        uuidField.setStyle("-fx-control-inner-background: #f0f0f0; -fx-text-fill: #555;");

        Button copyButton = new Button("📋");
        copyButton.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(uuidField.getText());
            clipboard.setContent(content);
        });

        HBox uuidBox = new HBox(5, uuidField, copyButton);
        HBox.setHgrow(uuidField, Priority.ALWAYS);

        add(new Label("UUID"), 0, 0);
        add(uuidBox, 0, 1);


        // --- Common Buttons (Created but not placed) ---
        actionButton = new Button();
        cancelButton = new Button("Cancel");
        actionButton.setStyle("-fx-background-color: #3CB371; -fx-text-fill: white;");

        mainActionsBox = new HBox(10, cancelButton, actionButton);
        mainActionsBox.setAlignment(Pos.CENTER_RIGHT);
    }

    // --- Abstract Methods for Subclasses ---
    protected abstract void setupFormControls();
    protected abstract UUID getUuidFromItem(T item);

    public void populateForm(T item) {
        this.itemBeingEdited = item;
        uuidField.setText(getUuidFromItem(item).toString());
    }

    public void clearForm() {
        this.itemBeingEdited = null;
        uuidField.clear();
    }

    // --- Public Getters ---
    public Button getActionButton() { return actionButton; }
    public Button getCancelButton() { return cancelButton; }
    public T getItemBeingEdited() { return itemBeingEdited; }
}

