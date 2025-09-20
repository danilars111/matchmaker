package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.Node;

/**
 * A consistent information dialog.
 */
public class InfoDialog extends BaseDialog {

    private InfoDialog(String contentText, Node ownerNode) {
        super(AlertType.INFORMATION, contentText, getOwnerWindow(ownerNode));
    }

    /**
     * Shows an information dialog and waits for it to be closed.
     * @param message The message to display.
     * @param ownerNode A node within the scene that should own this dialog.
     */
    public static void show(String message, Node ownerNode) {
        InfoDialog dialog = new InfoDialog(message, ownerNode);
        dialog.showAndWait();
    }
}
