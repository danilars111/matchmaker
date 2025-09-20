package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.Node;

/**
 * A consistent error dialog.
 */
public class ErrorDialog extends BaseDialog {

    private ErrorDialog(String contentText, Node ownerNode) {
        super(AlertType.ERROR, contentText, getOwnerWindow(ownerNode));
    }

    /**
     * Shows an error dialog and waits for it to be closed.
     * @param message The message to display.
     * @param ownerNode A node within the scene that should own this dialog.
     */
    public static void show(String message, Node ownerNode) {
        ErrorDialog dialog = new ErrorDialog(message, ownerNode);
        dialog.showAndWait();
    }
}
