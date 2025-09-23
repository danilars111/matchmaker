package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.Node;

/**
 * A consistent, styled dialog for displaying informational messages.
 */
public class InfoDialog extends BaseDialog {

    public InfoDialog(String contentText, Node owner) {
        super(AlertType.INFORMATION, contentText, getOwnerWindow(owner));
    }
}

