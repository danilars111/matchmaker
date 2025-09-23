package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.Node;

/**
 * A consistent, styled dialog for displaying error messages.
 */
public class ErrorDialog extends BaseDialog {

    /**
     * Creates an ErrorDialog.
     * @param contentText The error message to display.
     * @param owner The UI node that this dialog should be associated with.
     */
    public ErrorDialog(String contentText, Node owner) {
        super(AlertType.ERROR, contentText, getOwnerWindow(owner));
    }
}
