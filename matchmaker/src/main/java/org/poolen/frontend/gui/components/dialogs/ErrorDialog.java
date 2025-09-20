package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.control.TabPane;

/**
 * A consistent, styled dialog for displaying error messages.
 */
public class ErrorDialog extends BaseDialog {

    public ErrorDialog(String contentText, TabPane owner) {
        super(AlertType.ERROR, contentText, getOwnerWindow(owner));
    }
}

