package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/**
 * An abstract base for creating consistent, styled dialogs.
 * It handles the logic for finding the correct owner window from any UI Node.
 */
public abstract class BaseDialog extends Alert {
    public enum DialogType {
        CONFIRMATION,
        ERROR,
        INFO,
        UNSAVED_CHANGES,
    }

    /**
     * Protected constructor for subclasses to use.
     *
     * @param alertType The type of alert (e.g., ERROR, CONFIRMATION).
     * @param contentText The message to display in the dialog.
     * @param owner The window that this dialog should be modal to.
     * @param buttons The set of buttons to display.
     */
    public BaseDialog(AlertType alertType, String contentText, Window owner, ButtonType... buttons) {
        super(alertType, contentText, buttons);
        initOwner(owner);
        // You could add consistent styling for all dialogs here, e.g., by loading a stylesheet.
    }

    /**
     * A helper utility to get the parent Window from any given JavaFX Node.
     * This allows us to correctly position dialogs relative to their parent UI component.
     *
     * @param ownerNode The node from which to find the parent window.
     * @return The parent Window, or null if it cannot be found.
     */
    protected static Window getOwnerWindow(Node ownerNode) {
        return (ownerNode != null && ownerNode.getScene() != null) ? ownerNode.getScene().getWindow() : null;
    }
}
