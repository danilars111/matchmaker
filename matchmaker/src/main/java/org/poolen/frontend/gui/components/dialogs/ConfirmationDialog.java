package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.control.ButtonType;
import javafx.scene.control.TabPane;

/**
 * A consistent, styled dialog for Yes/No confirmation prompts.
 */
public class ConfirmationDialog extends BaseDialog {

    public ConfirmationDialog(String contentText, TabPane owner) {
        super(AlertType.CONFIRMATION, contentText, getOwnerWindow(owner), ButtonType.YES, ButtonType.NO);
    }
}

