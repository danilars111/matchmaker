package org.poolen.frontend.gui.components.dialogs;

import javafx.scene.control.TabPane;

/**
 * A consistent, styled dialog for displaying informational messages.
 */
public class InfoDialog extends BaseDialog {

    public InfoDialog(String contentText, TabPane owner) {
        super(AlertType.INFORMATION, contentText, getOwnerWindow(owner));
    }
}

