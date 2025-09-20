package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;

/**
 * A custom dialog to handle unsaved changes, offering specific actions.
 */
public class UnsavedChangesDialog extends BaseDialog {

    public static final ButtonType UPDATE_AND_CONTINUE = new ButtonType("Update");
    public static final ButtonType DISCARD_AND_CONTINUE = new ButtonType("Discard");

    public UnsavedChangesDialog(String contentText, TabPane owner) {
        super(Alert.AlertType.WARNING, contentText, getOwnerWindow(owner), UPDATE_AND_CONTINUE, DISCARD_AND_CONTINUE, ButtonType.CANCEL);
    }
}

