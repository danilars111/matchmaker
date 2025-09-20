package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * A consistent confirmation dialog (Yes/No).
 */
public class ConfirmationDialog extends BaseDialog {

    private ConfirmationDialog(String contentText, Node ownerNode) {
        super(AlertType.CONFIRMATION, contentText, getOwnerWindow(ownerNode), ButtonType.YES, ButtonType.NO);
    }

    /**
     * Shows a confirmation dialog and waits for the user's response.
     * @param message The message to display.
     * @param ownerNode A node within the scene that should own this dialog.
     * @return An Optional containing the ButtonType clicked by the user.
     */
    public static Optional<ButtonType> show(String message, Node ownerNode) {
        ConfirmationDialog dialog = new ConfirmationDialog(message, ownerNode);
        return dialog.showAndWait();
    }
}
