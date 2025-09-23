package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;

/**
 * A consistent, styled dialog for Yes/No confirmation prompts.
 */
public class ConfirmationDialog extends BaseDialog {

    public ConfirmationDialog(String contentText, Node owner) {
        super(AlertType.CONFIRMATION, contentText, getOwnerWindow(owner), ButtonType.YES, ButtonType.NO);
    }
}

