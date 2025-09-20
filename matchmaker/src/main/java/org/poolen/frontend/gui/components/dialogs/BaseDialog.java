package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.net.URL;

/**
 * An abstract base class for creating consistent, styled dialogs.
 * It ensures that all dialogs are modal to their parent window.
 */
public abstract class BaseDialog extends Alert {

    public BaseDialog(AlertType alertType, String contentText, Window owner, ButtonType... buttons) {
        super(alertType, contentText, buttons);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        centerOnParent();

        // --- Style Enhancements ---
        getDialogPane().setPrefWidth(400);

        Node content = getDialogPane().getContent();
        if (content instanceof Label) {
            ((Label) content).setWrapText(true);
        }

        getDialogPane().setMinHeight(Region.USE_PREF_SIZE);

        // This is our new, more robust magic! It tells the dialog to use its class loader
        // to find the beautiful new stylesheet from the root of the resources.
        URL cssResource = BaseDialog.class.getClassLoader().getResource("css/dialog-styles.css");
        if (cssResource != null) {
            getDialogPane().getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.err.println("Oh dear, I couldn't find the dialog stylesheet, darling!");
        }
    }

    /**
     * A helper method to safely get the parent Window from any Node.
     * @param node The node from which to derive the window.
     * @return The parent Window.
     */
    protected static Window getOwnerWindow(Node node) {
        if (node != null && node.getScene() != null) {
            return node.getScene().getWindow();
        }
        return null;
    }

    /**
     * Attaches a listener to center this dialog on its owner window once shown.
     */
    private void centerOnParent() {
        if (getOwner() == null) return;

        // We can only get the dialog's dimensions after it has been shown,
        // so we listen for the onShown event.
        setOnShown(event -> {
            double ownerX = getOwner().getX();
            double ownerY = getOwner().getY();
            double ownerWidth = getOwner().getWidth();
            double ownerHeight = getOwner().getHeight();

            double dialogWidth = getWidth();
            double dialogHeight = getHeight();

            setX(ownerX + (ownerWidth / 2) - (dialogWidth / 2));
            setY(ownerY + (ownerHeight / 2) - (dialogHeight / 2));
        });
    }
}

